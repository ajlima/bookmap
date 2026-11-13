package pt.dazz.bookmap.copybook.ast;

/**
 * Gives one value or one {@code THRU} range of a level 88 condition.
 *
 * @param from the value, or the first value of the range
 * @param thru the last value of the range, or {@code null} for one value
 */
public record ConditionValue(Literal from, Literal thru) {
}
