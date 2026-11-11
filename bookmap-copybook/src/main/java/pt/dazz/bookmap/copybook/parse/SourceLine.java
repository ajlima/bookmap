package pt.dazz.bookmap.copybook.parse;

/**
 * Gives the code area of one source line.
 *
 * @param source the source name
 * @param number the line number, from 1
 * @param column the column of the first character of {@code text}, from 1
 * @param text the code area
 * @param continuation {@code true} if the line continues the previous line
 */
public record SourceLine(String source, int number, int column, String text,
                         boolean continuation) {
}
