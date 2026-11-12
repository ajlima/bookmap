package pt.dazz.bookmap.copybook.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Copies the test copybooks to a work directory. */
final class TestCopybooks {

    private TestCopybooks() {
    }

    /** Copies the resource directory {@code copybooks/<name>} to {@code target/<name>}. */
    static Path copy(String name, Path target) {
        try {
            Path source = Path.of(TestCopybooks.class.getResource("/copybooks/" + name).toURI());
            Path destination = target.resolve(name);
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.toList()) {
                    Path to = destination.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(to);
                    } else {
                        Files.copy(file, to);
                    }
                }
            }
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Gives all {@code .cpy} files below a directory, sorted. */
    static List<Path> copybooks(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(f -> f.toString().endsWith(".cpy")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
