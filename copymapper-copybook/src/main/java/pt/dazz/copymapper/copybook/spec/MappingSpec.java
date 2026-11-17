package pt.dazz.copymapper.copybook.spec;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * The mapping spec of one copybook record.
 *
 * @param formatVersion the format version, always 1
 * @param sourceName the name of the source
 * @param sourceHash the hash of the source, or null
 * @param charset the name of a single-byte charset; the record keeps the canonical name
 * @param byteOrder the byte order, always BIG_ENDIAN
 * @param root the root node
 */
public record MappingSpec(int formatVersion, String sourceName, String sourceHash,
        String charset, String byteOrder, SpecNode root) {

    /** The only format version. */
    public static final int FORMAT_VERSION = 1;
    /** The only byte order. */
    public static final String BIG_ENDIAN = "BIG_ENDIAN";

    /** Checks the format version, the byte order, and the charset. */
    public MappingSpec {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(byteOrder, "byteOrder");
        Objects.requireNonNull(root, "root");
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported formatVersion " + formatVersion);
        }
        if (!BIG_ENDIAN.equals(byteOrder)) {
            throw new IllegalArgumentException("unsupported byteOrder " + byteOrder);
        }
        charset = canonicalSingleByte(charset);
    }

    private static String canonicalSingleByte(String name) {
        Charset found;
        float max;
        try {
            found = Charset.forName(name);
            max = found.newEncoder().maxBytesPerChar();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown charset " + name, e);
        } catch (UnsupportedOperationException e) {
            throw new IllegalArgumentException("charset " + name + " cannot encode", e);
        }
        if (max != 1.0f) {
            throw new IllegalArgumentException("charset " + name + " is not single-byte");
        }
        return found.name();
    }
}
