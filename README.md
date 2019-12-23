# kubeless-maven-plugin

Maven plugin that reads a class in `/src/main/java/io/kubeless` directory and the `pom.xml` of the project and converts them into a ready to use inputs in `kubeless function deploy` command.

> **Tip:** Use the Maven Archetype [`kubeless-dev-environment-archetype`](https://github.com/ivangfr/kubeless-dev-environment-archetype) to implement your Kubeless Functions.

## Maven Plugin

### Setup

A basic setup is
```xml
<plugin>
    <groupId>org.ivanfranchin</groupId>
    <artifactId>kubeless-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <configuration>
        <inputJavaClassName>AppFunction</inputJavaClassName>
    </configuration>
    <executions>
        <execution>
            <!-- Just an unique id for the execution -->
            <id>kubeless-convert</id>
            <!-- kubeless-maven-plugin runs in process-resources phase by default -->
            <!-- <phase>process-resources</phase> -->
            <goals>
                <goal>convert</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Configuration

- #### inputJavaClassName
  Name of the input Java Class that contains `Kubeless` Functions. The input Java Class file must be in `${basedir}/src/main/java/io/kubeless`.
  
- #### outputDirectory
  Folder where the output Java Class and the new `pom.xml` file will be created. The default value is `${project.build.directory}/generated-sources/kubeless`.

### Tests

- #### Run only Unit Tests
  Unit tests are written using [`JUnit 5`](https://junit.org/junit5/)
  ```
  mvn clean test
  ```

- #### Run only Integration Tests

  Integration tests uses [`maven-invoker-plugin`](https://maven.apache.org/plugins/maven-invoker-plugin/) and the `runt-its` profile and are in `src/it` folder.
  ```
  mvn clean verify -DskipTests -Prun-its
  ```

- #### Run all Tests

  The command below will run Unit and Integration Tests
  ```
  mvn clean verify -Prun-its
  ```
