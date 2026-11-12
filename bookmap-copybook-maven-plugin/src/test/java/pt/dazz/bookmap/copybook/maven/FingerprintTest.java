package pt.dazz.bookmap.copybook.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FingerprintTest {

    @TempDir
    Path dir;

    private Path source;
    private Path lib;
    private Path other;
    private String version = "1.0.0";
    private List<String> parameters = List.of("com.example.mainframe", "false");
    private final List<Path> copybooks = new ArrayList<>();
    private final List<Path> options = new ArrayList<>();
    private final List<Path> copyDirs = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        source = Files.createDirectories(dir.resolve("copybook"));
        lib = Files.createDirectories(dir.resolve("liba"));
        other = Files.createDirectories(dir.resolve("libb"));
        copybooks.add(write(source.resolve("orders/ORDER.cpy"), "01 ORDER."));
        options.add(write(source.resolve("orders/ORDER.json"), "{}"));
        write(lib.resolve("ADDRESS.cpy"), "05 ADDRESS.");
        write(other.resolve("ADDRESS.cpy"), "05 OTHER.");
        Files.createDirectories(lib.resolve("nested"));
        copyDirs.add(lib);
        copyDirs.add(other);
    }

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private String hash() throws IOException {
        return Fingerprint.of(version, parameters, source, copybooks, options, copyDirs);
    }

    @Test
    void sameInputsGiveTheSameHash() throws IOException {
        assertThat(hash()).isEqualTo(hash()).hasSize(64);
    }

    @Test
    void copybookContent() throws IOException {
        String before = hash();
        write(source.resolve("orders/ORDER.cpy"), "01 ORDER-V2.");
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void copybookAdded() throws IOException {
        String before = hash();
        copybooks.add(write(source.resolve("INVOICE.cpy"), "01 INVOICE."));
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void copybookRemoved() throws IOException {
        String before = hash();
        copybooks.clear();
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void copybookMoved() throws IOException {
        String before = hash();
        copybooks.set(0, write(source.resolve("billing/ORDER.cpy"), "01 ORDER."));
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void optionsFile() throws IOException {
        String before = hash();
        write(source.resolve("orders/ORDER.json"), "{\"lombok\": true}");
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void copyDirMember() throws IOException {
        String before = hash();
        write(lib.resolve("ADDRESS.cpy"), "05 ADDRESS-V2.");
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void copyDirMemberAdded() throws IOException {
        String before = hash();
        write(other.resolve("CONTACT.cpy"), "05 CONTACT.");
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void copyDirOrder() throws IOException {
        String before = hash();
        copyDirs.clear();
        copyDirs.add(other);
        copyDirs.add(lib);
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void parameter() throws IOException {
        String before = hash();
        parameters = List.of("com.example.mainframe", "true");
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void pluginVersion() throws IOException {
        String before = hash();
        version = "1.0.1";
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void parameterBoundariesAreKept() throws IOException {
        parameters = List.of("ab", "c");
        String before = hash();
        parameters = List.of("a", "bc");
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void missingCopyDirIsPermitted() throws IOException {
        String before = hash();
        copyDirs.add(dir.resolve("missing"));
        assertThat(hash()).isNotEqualTo(before);
    }

    @Test
    void unknownAlgorithmFails() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Fingerprint.digest("NONE"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullParameterIsPermitted() throws IOException {
        parameters = new ArrayList<>(List.of("x"));
        parameters.add(null);
        assertThat(hash()).hasSize(64);
    }
}
