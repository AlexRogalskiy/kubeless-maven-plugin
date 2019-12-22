# kubeless-maven-plugin

Maven plugin that reads a class in `/src/main/io/kubeless` directory and the `pom.xml` of the project and converts them into a ready to use inputs in `kubeless function deploy` command.

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
        <outputJavaClassName>KAppFunction</outputJavaClassName>
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

| Parameter               | Required | Read-Only | Default | Description |
| ----------------------- | -------- | --------- | ------- | ----------- |
| inputJavaClassName      | true     | false     |         | Name of the input Java Class that contains Kubeless Functions |
| outputJavaClassName     | true     | false     |         | Name with which the output Java Class will be created|
| inputJavaClassDirectory | true     | **true**  | ${basedir}/src/main/java/io/kubeless | Folder where the input Java Class is located |
| inputPomDirectory       | true     | **true**  | ${basedir} | Folder where the `pom.xml` file of the project is located |
| outputDirectory         | true     | false     | ${project.build.directory}/generated-sources/kubeless | Folder where the output Java Class and the new `pom.xml` file will be created |

### Tests

#### Run only Unit Tests

Unit tests are written using [`JUnit 5`](https://junit.org/junit5/)
```
mvn clean test
```

#### Run only Integration Tests

Integration tests uses [`maven-invoker-plugin`](https://maven.apache.org/plugins/maven-invoker-plugin/) and the `runt-its` profile and are in `src/it` folder.
```
mvn clean verify -DskipTests -Prun-its
```

#### Run all Tests

The command below will run Unit and Integration Tests
```
mvn clean verify -Prun-its
```
