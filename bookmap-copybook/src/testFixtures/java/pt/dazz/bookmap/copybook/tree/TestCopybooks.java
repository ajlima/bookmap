package pt.dazz.bookmap.copybook.tree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Copies the test copybooks to a work directory. The build plugin tests use this class. */
public final class TestCopybooks {

    private TestCopybooks() {
    }

    /** Copies the resource directory {@code copybook-trees/<name>} to {@code target/<name>}. */
    public static Path copy(String name, Path target) {
        try {
            URI uri = TestCopybooks.class.getResource("/copybook-trees/" + name).toURI();
            if (uri.getScheme().equals("jar")) {
                try {
                    FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    FileSystems.newFileSystem(uri, Map.of());
                }
            }
            Path source = Path.of(uri);
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
    public static List<Path> copybooks(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(f -> f.toString().endsWith(".cpy")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
