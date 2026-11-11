package pt.dazz.bookmap.copybook.parse;

/**
 * Gives the kind of a token.
 */
public enum TokenType {
    /** A COBOL word, in upper case. */
    WORD,
    /** An integer or a decimal number, with an optional sign. */
    NUMBER,
    /** A text literal, without the quotes. */
    LITERAL,
    /** A picture string after {@code PIC} or {@code PICTURE}. */
    PICTURE,
    /** The period that ends an entry. */
    PERIOD,
    /** The text between {@code ==} delimiters, trimmed. */
    PSEUDO_TEXT
}
