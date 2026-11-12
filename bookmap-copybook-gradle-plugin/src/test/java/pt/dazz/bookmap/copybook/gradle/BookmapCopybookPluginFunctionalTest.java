package pt.dazz.bookmap.copybook.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.dazz.bookmap.copybook.tree.TestCopybooks;

class BookmapCopybookPluginFunctionalTest {

    private static final String TASK = ":generateCopybookClasses";
    private static final String OUT = "build/generated/sources/copybook/main/";

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() throws IOException {
        dir = dir.toRealPath();
        Files.writeString(dir.resolve("settings.gradle.kts"), """
            rootProject.name = "orders"
            buildCache { local { directory = file("cache") } }
            """);
    }

    private void buildScript(String plugins, String copybooks) throws IOException {
        String core = System.getProperty("bookmap.core.jar").replace("\\", "/");
        Files.writeString(dir.resolve("build.gradle.kts"), """
            plugins {
            %s
                id("pt.dazz.bookmap.copybook")
            }
            %s
            bookmap {
                copybooks {
            %s
                }
            }
            """.formatted(plugins, plugins.contains("java")
            ? "dependencies { implementation(files(\"" + core + "\")) }" : "", copybooks));
    }

    private void javaBuild(String copybooks) throws IOException {
        buildScript("    java", copybooks);
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath()
            .withArguments(arguments).forwardOutput();
    }

    private BuildResult build(String... arguments) {
        return runner(arguments).build();
    }

    private BuildResult fail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private TaskOutcome generate(String... extra) {
        String[] arguments = new String[extra.length + 1];
        arguments[0] = "generateCopybookClasses";
        System.arraycopy(extra, 0, arguments, 1, extra.length);
        return build(arguments).task(TASK).getOutcome();
    }

    private void append(Path file, String text) throws IOException {
        Files.writeString(file, Files.readString(file) + text);
    }

    private static final String TREE = """
                    sourceDir = layout.projectDirectory.dir("tree")
                    copyDirs.from("copylib")
                    packageName = "com.example.mainframe"
        """;

    private void tree() {
        TestCopybooks.copy("tree", dir);
        TestCopybooks.copy("copylib", dir);
    }

