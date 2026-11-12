package pt.dazz.bookmap.copybook.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.dazz.bookmap.copybook.tree.TestCopybooks;

class GenerateMojoTest {

    private static final String BASE = "com.example.mainframe";

    @TempDir
    Path dir;

    private GenerateMojo mojo;
    private final RecordingLog log = new RecordingLog();

    @BeforeEach
    void setUp() throws IOException {
        dir = dir.toRealPath();
        mojo = new GenerateMojo();
        mojo.project = new MavenProject();
        mojo.sourceDir = dir.resolve("src/main/copybook").toFile();
        mojo.copyDirs = new ArrayList<>();
        mojo.outputDir = dir.resolve("target/generated-sources/copybook").toFile();
        mojo.packageName = BASE;
        mojo.setLog(log);
    }

    private Path out(String path) {
        return mojo.outputDir.toPath().resolve(path);
    }

    private void tree() {
        mojo.sourceDir = TestCopybooks.copy("tree", dir).toFile();
        mojo.copyDirs.add(TestCopybooks.copy("copylib", dir).toFile());
    }

    @Test
    void generatesIntoDerivedPackages() throws Exception {
        tree();
        mojo.execute();
        assertThat(out("com/example/mainframe/orders/Order.java")).exists();
        assertThat(out("com/example/invoicing/Invoice.java")).content().contains("lombok");
    }

    @Test
    void lombokAndPackagePerCopybookApply() throws Exception {
        tree();
        mojo.lombok = true;
        mojo.packagePerCopybook = true;
        mojo.execute();
        assertThat(out("com/example/mainframe/orders/order/Order.java")).content()
            .contains("lombok");
    }

    @Test
    void addsTheSourceRoot() throws Exception {
        tree();
        mojo.execute();
        assertThat(mojo.project.getCompileSourceRoots())
            .containsExactly(mojo.outputDir.getAbsolutePath());
    }

    @Test
    void skipAddsTheSourceRootAndGeneratesNothing() throws Exception {
        tree();
        mojo.skip = true;
        mojo.execute();
        assertThat(mojo.project.getCompileSourceRoots())
            .containsExactly(mojo.outputDir.getAbsolutePath());
        assertThat(mojo.outputDir).doesNotExist();
        assertThat(log.infos).containsExactly("Skipped.");
    }

    @Test
    void includesReplaceTheDefault() throws Exception {
        tree();
        Files.move(mojo.sourceDir.toPath().resolve("orders/ORDER.cpy"),
            mojo.sourceDir.toPath().resolve("orders/ORDER.cob"));
        mojo.includes = List.of("**/*.cob");
        mojo.execute();
        assertThat(out("com/example/mainframe/orders/Order.java")).exists();
        assertThat(out("com/example/invoicing/Invoice.java")).doesNotExist();
    }

    @Test
    void emptyIncludesUseTheDefault() throws Exception {
        tree();
        mojo.includes = List.of();
        mojo.execute();
        assertThat(out("com/example/mainframe/orders/Order.java")).exists();
    }

    @Test
    void excludesFilterFiles() throws Exception {
        tree();
        mojo.excludes = List.of("billing/**");
        mojo.execute();
        assertThat(out("com/example/mainframe/orders/Order.java")).exists();
        assertThat(out("com/example/invoicing/Invoice.java")).doesNotExist();
    }

    @Test
    void noCopyDirsParameterIsPermitted() throws Exception {
        mojo.sourceDir = TestCopybooks.copy("tree", dir).toFile();
        mojo.copyDirs = null;
        mojo.excludes = List.of("billing/**");
        mojo.execute();
        assertThat(out("com/example/mainframe/orders/Order.java")).exists();
    }

    @Test
    void missingSourceDirGeneratesNothing() throws Exception {
        mojo.execute();
        assertThat(mojo.outputDir).isEmptyDirectory();
    }

    @Test
    void missingPackageNameFails() {
        tree();
        mojo.packageName = null;
        mojo.excludes = List.of("billing/**");
        assertThatThrownBy(mojo::execute).isExactlyInstanceOf(MojoFailureException.class)
            .hasMessage("Set the packageName parameter of bookmap-copybook-maven-plugin.");
    }

    @Test
    void collisionFailsWithTheMavenHint() {
        mojo.sourceDir = TestCopybooks.copy("collision", dir).toFile();
        mojo.copyDirs.add(TestCopybooks.copy("copylib", dir).toFile());
        assertThatThrownBy(mojo::execute).isExactlyInstanceOf(MojoFailureException.class)
            .hasMessageContaining("Set <packagePerCopybook>true</packagePerCopybook>, set");
    }

    @Test
    void ioFailureIsAnExecutionException() throws IOException {
        Files.createDirectories(dir.resolve("target"));
        Files.writeString(dir.resolve("target/file"), "not a directory");
        mojo.outputDir = dir.resolve("target/file/copybook").toFile();
        assertThatThrownBy(mojo::execute).isExactlyInstanceOf(MojoExecutionException.class)
            .hasMessageStartingWith("Cannot write the generated classes: ");
    }

    @Test
    void logGetsWarningsAndErrors() {
        Path source = dir.resolve("mixed");
        TestCopybooks.copy("warning", source);
        TestCopybooks.copy("broken", source);
        mojo.sourceDir = source.toFile();
        assertThatThrownBy(mojo::execute).isExactlyInstanceOf(MojoFailureException.class)
            .hasMessage("2 copybook(s) have errors. See the messages above.");
        assertThat(log.warnings).anySatisfy(w -> assertThat(w).contains("is skipped"));
        assertThat(log.errors).hasSize(2);
    }

    /** A log that records the messages. */
    static final class RecordingLog extends SystemStreamLog {
        final List<String> infos = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void info(CharSequence content) {
            infos.add(content.toString());
        }

        @Override
        public void warn(CharSequence content) {
            warnings.add(content.toString());
        }

        @Override
        public void error(CharSequence content) {
            errors.add(content.toString());
        }
    }
}
