package pt.dazz.bookmap.copybook.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CopybookTreeTest {

    private static final String BASE = "com.example.mainframe";

    @TempDir
    Path work;

    private Path output;
    private Path copylib;
    private final RecordingLog log = new RecordingLog();

    @BeforeEach
    void setUp() {
        output = work.resolve("out");
        copylib = TestCopybooks.copy("copylib", work);
    }

    private CopybookTree.Settings settings(Path sourceDir, List<Path> copyDirs,
                                                 String base, boolean lombok,
                                                 boolean perCopybook) {
        return settings(sourceDir, copyDirs, base, lombok, perCopybook, null);
    }

    private CopybookTree.Settings settings(Path sourceDir, List<Path> copyDirs,
                                                 String base, boolean lombok,
                                                 boolean perCopybook, Integer maxRecordLength) {
        return new CopybookTree.Settings(sourceDir, copyDirs, base, lombok, perCopybook,
            "Set bookmap.copybooks.packageName.",
            "bookmap.copybooks.packagePerCopybook = true", maxRecordLength);
    }

    private void run(String name, List<Path> copyDirs, String base, boolean lombok,
                     boolean perCopybook) throws IOException {
        Path source = TestCopybooks.copy(name, work);
        CopybookTree.run(settings(source, copyDirs, base, lombok, perCopybook),
            TestCopybooks.copybooks(source), output, log);
    }

    @Test
    void generatesIntoDerivedPackages() throws IOException {
        run("tree", List.of(copylib), BASE, false, false);
        assertThat(output.resolve("com/example/mainframe/orders/Order.java")).exists();
        assertThat(output.resolve("com/example/invoicing/Invoice.java")).exists();
        assertThat(output.resolve("com/example/invoicing/Invoice.java")).content()
            .contains("street");
    }

    @Test
    void optionsFileLombokWins() throws IOException {
        run("tree", List.of(copylib), BASE, false, false);
        assertThat(output.resolve("com/example/invoicing/Invoice.java")).content()
            .contains("lombok");
        assertThat(output.resolve("com/example/mainframe/orders/Order.java")).content()
            .doesNotContain("lombok");
    }

    @Test
    void extensionLombokApplies() throws IOException {
        run("tree", List.of(copylib), BASE, true, false);
        assertThat(output.resolve("com/example/mainframe/orders/Order.java")).content()
            .contains("lombok");
    }

    @Test
    void perCopybookAddsASegment() throws IOException {
        run("tree", List.of(copylib), BASE, false, true);
        assertThat(output.resolve("com/example/mainframe/orders/order/Order.java")).exists();
        assertThat(output.resolve("com/example/invoicing/Invoice.java")).exists();
    }

    @Test
    void missingPackageNameFails() {
        Path source = TestCopybooks.copy("tree", work);
        assertThatThrownBy(() -> CopybookTree.run(
            settings(source, List.of(copylib), null, false, false),
            List.of(source.resolve("orders/ORDER.cpy")), output, log))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessage("Set bookmap.copybooks.packageName.");
        assertThat(output).isEmptyDirectory();
    }

    @Test
    void missingPackageNameIsNoErrorWhenTheFileSetsIt() throws IOException {
        Path source = TestCopybooks.copy("tree", work);
        CopybookTree.run(settings(source, List.of(copylib), null, false, false),
            List.of(source.resolve("billing/INVOICE.cpy")), output, log);
        assertThat(output.resolve("com/example/invoicing/Invoice.java")).exists();
    }

    @Test
    void noCopybookCleansTheOutput() throws IOException {
        Files.createDirectories(output.resolve("old/pkg"));
        Files.writeString(output.resolve("old/pkg/Stale.java"), "class Stale {}");
        Files.writeString(output.resolve("Top.java"), "class Top {}");
        CopybookTree.run(settings(work, List.of(), BASE, false, false), List.of(), output,
            log);
        assertThat(output).isEmptyDirectory();
    }

    @Test
    void createsAMissingOutputDirectory() throws IOException {
        CopybookTree.run(settings(work, List.of(), BASE, false, false), List.of(), output,
            log);
        assertThat(output).isEmptyDirectory();
    }

    @Test
    void missingCopyMemberFails() {
        Path source = TestCopybooks.copy("tree", work);
        assertThatThrownBy(() -> CopybookTree.run(
            settings(source, List.of(), BASE, false, false),
            List.of(source.resolve("billing/INVOICE.cpy")), output, log))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessage("1 copybook(s) have errors. See the messages above.");
        assertThat(log.errors).anySatisfy(e -> assertThat(e)
            .contains("copy member 'ADDRESS' not found"));
    }

    @Test
    void reportsErrorsOfAllCopybooks() {
        assertThatThrownBy(() -> run("broken", List.of(), BASE, false, false))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessage("2 copybook(s) have errors. See the messages above.");
        assertThat(log.errors).anySatisfy(e -> assertThat(e).contains("BAD.cpy"));
        assertThat(log.errors).anySatisfy(e -> assertThat(e).contains("WORSE.cpy"));
        assertThat(output).isEmptyDirectory();
    }

    @Test
    void warningsGoToWarn() throws IOException {
        run("warning", List.of(), BASE, false, false);
        assertThat(log.warnings).singleElement().asString()
            .contains("PAYMENT-NUMBER redefines PAYMENT-ID and is skipped");
        assertThat(log.errors).isEmpty();
        assertThat(output.resolve("com/example/mainframe/Payment.java")).exists();
    }

    @Test
    void infosGoToInfo() throws IOException {
        run("warning", List.of(), BASE, false, false);
        assertThat(log.infos).singleElement().asString()
            .endsWith("info: record PAYMENT has 8 bytes");
    }

    @Test
    void maxRecordLengthOfTheSettingsGivesAWarning() throws IOException {
        Path source = TestCopybooks.copy("warning", work);
        CopybookTree.run(settings(source, List.of(), BASE, false, false, 4),
            TestCopybooks.copybooks(source), output, log);
        assertThat(log.warnings).anySatisfy(w -> assertThat(w).contains("record PAYMENT has 8"
            + " bytes, more than the maximum record length of 4 bytes."));
        assertThat(output.resolve("com/example/mainframe/Payment.java")).exists();
    }

    @Test
    void maxRecordLengthOfTheOptionsFileWins() throws IOException {
        Path source = TestCopybooks.copy("warning", work);
        Files.writeString(source.resolve("PAYMENT.json"), "{\"maxRecordLength\": 8}");
        CopybookTree.run(settings(source, List.of(), BASE, false, false, 4),
            TestCopybooks.copybooks(source), output, log);
        assertThat(log.warnings).noneSatisfy(w -> assertThat(w).contains("maximum"));
    }

    @Test
    void maxRecordLengthMustBePositive() {
        Path source = TestCopybooks.copy("warning", work);
        assertThatThrownBy(() -> CopybookTree.run(settings(source, List.of(), BASE, false,
            false, 0), TestCopybooks.copybooks(source), output, log))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessage("The maximum record length must be 1 or more, not 0.");
    }

    @Test
    void invalidJsonGivesTheFilePath() {
        assertThatThrownBy(() -> run("badjson", List.of(), BASE, false, false))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessage("1 copybook(s) have errors. See the messages above.");
        assertThat(log.errors).singleElement().asString()
            .startsWith(work.resolve("badjson/SHIPMENT.json") + ": ");
    }

    private Path shipment(byte[] options) throws IOException {
        Path source = TestCopybooks.copy("badjson", work);
        Files.write(source.resolve("SHIPMENT.json"), options);
        return source;
    }

    @Test
    void unreadableOptionsFileGivesTheFilePath() throws IOException {
        Path source = shipment(new byte[] {'{', (byte) 0xFF, '}'});
        assertThatThrownBy(() -> CopybookTree.run(
            settings(source, List.of(), BASE, false, false),
            List.of(source.resolve("SHIPMENT.cpy")), output, log))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessage("1 copybook(s) have errors. See the messages above.");
        assertThat(log.errors).singleElement().asString()
            .startsWith(source.resolve("SHIPMENT.json") + ": cannot read the file: ");
        assertThat(CopybookTree.configCopyDirs(List.of(source.resolve("SHIPMENT.cpy"))))
            .isEmpty();
    }

    @Test
    void invalidCopyDirPathGivesTheFilePath() throws IOException {
        Path source = shipment("{\"copyDirs\": [\"lib\\u0000x\"]}".getBytes());
        assertThat(CopybookTree.configCopyDirs(List.of(source.resolve("SHIPMENT.cpy"))))
            .isEmpty();
        assertThatThrownBy(() -> CopybookTree.run(
            settings(source, List.of(), BASE, false, false),
            List.of(source.resolve("SHIPMENT.cpy")), output, log))
            .isInstanceOf(CopybookTreeException.class);
        assertThat(log.errors).singleElement().asString()
            .startsWith(source.resolve("SHIPMENT.json") + ": a path in copyDirs is not valid: ");
    }

    @Test
    void invalidPackageInTheOptionsFileFails() throws IOException {
        Path source = shipment("{\"package\": \"com.my-co.shipping\"}".getBytes());
        assertThatThrownBy(() -> CopybookTree.run(
            settings(source, List.of(), BASE, false, false),
            List.of(source.resolve("SHIPMENT.cpy")), output, log))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessage("1 copybook(s) have errors. See the messages above.");
        assertThat(log.errors).containsExactly("SHIPMENT.cpy: the package 'com.my-co.shipping'"
            + " is not a valid Java package name");
    }

    @Test
    void emptyBasePackageFails() {
        Path source = TestCopybooks.copy("tree", work);
        assertThatThrownBy(() -> CopybookTree.run(
            settings(source, List.of(copylib), "", false, false),
            List.of(source.resolve("orders/ORDER.cpy")), output, log))
            .isInstanceOf(CopybookTreeException.class);
        assertThat(log.errors).containsExactly("orders/ORDER.cpy: the package '.orders'"
            + " is not a valid Java package name");
    }

    @Test
    void collisionFails() {
        assertThatThrownBy(() -> run("collision", List.of(copylib), BASE, false, false))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessageContaining("class com.example.mainframe.OrderLine comes from"
                + " CUSTOMER-ORDER.cpy and CUSTOMER-RETURN.cpy")
            .hasMessageContaining("packagePerCopybook = true");
        assertThat(output).isEmptyDirectory();
    }

    @Test
    void reportsAllCollisions() {
        assertThatThrownBy(() -> run("collision2", List.of(copylib), BASE, false, false))
            .isInstanceOf(CopybookTreeException.class)
            .hasMessageContaining("class com.example.mainframe.OrderLine comes from")
            .hasMessageContaining("class com.example.mainframe.Contact comes from");
    }

    @Test
    void perCopybookRemovesTheCollision() throws IOException {
        run("collision", List.of(copylib), BASE, false, true);
        assertThat(output.resolve("com/example/mainframe/customer_order/OrderLine.java")).exists();
        assertThat(output.resolve("com/example/mainframe/customer_return/OrderLine.java"))
            .exists();
    }

    @Test
    void jsonCopyDirsAreSearchedFirst() throws IOException {
        TestCopybooks.copy("jsoncopylib", work);
        run("jsoncopy", List.of(copylib), BASE, false, false);
        assertThat(output.resolve("com/example/mainframe/Account.java")).content()
            .contains("bankName");
    }

    @Test
    void configCopyDirsReadsTheOptionsFiles() {
        Path account = TestCopybooks.copy("jsoncopy", work);
        Path tree = TestCopybooks.copy("tree", work);
        Path badjson = TestCopybooks.copy("badjson", work);
        assertThat(CopybookTree.configCopyDirs(List.of(account.resolve("ACCOUNT.cpy"))))
            .containsExactly(account.resolve("../jsoncopylib"));
        assertThat(CopybookTree.configCopyDirs(List.of(tree.resolve("orders/ORDER.cpy"),
            badjson.resolve("SHIPMENT.cpy")))).isEmpty();
    }

    @Test
    void customMessagesAreUsed() {
        Path tree = TestCopybooks.copy("tree", work);
        Path collision = TestCopybooks.copy("collision", work);
        String missing = "Set the packageName parameter of bookmap-copybook-maven-plugin.";
        String hint = "<packagePerCopybook>true</packagePerCopybook>";
        assertThatThrownBy(() -> CopybookTree.run(new CopybookTree.Settings(tree, List.of(),
            null, false, false, missing, hint, null), List.of(tree.resolve("orders/ORDER.cpy")),
            output, log)).hasMessage(missing);
        assertThatThrownBy(() -> CopybookTree.run(new CopybookTree.Settings(collision,
            List.of(copylib), BASE, false, false, missing, hint, null),
            TestCopybooks.copybooks(collision), output, log))
            .hasMessageContaining("Set " + hint + ", set \"package\" in an options file");
    }

    @Test
    void relativePathBelowTheBase() {
        assertThat(CopybookTree.relativePath(work, work.resolve("lib/members")))
            .isEqualTo("lib/members");
        assertThat(CopybookTree.relativePath(work.resolve("a"), work.resolve("b")))
            .isEqualTo("../b");
    }

    @Test
    void relativePathWithAnotherRootIsAbsolute() throws IOException {
        try (java.nio.file.FileSystem windows = com.google.common.jimfs.Jimfs.newFileSystem(
            com.google.common.jimfs.Configuration.windows().toBuilder()
                .setRoots("C:\\", "D:\\").build())) {
            Path base = windows.getPath("C:\\work\\model\\src\\main\\copybook");
            Path lib = windows.getPath("D:\\copylib");
            assertThat(CopybookTree.relativePath(base, lib)).isEqualTo("D:/copylib");
            assertThat(CopybookTree.relativePath(base, windows.getPath("C:\\work\\lib")))
                .isEqualTo("../../../../lib");
        }
    }

    @Test
    void optionsFileName() {
        assertThat(CopybookTree.optionsFile(Path.of("a/ORDER.cpy")))
            .isEqualTo(Path.of("a/ORDER.json"));
        assertThat(CopybookTree.optionsFile(Path.of("ORDER")))
            .isEqualTo(Path.of("ORDER.json"));
    }

    private static final class RecordingLog implements CopybookTree.Log {
        final List<String> infos = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message) {
            infos.add(message);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }
    }
}