    @Test
    void compilesTheGeneratedClasses() throws IOException {
        tree();
        javaBuild(TREE + "        exclude(\"billing/**\")\n");
        BuildResult result = build("build");
        assertThat(result.task(TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve("build/classes/java/main/com/example/mainframe/orders/Order.class"))
            .exists();
    }

    @Test
    void secondBuildIsUpToDate() throws IOException {
        tree();
        javaBuild(TREE);
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(generate()).isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void copybookChangeRunsTaskAgain() throws IOException {
        tree();
        javaBuild(TREE);
        generate();
        append(dir.resolve("tree/orders/ORDER.cpy"),
            "           05  CURRENCY-CODE            PIC X(3).\n");
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/mainframe/orders/Order.java")).content()
            .contains("currencyCode");
    }

    @Test
    void jsonChangeRunsTaskAgain() throws IOException {
        tree();
        javaBuild(TREE);
        generate();
        Files.writeString(dir.resolve("tree/billing/INVOICE.json"),
            "{\"package\": \"com.example.billing\"}");
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/billing/Invoice.java")).exists();
    }

    @Test
    void memberChangeRunsTaskAgain() throws IOException {
        tree();
        javaBuild(TREE);
        generate();
        append(dir.resolve("copylib/ADDRESS.cpy"),
            "               10  POSTAL-CODE          PIC X(10).\n");
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/invoicing/Invoice.java")).content()
            .contains("postalCode");
    }

    @Test
    void memberChangeInJsonCopyDirRunsTaskAgain() throws IOException {
        TestCopybooks.copy("jsoncopy", dir);
        TestCopybooks.copy("jsoncopylib", dir);
        javaBuild("""
                    sourceDir = layout.projectDirectory.dir("jsoncopy")
                    packageName = "com.example.banking"
            """);
        generate();
        append(dir.resolve("jsoncopylib/BANK-ADDRESS.cpy"),
            "               10  BANK-COUNTRY         PIC X(2).\n");
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/banking/Account.java")).content()
            .contains("bankCountry");
    }

    @Test
    void newJsonCopyDirIsTrackedWithTheConfigurationCache() throws IOException {
        TestCopybooks.copy("jsoncopy", dir);
        TestCopybooks.copy("jsoncopylib", dir);
        Files.createDirectories(dir.resolve("otherlib"));
        Files.copy(dir.resolve("jsoncopylib/BANK-ADDRESS.cpy"),
            dir.resolve("otherlib/BANK-ADDRESS.cpy"));
        javaBuild("""
                    sourceDir = layout.projectDirectory.dir("jsoncopy")
                    packageName = "com.example.banking"
            """);
        assertThat(generate("--configuration-cache")).isEqualTo(TaskOutcome.SUCCESS);
        Files.writeString(dir.resolve("jsoncopy/ACCOUNT.json"), "{\"copyDirs\": [\"../otherlib\"]}");
        assertThat(generate("--configuration-cache")).isEqualTo(TaskOutcome.SUCCESS);
        append(dir.resolve("otherlib/BANK-ADDRESS.cpy"),
            "               10  SWIFT-CODE           PIC X(11).\n");
        BuildResult result = build("generateCopybookClasses", "--configuration-cache");
        assertThat(result.getOutput()).contains("Reusing configuration cache.");
        assertThat(result.task(TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/banking/Account.java")).content()
            .contains("swiftCode");
    }

    @Test
    void copyDirOrderChangeRunsTaskAgain() throws IOException {
        TestCopybooks.copy("tree", dir);
        for (String lib : new String[] {"liba", "libb"}) {
            Files.createDirectories(dir.resolve(lib));
            Files.writeString(dir.resolve(lib + "/ADDRESS.cpy"), """
                           05  ADDRESS.
                               10  CITY-%s               PIC X(20).
                """.formatted(lib.substring(3).toUpperCase()));
        }
        String copybooks = """
                    sourceDir = layout.projectDirectory.dir("tree")
                    packageName = "com.example.mainframe"
            """;
        javaBuild(copybooks + "            copyDirs.from(\"liba\", \"libb\")\n");
        generate();
        assertThat(dir.resolve(OUT + "com/example/invoicing/Invoice.java")).content()
            .contains("cityA");
        javaBuild(copybooks + "            copyDirs.from(\"libb\", \"liba\")\n");
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/invoicing/Invoice.java")).content()
            .contains("cityB");
    }

    @Test
    void removedCopybookRemovesItsClasses() throws IOException {
        tree();
        javaBuild(TREE);
        generate();
        Files.delete(dir.resolve("tree/orders/ORDER.cpy"));
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/mainframe/orders/Order.java")).doesNotExist();
        assertThat(dir.resolve(OUT + "com/example/invoicing/Invoice.java")).exists();
    }

    @Test
    void buildCacheGivesFromCache() throws IOException {
        tree();
        javaBuild(TREE);
        assertThat(generate("--build-cache")).isEqualTo(TaskOutcome.SUCCESS);
        build("clean");
        assertThat(generate("--build-cache")).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(dir.resolve(OUT + "com/example/mainframe/orders/Order.java")).exists();
    }

    @Test
    void removedCopybookIsAlsoRemovedFromCache() throws IOException {
        tree();
        javaBuild(TREE);
        Path order = dir.resolve("tree/orders/ORDER.cpy");
        String text = Files.readString(order);
        Path orderClass = dir.resolve(OUT + "com/example/mainframe/orders/Order.java");
        assertThat(generate("--build-cache")).isEqualTo(TaskOutcome.SUCCESS);
        Files.delete(order);
        assertThat(generate("--build-cache")).isEqualTo(TaskOutcome.SUCCESS);
        Files.writeString(order, text);
        assertThat(generate("--build-cache")).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(orderClass).exists();
        Files.delete(order);
        assertThat(generate("--build-cache")).isEqualTo(TaskOutcome.FROM_CACHE);
        assertThat(orderClass).doesNotExist();
        assertThat(dir.resolve(OUT + "com/example/invoicing/Invoice.java")).exists();
    }

    @Test
    void configurationCacheIsReused() throws IOException {
        tree();
        javaBuild(TREE);
        build("generateCopybookClasses", "--configuration-cache");
        assertThat(build("generateCopybookClasses", "--configuration-cache").getOutput())
            .contains("Reusing configuration cache.");
    }

    @Test
    void copybookErrorFailsTheBuild() throws IOException {
        TestCopybooks.copy("broken", dir);
        javaBuild("""
                    sourceDir = layout.projectDirectory.dir("broken")
                    packageName = "com.example.mainframe"
            """);
        String output = fail("build").getOutput();
        assertThat(output).contains("BAD.cpy:2:", "WORSE.cpy:2:",
            "2 copybook(s) have errors. See the messages above.");
    }

    @Test
    void invalidJsonFailsWithTheFilePath() throws IOException {
        TestCopybooks.copy("badjson", dir);
        javaBuild("""
                    sourceDir = layout.projectDirectory.dir("badjson")
                    packageName = "com.example.shipping"
            """);
        assertThat(fail("build").getOutput()).contains(dir.resolve("badjson/SHIPMENT.json")
            + ": ");
    }

    @Test
    void missingMemberFailsWithTheSearchedDirectories() throws IOException {
        tree();
        Files.createDirectories(dir.resolve("emptylib"));
        javaBuild("""
                    sourceDir = layout.projectDirectory.dir("tree")
                    copyDirs.from("emptylib")
                    packageName = "com.example.mainframe"
            """);
        assertThat(fail("build").getOutput()).contains("copy member 'ADDRESS' not found in "
            + dir.resolve("emptylib"));
    }

    @Test
    void excludeKeepsAMemberOut() throws IOException {
        TestCopybooks.copy("tree", dir.resolve("src/main/copybook"));
        TestCopybooks.copy("copylib", dir.resolve("src/main/copybook/members"));
        javaBuild("""
                    sourceDir = layout.projectDirectory.dir("src/main/copybook/tree")
                    copyDirs.from("src/main/copybook/members/copylib")
                    packageName = "com.example.mainframe"
                    exclude("billing/**")
            """);
        assertThat(build("build").task(TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        Files.writeString(dir.resolve("build.gradle.kts"),
            Files.readString(dir.resolve("build.gradle.kts")).replace("/tree\")", "\")")
                .replace("exclude(\"billing/**\")", "exclude(\"members/**\", \"**/billing/**\")"));
        assertThat(build("build").task(TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/mainframe/tree/orders/Order.java")).exists();
    }

    @Test
    void collisionFailsAndPackagePerCopybookFixes() throws IOException {
        TestCopybooks.copy("collision", dir);
        TestCopybooks.copy("copylib", dir);
        String copybooks = """
                    sourceDir = layout.projectDirectory.dir("collision")
                    copyDirs.from("copylib")
                    packageName = "com.example.mainframe"
            """;
        javaBuild(copybooks);
        assertThat(fail("build").getOutput()).contains(
            "class com.example.mainframe.OrderLine comes from CUSTOMER-ORDER.cpy and"
                + " CUSTOMER-RETURN.cpy", "packagePerCopybook = true");
        javaBuild(copybooks + "            packagePerCopybook = true\n");
        assertThat(build("build").task(TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void missingPackageNameFails() throws IOException {
        tree();
        javaBuild("""
                    sourceDir = layout.projectDirectory.dir("tree")
                    exclude("billing/**")
            """);
        assertThat(fail("build").getOutput()).contains("Set bookmap.copybooks.packageName.");
    }

    @Test
    void noSourceDirPassesAndDeletesOldFiles() throws IOException {
        javaBuild("");
        Path stale = dir.resolve(OUT + "com/example/old/Stale.java");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "class Stale {}");
        assertThat(build("build").task(TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(stale).doesNotExist();
    }

    @Test
    void withoutJavaPluginApplies() throws IOException {
        tree();
        buildScript("", TREE);
        assertThat(generate()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve(OUT + "com/example/mainframe/orders/Order.java")).exists();
    }
}
