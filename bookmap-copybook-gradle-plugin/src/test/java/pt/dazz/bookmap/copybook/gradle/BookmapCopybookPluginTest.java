package pt.dazz.bookmap.copybook.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookmapCopybookPluginTest {

    @TempDir
    Path dir;

    private Project project;

    @BeforeEach
    void setUp() throws IOException {
        dir = dir.toRealPath();
        project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
    }

    private GenerateCopybookClasses task() {
        project.getPluginManager().apply("pt.dazz.bookmap.copybook");
        return (GenerateCopybookClasses) project.getTasks().getByName("generateCopybookClasses");
    }

    private CopybooksSpec spec() {
        return project.getExtensions().getByType(BookmapExtension.class).getCopybooks();
    }

    private Set<String> copybookNames(GenerateCopybookClasses task) {
        return task.getCopybookFiles().getFiles().stream().map(File::getName)
            .collect(Collectors.toSet());
    }

    private void write(String path) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
    }

    @Test
    void registersTheExtensionAndTheTask() {
        GenerateCopybookClasses task = task();
        assertThat(project.getExtensions().findByName("bookmap"))
            .isInstanceOf(BookmapExtension.class);
        assertThat(task.getGroup()).isEqualTo("build");
        assertThat(task.getDescription())
            .isEqualTo("Generates the BookMap classes from the COBOL copybooks.");
    }

    @Test
    void setsTheConventions() {
        GenerateCopybookClasses task = task();
        assertThat(task.getSourceDir().get().getAsFile()).isEqualTo(dir.resolve(
            "src/main/copybook").toFile());
        assertThat(task.getOutputDir().get().getAsFile()).isEqualTo(dir.resolve(
            "build/generated/sources/copybook/main").toFile());
        assertThat(task.getLombok().get()).isFalse();
        assertThat(task.getPackagePerCopybook().get()).isFalse();
        assertThat(task.getIncludes().get()).isEmpty();
        assertThat(task.getExcludes().get()).isEmpty();
        assertThat(task.getPackageName().isPresent()).isFalse();
    }

    @Test
    void mapsTheExtensionToTheTask() {
        GenerateCopybookClasses task = task();
        project.getExtensions().getByType(BookmapExtension.class).copybooks(spec -> {
            spec.getPackageName().set("com.example.mainframe");
            spec.getLombok().set(true);
            spec.getPackagePerCopybook().set(true);
            spec.getCopyDirs().from("src/main/copylib");
            spec.getSourceDir().set(dir.resolve("copybooks").toFile());
            spec.getOutputDir().set(dir.resolve("generated").toFile());
        });
        assertThat(task.getPackageName().get()).isEqualTo("com.example.mainframe");
        assertThat(task.getLombok().get()).isTrue();
        assertThat(task.getPackagePerCopybook().get()).isTrue();
        assertThat(task.getCopyDirs().getFiles())
            .containsExactly(dir.resolve("src/main/copylib").toFile());
        assertThat(task.getSourceDir().get().getAsFile()).isEqualTo(dir.resolve("copybooks")
            .toFile());
        assertThat(task.getOutputDir().get().getAsFile()).isEqualTo(dir.resolve("generated")
            .toFile());
    }

    @Test
    void withoutJavaAddsNoSourceDir() {
        task();
        assertThat(project.getExtensions().findByType(SourceSetContainer.class)).isNull();
    }

    @Test
    void withJavaAddsTheOutputToMain() {
        project.getPluginManager().apply("java");
        task();
        SourceSetContainer sourceSets = project.getExtensions()
            .getByType(SourceSetContainer.class);
        assertThat(sourceSets.getByName("main").getJava().getSrcDirs())
            .contains(dir.resolve("build/generated/sources/copybook/main").toFile());
    }

    @Test
    void defaultIncludeIsCpy() throws IOException {
        write("src/main/copybook/ORDER.cpy");
        write("src/main/copybook/members/ADDRESS.cpy");
        write("src/main/copybook/INVOICE.cob");
        assertThat(copybookNames(task())).containsExactlyInAnyOrder("ORDER.cpy", "ADDRESS.cpy");
    }

    @Test
    void includeReplacesTheDefault() throws IOException {
        write("src/main/copybook/ORDER.cpy");
        write("src/main/copybook/INVOICE.cob");
        GenerateCopybookClasses task = task();
        assertThat(spec().include("**/*.cob")).isSameAs(spec());
        assertThat(copybookNames(task)).containsExactly("INVOICE.cob");
    }

    @Test
    void excludeFiltersFiles() throws IOException {
        write("src/main/copybook/ORDER.cpy");
        write("src/main/copybook/members/ADDRESS.cpy");
        GenerateCopybookClasses task = task();
        assertThat(spec().exclude("members/**")).isSameAs(spec());
        assertThat(copybookNames(task)).containsExactly("ORDER.cpy");
    }

    @Test
    void optionsFilesAreTheJsonFiles() throws IOException {
        write("src/main/copybook/ORDER.cpy");
        write("src/main/copybook/billing/INVOICE.json");
        assertThat(task().getOptionsFiles().getFiles()).extracting(File::getName)
            .containsExactly("INVOICE.json");
    }

    @Test
    void taskActionGenerates() {
        GenerateCopybookClasses task = task();
        Path source = TestCopybooks.copy("tree", dir);
        Path copylib = TestCopybooks.copy("copylib", dir);
        spec().getSourceDir().set(source.toFile());
        spec().getCopyDirs().from(copylib.toFile());
        spec().getPackageName().set("com.example.mainframe");
        task.generate();
        assertThat(dir.resolve(
            "build/generated/sources/copybook/main/com/example/mainframe/orders/Order.java"))
            .exists();
    }

    @Test
    void taskActionWrapsTheFailure() {
        GenerateCopybookClasses task = task();
        spec().getSourceDir().set(TestCopybooks.copy("tree", dir).toFile());
        spec().exclude("billing/**");
        assertThatThrownBy(task::generate).isExactlyInstanceOf(GradleException.class)
            .hasMessage("Set bookmap.copybooks.packageName.");
    }

    @Test
    void taskActionWrapsAnIoFailure() throws IOException {
        GenerateCopybookClasses task = task();
        Files.createDirectories(dir.resolve("build"));
        Files.writeString(dir.resolve("build/out"), "not a directory");
        spec().getOutputDir().set(dir.resolve("build/out/main").toFile());
        assertThatThrownBy(task::generate).isExactlyInstanceOf(GradleException.class)
            .hasMessageStartingWith("Cannot write the generated classes: ");
    }

    @Test
    void configCopyDirsComeFromTheOptionsFiles() {
        GenerateCopybookClasses task = task();
        Path source = TestCopybooks.copy("jsoncopy", dir);
        spec().getSourceDir().set(source.toFile());
        assertThat(task.getConfigCopyDirs().getFiles())
            .containsExactly(dir.resolve("jsoncopylib").toFile());
    }

    @Test
    void logWritesWarningsAndErrors() {
        GenerateCopybookClasses task = task();
        Path source = dir.resolve("mixed");
        TestCopybooks.copy("warning", source);
        TestCopybooks.copy("broken", source);
        spec().getSourceDir().set(source.toFile());
        spec().getPackageName().set("com.example.mainframe");
        assertThatThrownBy(task::generate).isExactlyInstanceOf(GradleException.class)
            .hasMessage("2 copybook(s) have errors. See the messages above.");
    }
}
