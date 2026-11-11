package pt.daaz.bookmap.convert;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Holds the settings that a codec needs for one conversion.
 * Only single-byte charsets are valid.
 *
 * @param charset the charset of the text fields
 */
public record CodecContext(Charset charset) {

    private static final String DEFAULT_CHARSET = "IBM037";

    /**
     * Creates the context and checks the charset.
     */
    public CodecContext {
        Objects.requireNonNull(charset, "charset");
        if (charset.newEncoder().maxBytesPerChar() > 1) {
            throw new IllegalArgumentException(
                "Only single-byte charsets are valid: " + charset.name());
        }
    }

    /**
     * Gives the default context. It uses the charset IBM037.
     *
     * @return the default context
     */
    public static CodecContext defaults() {
        return new CodecContext(Charset.forName(DEFAULT_CHARSET));
    }
}
