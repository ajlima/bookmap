package pt.dazz.bookmap.copybook.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.dazz.bookmap.copybook.tree.TestCopybooks;

/** Runs the plugin with Maven 3.6.3 (the minimum) and Maven 3.9.16. */
class MavenIntegrationTest {

    private static final String UP_TO_DATE = "The generated classes are up to date.";

    @TempDir
    Path dir;

    @BeforeAll
    static void cleanLocalRepository() throws IOException {
        MavenRunner.forgetBookmapArtifacts();
    }

    private static String pom(String artifactId, String configuration) {
        return """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>%s</artifactId>
              <version>1.0</version>
              <properties>
                <maven.compiler.release>21</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>pt.dazz.bookmap</groupId>
                  <artifactId>bookmap-core</artifactId>
                  <version>${bookmap.version}</version>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                      <annotationProcessorPaths>
                        <path>
                          <groupId>pt.dazz.bookmap</groupId>
                          <artifactId>bookmap-processor</artifactId>
                          <version>${bookmap.version}</version>
                        </path>
                      </annotationProcessorPaths>
                    </configuration>
                  </plugin>
                  <plugin>
                    <groupId>pt.dazz.bookmap</groupId>
                    <artifactId>bookmap-copybook-maven-plugin</artifactId>
                    <version>${bookmap.version}</version>
                    <executions>
                      <execution>
                        <goals>
                          <goal>generate</goal>
                        </goals>
                      </execution>
                    </executions>
                    <configuration>
            %s
                    </configuration>
                  </plugin>
                </plugins>
              </build>
            </project>
            """.formatted(artifactId, configuration);
    }

    private static final String TREE_CONFIGURATION = """
                      <packageName>com.example.mainframe</packageName>
                      <copyDirs>
                        <copyDir>src/main/copylib</copyDir>
                      </copyDirs>
                      <excludes>
                        <exclude>billing/**</exclude>
                      </excludes>
        """;

    /** A project with the tree fixture (without the Lombok copybook) and the copylib. */
    private Path treeProject(Path project, String configuration) throws IOException {
        Path main = Files.createDirectories(project.resolve("src/main"));
        Files.move(TestCopybooks.copy("tree", main), main.resolve("copybook"));
        Files.move(TestCopybooks.copy("copylib", main), main.resolve("copylib"));
        Files.writeString(project.resolve("pom.xml"), pom("orders", configuration));
        return project;
    }

    @ParameterizedTest(name = "{displayName} with Maven {0}")
    @ValueSource(strings = {"3.6.3", "3.9.16"})
    void generatesAndCompiles(String version) throws Exception {
        Path project = treeProject(dir, TREE_CONFIGURATION);
        MavenRunner.Result result = new MavenRunner(version).run(project, "compile");
        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(project.resolve("target/classes/com/example/mainframe/orders/Order.class"))
            .exists();
        assertThat(project.resolve(
            "target/classes/com/example/mainframe/orders/Order_BookCodec.class")).exists();
    }

    @ParameterizedTest(name = "{displayName} with Maven {0}")
    @ValueSource(strings = {"3.6.3", "3.9.16"})
    void secondBuildIsUpToDate(String version) throws Exception {
        Path project = treeProject(dir, TREE_CONFIGURATION);
        MavenRunner maven = new MavenRunner(version);
        assertThat(maven.run(project, "compile").output()).doesNotContain(UP_TO_DATE);
        MavenRunner.Result second = maven.run(project, "compile");
        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(second.output()).contains(UP_TO_DATE);
    }

    @ParameterizedTest(name = "{displayName} with Maven {0}")
    @ValueSource(strings = {"3.6.3", "3.9.16"})
    void optionsFileAndSubpackages(String version) throws Exception {
        Path project = treeProject(dir, TREE_CONFIGURATION);
        Files.writeString(project.resolve("src/main/copybook/orders/ORDER.json"),
            "{\"package\": \"com.example.ordering\"}");
        MavenRunner.Result result = new MavenRunner(version).run(project, "compile");
        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(project.resolve("target/classes/com/example/ordering/Order.class")).exists();
    }

