package pt.dazz.bookmap.copybook.ast;

/**
 * Describes an {@code OCCURS} clause.
 *
 * @param min the minimum number of entries; equal to {@code max} for a fixed table
 * @param max the maximum number of entries
 * @param dependingOn the count item, or {@code null} for a fixed table
 */
public record Occurs(int min, int max, QualifiedName dependingOn) {
}
