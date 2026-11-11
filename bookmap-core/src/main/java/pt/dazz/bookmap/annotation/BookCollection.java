package pt.dazz.bookmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a Java list field to a COBOL table (OCCURS).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface BookCollection {

    /**
     * Gives the position of the table in the book. Lower values come first.
     *
     * @return the order
     */
    int order();

    /**
     * Gives the message in which the table appears.
     *
     * @return the direction
     */
    Direction direction() default Direction.OUTPUT;

    /**
     * Gives the name of the field that holds the number of entries.
     * For a variable collection (OCCURS DEPENDING ON), an empty value means the member
     * immediately before the collection, in {@code order}. For a fixed collection, an empty
     * value means no count field: the unused slots contain LOW-VALUES.
     * The count field must be {@code int}, {@code Integer}, {@code long}, or {@code Long}.
     *
     * @return the field name
     */
    String dependsOn() default "";

    /**
     * Gives the maximum number of entries.
     *
     * @return the maximum number of entries
     */
    int maxOccurs();

    /**
     * Tells if the table is fixed (OCCURS n without DEPENDING ON). The area of a fixed table
     * always holds {@code maxOccurs} entries. The codec writes LOW-VALUES in the unused slots.
     * Without {@code dependsOn}, a read stops after the last slot that is not LOW-VALUES.
     *
     * @return {@code true} for a fixed table
     */
    boolean fixed() default false;
}
