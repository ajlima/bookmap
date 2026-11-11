package pt.daaz.bookmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import pt.daaz.bookmap.convert.FieldConverter;
import pt.daaz.bookmap.convert.NoConverter;

/**
 * Maps a Java field to one elementary item of a book.
 * The processor treats an attribute as set only when the source code sets it explicitly.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface BookField {

    /**
     * Gives the position of the field in the book. Lower values come first.
     *
     * @return the order
     */
    int order();

    /**
     * Gives the COBOL PIC clause, for example {@code X(10)} or {@code S9(7)V99}.
     *
     * @return the PIC clause
     */
    String pic() default "";

    /**
     * Gives the length in bytes or digits when no PIC clause is present.
     *
     * @return the length
     */
    int length() default 0;

    /**
     * Gives the message in which the field appears.
     *
     * @return the direction
     */
    Direction direction() default Direction.INPUT_OUTPUT;

    /**
     * Gives the COBOL storage format of a numeric field.
     *
     * @return the usage
     */
    Usage usage() default Usage.DISPLAY;

    /**
     * Gives the number of decimal places when no PIC clause is present.
     *
     * @return the scale
     */
    int scale() default 0;

    /**
     * Tells if a numeric field has a sign when no PIC clause is present.
     *
     * @return true for a signed field
     */
    boolean signed() default false;

    /**
     * Gives the date or time pattern for date and time fields.
     *
     * <p>The pattern uses {@link java.time.format.DateTimeFormatter} letters. The formatter is
     * strict and uses {@link java.util.Locale#ROOT}. A two-digit year ({@code yy}) reads as a
     * year from 2000 to 2099. On write, a value that the pattern cannot keep causes a
     * {@link pt.daaz.bookmap.exception.MappingException}. Examples: the year 1975 with
     * {@code yyMMdd}, or a time with seconds or nanoseconds with {@code HHmm}. Remove the
     * extra precision before you set the value, for example with
     * {@code LocalTime.truncatedTo(ChronoUnit.MINUTES)}.
     *
     * @return the pattern
     */
    String pattern() default "yyyyMMdd";

    /**
     * Gives a custom converter for the field.
     *
     * @return the converter class
     */
    Class<? extends FieldConverter> converter() default NoConverter.class;
}
