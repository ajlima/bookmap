package pt.dazz.bookmap.copybook.parse;

/**
 * Gives the reference format of a copybook.
 */
public enum SourceFormat {
    /** Columns 1-6: sequence area. Column 7: indicator. Columns 8-72: code. */
    FIXED,
    /** No columns. {@code *>} starts a comment. */
    FREE
}
