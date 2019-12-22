package org.ivanfranchin.kubelessplugin;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mojo(name = "convert", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public class KubelessConvertMojo extends AbstractMojo {

    @Parameter(property = "inputJavaClassName", required = true)
    protected String inputJavaClassName;

    @Parameter(property = "outputJavaClassName", required = true)
    protected String outputJavaClassName;

    @Parameter(property = "inputJavaClassDirectory", defaultValue = "${basedir}/src/main/java/io/kubeless", required = true, readonly = true)
    protected File inputJavaClassDirectory;

    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/generated-sources/kubeless", required = true)
    protected File outputDirectory;

    @Parameter(property = "inputPomDirectory", defaultValue = "${basedir}", required = true, readonly = true)
    protected File inputPomDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        checkInputsExist();
        createOutputDirectory();
        createOutputJavaClassFile();
        createOutputPomFile();
    }

    protected void checkInputsExist() throws MojoExecutionException {
        if (!getInputJavaClassFile().exists()) {
            throw new MojoExecutionException("The input Java Class informed does not exist");
        }
        if (!getInputPomFile().exists()) {
            throw new MojoExecutionException("The pom.xml file is not present in the directory informed");
        }
    }

    private void createOutputDirectory() throws MojoExecutionException {
        try {
            final Path path = Paths.get(outputDirectory.toURI());
            if (!path.toFile().exists()) {
                Files.createDirectories(path);
                getLog().info("Created successfully directory: " + path);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create output directory", e);
        }
    }

    private void createOutputJavaClassFile() throws MojoExecutionException {
        final CompilationUnit compilationUnit = validateInputJavaClass();
        writeOutputClassFile(manipulateInputJavaClassContent(compilationUnit));
    }

    private CompilationUnit validateInputJavaClass() throws MojoExecutionException {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(getInputJavaClassFile());

            Optional<PackageDeclaration> packageDeclarationOptional = compilationUnit.getPackageDeclaration();
            boolean hasValidPackage = packageDeclarationOptional.isPresent() &&
                    packageDeclarationOptional.get().getName().asString().equals(IO_KUBELESS);
            if (!hasValidPackage) {
                throw new MojoExecutionException("The input Java Class must belong to package io.kubeless");
            }

            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = compilationUnit.getClassByName(inputJavaClassName)
                    .orElseThrow(() -> new MojoExecutionException("The input Java Class informed is not a valid Java Class"));

            AccessSpecifier accessSpecifier = classOrInterfaceDeclaration.getAccessSpecifier();
            if (accessSpecifier.equals(AccessSpecifier.PRIVATE) || accessSpecifier.equals(AccessSpecifier.PROTECTED)) {
                throw new MojoExecutionException("The input Java Class informed is not a public class");
            }

            final Optional<MethodDeclaration> anyMethodOptional = classOrInterfaceDeclaration.getMethods().stream()
                    .filter(methodDeclaration -> methodDeclaration.hasModifier(Modifier.Keyword.PUBLIC))
                    .filter(methodDeclaration -> methodDeclaration.getType().asString().equals("String"))
                    .filter(methodDeclaration -> methodDeclaration.getParameters().size() == 2)
                    .filter(methodDeclaration -> {
                        String type = methodDeclaration.getParameter(0).getTypeAsString();
                        return type.equals("io.kubeless.Event") || type.equals("Event");
                    })
                    .filter(methodDeclaration -> {
                        String type = methodDeclaration.getParameter(1).getTypeAsString();
                        return type.equals("io.kubeless.Context") || type.equals("Context");
                    })
                    .findAny();

            if (!anyMethodOptional.isPresent()) {
                throw new MojoExecutionException("The input Java Class informed does not implement any Kubeless Function, i.e, one method that takes io.kubeless.Event and io.kubeless.Context as parameters and returns a String");
            }

            return compilationUnit;
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Unable to read input Java Class", e);
        } catch (ParseProblemException e) {
            throw new MojoExecutionException("Unable to parse input Java Class", e);
        }
    }

    private String manipulateInputJavaClassContent(final CompilationUnit compilationUnit) {
        compilationUnit.getClassByName(inputJavaClassName).ifPresent(cClass -> {
            cClass.setName(outputJavaClassName);
            cClass.getConstructors().forEach(constructor -> constructor.setName(outputJavaClassName));
        });
        return compilationUnit.toString();
    }

    private void writeOutputClassFile(String classContent) throws MojoExecutionException {
        try {
            Files.write(getOutputJavaSourceFile().toPath(), classContent.getBytes());
            getLog().info("Created successfully file: " + getOutputJavaSourceFile());
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write output Java Class", e);
        }
    }

    private void createOutputPomFile() throws MojoExecutionException {
        final Model model = readPomTemplateFile();
        model.getDependencies().addAll(getProjectDependencies());
        writeOutputPomFile(model);
    }

    private List<Dependency> getProjectDependencies() throws MojoExecutionException {
        try {
            final Model model = mavenXpp3Reader.read(readProjectPomXml());

            return model.getDependencies().stream()
                    .filter(d -> d.getScope() == null || (d.getScope() != null && !d.getScope().equals(TEST_SCOPE)))
                    .filter(d -> !EXCLUDE_DEPENDENCIES.contains(String.format("%s:%s", d.getGroupId(), d.getArtifactId())))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("An exception occurred while reading project pom.xml", e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Unable to parse project pom.xml", e);
        }
    }

    private InputStream readProjectPomXml() throws MojoExecutionException {
        try {
            return new FileInputStream(getInputPomFile());
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Unable to read project pom.xml", e);
        }
    }

    private Model readPomTemplateFile() throws MojoExecutionException {
        try {
            return mavenXpp3Reader.read(this.getClass().getResourceAsStream(POM_TEMPLATE_XML));
        } catch (IOException e) {
            throw new MojoExecutionException("An exception occurred while reading pom-template.xml", e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Unable to parse pom-template.xml", e);
        }
    }

    private void writeOutputPomFile(final Model model) throws MojoExecutionException {
        try {
            mavenXpp3Writer.write(new FileOutputStream(getOutputResourceFilePath()), model);
            getLog().info("Created successfully file: " + getOutputResourceFilePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write new pom.xml", e);
        }
    }

    private File getInputJavaClassFile() {
        return Paths.get(inputJavaClassDirectory + File.separator + inputJavaClassName + DOT_JAVA).toFile();
    }

    private File getOutputJavaSourceFile() {
        return Paths.get(outputDirectory + File.separator + outputJavaClassName + DOT_JAVA).toFile();
    }

    private File getInputPomFile() {
        return Paths.get(inputPomDirectory + File.separator + POM_XML).toFile();
    }

    private String getOutputResourceFilePath() {
        return outputDirectory + File.separator + POM_XML;
    }

    private static final MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
    private static final MavenXpp3Writer mavenXpp3Writer = new MavenXpp3Writer();

    private static final String TEST_SCOPE = "test";
    private static final String POM_XML = "pom.xml";
    private static final String DOT_JAVA = ".java";
    private static final String POM_TEMPLATE_XML = "/pom-template.xml";
    private static final String IO_KUBELESS = "io.kubeless";
    private static final List<String> EXCLUDE_DEPENDENCIES = Collections.singletonList("de.inoio.kubeless:jvm-runtime");

}
