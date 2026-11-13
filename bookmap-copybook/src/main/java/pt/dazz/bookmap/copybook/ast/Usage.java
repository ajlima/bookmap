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
    COMP3,
    /**
     * Native binary ({@code COMP-5}). The bytes are the same as {@link #COMP}, but the item can
     * hold all values of its bytes.
     */
    COMP5
}
