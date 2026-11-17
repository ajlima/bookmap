package pt.dazz.copymapper.copybook.spec;

import java.util.Objects;

/**
 * The options of {@link MappingSpecs#draft}.
 *
 * @param charset the name of the single-byte charset of the record
 */
public record DraftOptions(String charset) {

    /** The default charset. */
    public static final String DEFAULT_CHARSET = "IBM037";

    /** Checks the charset name. */
    public DraftOptions {
        Objects.requireNonNull(charset, "charset");
    }

    /**
     * Gives the default options.
     *
     * @return the options with the charset IBM037
     */
    public static DraftOptions defaults() {
        return new DraftOptions(DEFAULT_CHARSET);
    }
}
