package pt.dazz.bookmap.copybook.ast;

/**
 * Gives one literal of a {@code VALUE} clause.
 *
 * @param kind the kind of literal
 * @param text the characters between the quotes, the number as in the source, or the
 *     figurative constant in the singular form, for example {@code SPACE}
 */
public record Literal(Kind kind, String text) {

    /**
     * Gives the kind of a literal.
     */
    public enum Kind {
        /** A text literal, for example {@code 'SELLER'}. */
        TEXT,
        /** A numeric literal, for example {@code -12.50}. */
        NUMBER,
        /**
         * A figurative constant: {@code SPACE}, {@code ZERO}, {@code HIGH-VALUE},
         * {@code LOW-VALUE}, or {@code QUOTE}.
         */
        FIGURATIVE
    }
}
