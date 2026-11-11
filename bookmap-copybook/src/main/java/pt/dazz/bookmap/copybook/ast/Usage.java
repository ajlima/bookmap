package pt.dazz.bookmap.copybook.ast;

/**
 * Gives the storage format of a COBOL item that BookMap supports.
 */
public enum Usage {
    /** One byte for each character or digit. */
    DISPLAY,
    /** Binary ({@code COMP}, {@code COMP-4}, {@code BINARY}). */
    COMP,
    /** Packed decimal ({@code COMP-3}, {@code PACKED-DECIMAL}). */
    COMP3
}
