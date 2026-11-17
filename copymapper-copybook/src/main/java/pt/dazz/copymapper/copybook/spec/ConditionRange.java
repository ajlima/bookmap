package pt.dazz.copymapper.copybook.spec;

/**
 * A value or a range of a condition name (level 88).
 *
 * @param from the value, or the start of the range
 * @param thru the end of the range, or null
 */
public record ConditionRange(ValueSpec from, ValueSpec thru) {
}
