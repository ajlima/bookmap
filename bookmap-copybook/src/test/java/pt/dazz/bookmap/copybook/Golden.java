package pt.dazz.bookmap.copybook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compares a text with a golden file in the test resources. On a difference, the test writes
 * the actual text to {@code build/golden-actual/} for a review.
 */
public final class Golden {

    private Golden() {
    }

    /**
     * Asserts that a text is equal to a golden file.
     *
     * @param resource the resource path, for example {@code json/model.json}
     * @param actual the actual text
     */
    public static void assertMatches(String resource, String actual) {
        String expected = read(resource);
        if (!actual.equals(expected)) {
            writeActual(resource, actual);
        }
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * Writes an actual text to {@code build/golden-actual/}.
     *
     * @param resource the resource path
     * @param actual the actual text
     */
    public static void writeActual(String resource, String actual) {
        try {
            Path file = Path.of("build", "golden-actual", resource);
            Files.createDirectories(file.getParent());
            Files.writeString(file, actual);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads a test resource as UTF-8 text.
     *
     * @param resource the resource path
     * @return the text, or an empty text if the resource does not exist
     */
    public static String read(String resource) {
        try (InputStream in = Golden.class.getResourceAsStream("/" + resource)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
