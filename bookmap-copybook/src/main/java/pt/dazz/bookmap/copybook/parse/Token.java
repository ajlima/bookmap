package pt.dazz.bookmap.copybook.parse;

/**
 * Gives one token of a copybook.
 *
 * @param type the kind of token
 * @param text the text; upper case for a word
 * @param source the source name
 * @param line the line number, from 1
 * @param column the column number, from 1
 */
public record Token(TokenType type, String text, String source, int line, int column) {

    /**
     * Tells if this token is a word with the given text.
     *
     * @param word the word, in upper case
     * @return {@code true} if the token is this word
     */
    public boolean is(String word) {
        return type == TokenType.WORD && text.equals(word);
    }
}
