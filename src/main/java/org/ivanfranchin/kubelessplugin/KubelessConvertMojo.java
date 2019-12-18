package org.ivanfranchin.kubelessplugin;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
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
import java.util.stream.Collectors;

@Mojo(name = "convert", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class KubelessConvertMojo extends AbstractMojo {

    @Parameter(property = "inputClassName", required = true)
    private String inputClassName;

    @Parameter(property = "outputClassName", required = true)
    private String outputClassName;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        validateInputClass();
        createOutputDirectory();
        createOutputClassFile();
        createOutputPomFile();
    }

    private void validateInputClass() throws MojoExecutionException {
        try {
            Files.readAllBytes(Paths.get(getInputClassFilePath()));
        } catch (IOException e) {
            throw new MojoExecutionException("The input class informed does not exist", e);
        }
    }

    private void createOutputDirectory() throws MojoExecutionException {
        try {
            final Path path = Paths.get(getOutputDirectoryPath());
            if (!path.toFile().exists()) {
                Files.createDirectories(path);
                log.info("Created successfully directory: " + path);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create output directory", e);
        }
    }

    private void createOutputClassFile() throws MojoExecutionException {
        String inputClassContent = getInputClassContent();
        String outputClassContent = manipulateClassContent(inputClassContent);
        writeOutputClassFile(outputClassContent);
    }

    private String getInputClassContent() throws MojoExecutionException {
        try {
            return new String(Files.readAllBytes(Paths.get(getInputClassFilePath())));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read input class", e);
        }
    }

    private void writeOutputClassFile(String classContent) throws MojoExecutionException {
        try {
            Files.write(Paths.get(getOutputSourceFilePath()), classContent.getBytes());
            log.info("Created successfully file: " + getOutputSourceFilePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write output class", e);
        }
    }

    private String manipulateClassContent(String classContent) {
        final CompilationUnit compilationUnit = StaticJavaParser.parse(classContent);
        compilationUnit.getClassByName(inputClassName).ifPresent(cClass -> {
            cClass.setName(outputClassName);
            cClass.getConstructors().forEach(constructor -> constructor.setName(outputClassName));
        });
        for (String newImport : NEW_KUBELESS_IMPORTS) {
            compilationUnit.getImports().add(new ImportDeclaration(newImport, false, false));
        }
        return compilationUnit.toString();
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
                    .filter(d -> !excludeDependencies.contains(String.format("%s:%s", d.getGroupId(), d.getArtifactId())))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new MojoExecutionException("An exception occurred while reading project pom.xml", e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Unable to parse project pom.xml", e);
        }
    }

    private InputStream readProjectPomXml() throws MojoExecutionException {
        try {
            return new FileInputStream(getResourceFilePath());
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Unable to read project pom.xml", e);
        }
    }

    private Model readPomTemplateFile() throws MojoExecutionException {
        try {
            return mavenXpp3Reader.read(getClass().getResourceAsStream(POM_TEMPLATE_XML));
        } catch (IOException e) {
            throw new MojoExecutionException("An exception occurred while reading pom-template.xml", e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Unable to parse pom-template.xml", e);
        }
    }

    private void writeOutputPomFile(Model model) throws MojoExecutionException {
        try {
            mavenXpp3Writer.write(new FileOutputStream(getOutputResourceFilePath()), model);
            log.info("Created successfully file: " + getOutputResourceFilePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write new pom.xml", e);
        }
    }

    private String getInputClassDirectory() {
        return project.getBasedir() + "/src/main/java/io/kubeless";
    }

    private String getOutputDirectoryPath() {
        return project.getBuild().getDirectory() + "/generated-sources/kubeless";
    }

    private String getInputClassFilePath() {
        return getInputClassDirectory() + File.separator + inputClassName + DOT_JAVA;
    }

    private String getOutputSourceFilePath() {
        return getOutputDirectoryPath() + File.separator + outputClassName + DOT_JAVA;
    }

    private String getResourceFilePath() {
        return project.getBasedir() + File.separator + POM_XML;
    }

    private String getOutputResourceFilePath() {
        return getOutputDirectoryPath() + File.separator + POM_XML;
    }

    private final Log log = getLog();
    private static final MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();
    private static final MavenXpp3Writer mavenXpp3Writer = new MavenXpp3Writer();

    private static final String TEST_SCOPE = "test";
    private static final String POM_XML = "pom.xml";
    private static final String DOT_JAVA = ".java";
    private static final String POM_TEMPLATE_XML = "/pom-template.xml";
    private static final String[] NEW_KUBELESS_IMPORTS = new String[]{"io.kubeless.Event", "io.kubeless.Context"};
    private static final List<String> excludeDependencies = Collections.singletonList("de.inoio.kubeless:jvm-runtime");

}
