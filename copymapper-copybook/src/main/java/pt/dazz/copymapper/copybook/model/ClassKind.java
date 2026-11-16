package pt.dazz.copymapper.copybook.model;

/**
 * Gives the role of a generated class.
 */
public enum ClassKind {
    /** A class with {@code @Operation}, from a record. */
    OPERATION,
    /** The item class of a collection. */
    ITEM,
    /** A subclass for one alternative of a {@code REDEFINES} area. */
    SUBTYPE,
    /** The class of a group in a {@code REDEFINES} view area ({@code @Group}). */
    GROUP
}
