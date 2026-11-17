package pt.dazz.copymapper.copybook.spec;

/**
 * A place in a source file.
 *
 * @param source the source name
 * @param line the line, from 1
 * @param column the column, from 1
 */
public record SourceLocation(String source, int line, int column) {
}
