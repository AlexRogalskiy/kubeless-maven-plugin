package org.ivanfranchin.kubelessplugin;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KubelessConvertMojoTest {

    @Nested
    @DisplayName("Test only the input Java Class")
    class InputJavaClassTest {

        private static final String TEST_CLASS_FOLDER = "input-java-class-test";

        @Test
        void whenInputJavaDoesNotExist() {
            String inputJavaClassName = "NonExistentClass";
            File outputDirectory = Paths.get("target/test-classes/unit/generated-sources/" + TEST_CLASS_FOLDER).toFile();
            File baseDir = Paths.get("src/test/resources/unit/sources/" + TEST_CLASS_FOLDER + "/pom.xml").toFile();
            KubelessConvertMojo kubelessConvertMojo = createKubelessConvertMojo(inputJavaClassName, outputDirectory, baseDir);

            Throwable exception = assertThrows(MojoExecutionException.class, kubelessConvertMojo::execute);
            String expectedMessage = "The input Java Class informed does not exist in src/test/resources/unit/sources/input-java-class-test/src/main/java/io/kubeless";
            assertEquals(expectedMessage, exception.getMessage());
        }

        @Test
        void whenInputJavaClassHasPrivateAccessModifier() {
            String inputJavaClassName = "PrivateAccessModifier";
            File outputDirectory = Paths.get("target/test-classes/unit/generated-sources/" + TEST_CLASS_FOLDER).toFile();
            File baseDir = Paths.get("src/test/resources/unit/sources/" + TEST_CLASS_FOLDER + "/pom.xml").toFile();
            KubelessConvertMojo kubelessConvertMojo = createKubelessConvertMojo(inputJavaClassName, outputDirectory, baseDir);

            Throwable exception = assertThrows(MojoExecutionException.class, kubelessConvertMojo::execute);
            assertEquals("Unable to parse input Java Class", exception.getMessage());
        }

        @Test
        void whenInputJavaClassHasProtectedAccessModifier() {
            String inputJavaClassName = "ProtectedAccessModifier";
            File outputDirectory = Paths.get("target/test-classes/unit/generated-sources/" + TEST_CLASS_FOLDER).toFile();
            File baseDir = Paths.get("src/test/resources/unit/sources/" + TEST_CLASS_FOLDER + "/pom.xml").toFile();
            KubelessConvertMojo kubelessConvertMojo = createKubelessConvertMojo(inputJavaClassName, outputDirectory, baseDir);

            Throwable exception = assertThrows(MojoExecutionException.class, kubelessConvertMojo::execute);
            assertEquals("Unable to parse input Java Class", exception.getMessage());
        }

    }

    @Nested
    @DisplayName("Test only on the existence of a valid Kubeless Function in input Java Class")
    class FunctionSignatureTest {

        private static final String TEST_CLASS_FOLDER = "function-signature-test";

        @Test
        void whenInputJavaClassIsPublicWithoutKubelessFunction() {
            IntStream.range(1, 8)
                    .mapToObj(n -> String.format("PublicWithoutKubelessFunction%s", n))
                    .forEach(inputJavaClassName -> {
                        File outputDirectory = Paths.get("target/test-classes/unit/generated-sources/" + TEST_CLASS_FOLDER).toFile();
                        File baseDir = Paths.get("src/test/resources/unit/sources/" + TEST_CLASS_FOLDER + "/pom.xml").toFile();
                        KubelessConvertMojo kubelessConvertMojo = createKubelessConvertMojo(inputJavaClassName, outputDirectory, baseDir);

                        Throwable exception = assertThrows(MojoExecutionException.class, kubelessConvertMojo::execute);
                        String expectedMessage = "The input Java Class informed does not implement any Kubeless Function, i.e, one method that takes io.kubeless.Event and io.kubeless.Context as parameters and returns a String";
                        assertEquals(expectedMessage, exception.getMessage());
                    });
        }

        @Test
        void whenInputJavaClassIsPublicWithKubelessFunction() {
            IntStream.range(1, 6)
                    .mapToObj(n -> String.format("PublicWithKubelessFunction%s", n))
                    .forEach(inputJavaClassName -> {
                        File outputDirectory = Paths.get("target/test-classes/unit/generated-sources/" + TEST_CLASS_FOLDER).toFile();
                        File baseDir = Paths.get("src/test/resources/unit/sources/" + TEST_CLASS_FOLDER + "/pom.xml").toFile();

                        try {
                            KubelessConvertMojo kubelessConvertMojo = createKubelessConvertMojo(inputJavaClassName, outputDirectory, baseDir);
                            kubelessConvertMojo.execute();

                            File outputClassFile = Paths.get(outputDirectory + File.separator + inputJavaClassName + ".java").toFile();
                            assertTrue(outputClassFile.exists());
                        } catch (MojoExecutionException | MojoFailureException e) {
                            fail();
                        }
                    });
        }

    }

    @Nested
    @DisplayName("Test on the input pom.xml")
    class InputPomTest {

        private static final String TEST_CLASS_FOLDER = "input-pom-test";

        @Test
        void whenInputPomDoesNotExist() throws MojoFailureException, MojoExecutionException {
            String inputJavaClassName = "AppFunction";
            File outputDirectory = Paths.get("target/test-classes/unit/generated-sources/" + TEST_CLASS_FOLDER).toFile();
            File baseDir = Paths.get("src/test/resources/unit/sources/" + TEST_CLASS_FOLDER + "/pom.xml").toFile();

            KubelessConvertMojo kubelessConvertMojo = createKubelessConvertMojo(inputJavaClassName, outputDirectory, baseDir);

            Throwable exception = assertThrows(MojoExecutionException.class, kubelessConvertMojo::execute);
            String expectedMessage = "The input pom.xml file is not present in src/test/resources/unit/sources/input-pom-test";
            assertEquals(expectedMessage, exception.getMessage());
        }

    }

    @Nested
    @DisplayName("Test only on the output Java Class and pom.xml")
    class OutputPomTest {

        private static final String TEST_CLASS_FOLDER = "output-java-class-pom-test";

        @Test
        void whenAllInputsInformedAreValid() throws MojoFailureException, MojoExecutionException, IOException, XmlPullParserException {
            String inputJavaClassName = "AppFunction";
            File outputDirectory = Paths.get("target/test-classes/unit/generated-sources/" + TEST_CLASS_FOLDER).toFile();
            File baseDir = Paths.get("src/test/resources/unit/sources/" + TEST_CLASS_FOLDER + "/pom.xml").toFile();

            KubelessConvertMojo kubelessConvertMojo = createKubelessConvertMojo(inputJavaClassName, outputDirectory, baseDir);
            kubelessConvertMojo.execute();

            //-- Assert Output Java Class

            File outputClassFile = Paths.get(outputDirectory + File.separator + inputJavaClassName + ".java").toFile();
            assertTrue(outputClassFile.exists());

            CompilationUnit compilationUnit = StaticJavaParser.parse(outputClassFile);
            assertTrue(standardJavaClassValidation(compilationUnit));

            //-- Assert Output pom.xml

            File outputPomFile = Paths.get(outputDirectory + File.separator + "pom.xml").toFile();
            assertTrue(outputPomFile.exists());

            Model model = mavenXpp3Reader.read(new FileInputStream(outputPomFile));
            assertTrue(standardPomValidation(model));
        }
    }

    //-- Helper Methods

    private KubelessConvertMojo createKubelessConvertMojo(String inputJavaClassName, File outputDirectory, File baseDir) {
        KubelessConvertMojo kubelessConvertMojo = new KubelessConvertMojo();
        kubelessConvertMojo.inputJavaClassName = inputJavaClassName;
        kubelessConvertMojo.outputDirectory = outputDirectory;

        MavenProject project = new MavenProject();
        project.setFile(baseDir);
        kubelessConvertMojo.project = project;

        return kubelessConvertMojo;
    }

    private boolean standardJavaClassValidation(CompilationUnit compilationUnit) {
        return compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getName().asString().equals("io.kubeless"))
                .orElse(false);
    }

    private boolean standardPomValidation(Model model) {
        return model.getArtifactId().equals("function") &&
                model.getName().equals("function") &&
                model.getParent().getGroupId().equals("io.kubeless") &&
                model.getParent().getArtifactId().equals("kubeless");
    }

    private static final MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();

}