    @ParameterizedTest(name = "{displayName} with Maven {0}")
    @ValueSource(strings = {"3.6.3", "3.9.16"})
    void missingMemberFails(String version) throws Exception {
        Path project = treeProject(dir, """
                      <packageName>com.example.mainframe</packageName>
            """);
        MavenRunner.Result result = new MavenRunner(version).run(project, "compile");
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("copy member 'ADDRESS' not found");
    }

    @ParameterizedTest(name = "{displayName} with Maven {0}")
    @ValueSource(strings = {"3.6.3", "3.9.16"})
    void collisionFailsAndPackagePerCopybookFixes(String version) throws Exception {
        Path main = Files.createDirectories(dir.resolve("src/main"));
        Files.move(TestCopybooks.copy("collision", main), main.resolve("copybook"));
        Files.move(TestCopybooks.copy("copylib", main), main.resolve("copylib"));
        Files.writeString(dir.resolve("pom.xml"), pom("returns", """
                      <packageName>com.example.mainframe</packageName>
                      <copyDirs>
                        <copyDir>src/main/copylib</copyDir>
                      </copyDirs>
            """));
        MavenRunner maven = new MavenRunner(version);
        MavenRunner.Result failed = maven.run(dir, "compile");
        assertThat(failed.exitCode()).isNotZero();
        assertThat(failed.output()).contains("<packagePerCopybook>true</packagePerCopybook>");
        MavenRunner.Result fixed = maven.run(dir, "compile",
            "-Dbookmap.copybook.packagePerCopybook=true");
        assertThat(fixed.exitCode()).as(fixed.output()).isZero();
    }

    @ParameterizedTest(name = "{displayName} with Maven {0}")
    @ValueSource(strings = {"3.6.3", "3.9.16"})
    void multiModuleReactor(String version) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>parent</artifactId>
              <version>1.0</version>
              <packaging>pom</packaging>
              <modules>
                <module>mainframe-model</module>
                <module>orders-service</module>
              </modules>
            </project>
            """);
        Path model = treeProject(Files.createDirectories(dir.resolve("mainframe-model")),
            TREE_CONFIGURATION);
        String modelPom = Files.readString(model.resolve("pom.xml")).replace(
            "<artifactId>orders</artifactId>\n  <version>1.0</version>",
            "<artifactId>mainframe-model</artifactId>\n  <version>1.0</version>");
        Files.writeString(model.resolve("pom.xml"), modelPom.replace("<modelVersion>4.0.0"
            + "</modelVersion>", "<modelVersion>4.0.0</modelVersion>\n  <parent>"
            + "<groupId>com.example</groupId><artifactId>parent</artifactId>"
            + "<version>1.0</version></parent>"));
        Path service = Files.createDirectories(dir.resolve(
            "orders-service/src/main/java/com/example/service"));
        Files.writeString(service.resolve("OrderService.java"), """
            package com.example.service;

            import com.example.mainframe.orders.Order;

            public class OrderService {
                public Order newOrder(String id) {
                    Order order = new Order();
                    order.setOrderId(id);
                    return order;
                }
            }
            """);
        Files.writeString(dir.resolve("orders-service/pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0</version>
              </parent>
              <artifactId>orders-service</artifactId>
              <properties>
                <maven.compiler.release>21</maven.compiler.release>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>mainframe-model</artifactId>
                  <version>1.0</version>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                  </plugin>
                </plugins>
              </build>
            </project>
            """);
        MavenRunner.Result result = new MavenRunner(version).run(dir, "package");
        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(dir.resolve("orders-service/target/classes/com/example/service/"
            + "OrderService.class")).exists();
    }
}
