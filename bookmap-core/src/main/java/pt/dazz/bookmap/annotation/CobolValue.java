package pt.dazz.bookmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gives the COBOL values of one enum constant. An enum field without a converter uses these
 * values. The processor generates the conversion code at compile time.
 *
 * <p>On read, the field value selects the constant that contains it. On write, a constant
 * writes its canonical value: the first discrete value, else the {@code from} value of the
 * first range. A constant needs at least one value or one range.
 *
 * <p>A text value has no spaces on the right: the processor removes them, because a
 * {@code PIC X(n)} value has spaces on the right. A numeric value is a decimal number, for
 * example {@code "1"}, {@code "-5"}, or {@code "12.50"}. Two constants must not share a value.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface CobolValue {

    /**
     * Gives the discrete values. The first value is the canonical value.
     *
     * @return the values
     */
    String[] value() default {};

    /**
     * Gives the {@code THRU} ranges.
     *
     * @return the ranges
     */
    Range[] ranges() default {};

    /**
     * Gives one {@code THRU} range. Both bounds are in the range.
     */
    @Target({})
    @Retention(RetentionPolicy.CLASS)
    @interface Range {

        /**
         * Gives the first value of the range.
         *
         * @return the first value
         */
        String from();

        /**
         * Gives the last value of the range.
         *
         * @return the last value
         */
        String thru();
    }
}
