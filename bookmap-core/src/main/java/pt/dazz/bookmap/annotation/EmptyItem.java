package pt.dazz.bookmap.annotation;

/**
 * Gives the bytes of an unused slot of a fixed collection. See
 * {@link BookCollection#emptyItem()}.
 */
public enum EmptyItem {
    /** An unused slot has only LOW-VALUES (bytes {@code 0x00}). */
    LOW_VALUES,
    /**
     * An unused slot has only spaces of the charset. A read also accepts LOW-VALUES in the slot.
     */
    SPACES
}
