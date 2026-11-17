package pt.dazz.copymapper.copybook.spec;

/**
 * An OCCURS clause.
 *
 * @param min the minimum number of rows
 * @param max the maximum number of rows
 * @param dependingOn the path of the DEPENDING ON field, or null
 */
public record OccursSpec(int min, int max, String dependingOn) {
}
