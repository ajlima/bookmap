package pt.dazz.bookmap.copybook.model;

/**
 * Gives one value or one {@code THRU} range of a level 88 item, as a value of the field. A
 * text value has no spaces on the right. A numeric value is a plain decimal number.
 *
 * @param from the value, or the first value of the range
 * @param thru the last value of the range, or {@code null} for one value
 */
public record ValueRange(String from, String thru) {
}
