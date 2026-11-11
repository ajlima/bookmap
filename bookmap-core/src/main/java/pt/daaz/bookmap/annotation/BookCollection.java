package pt.daaz.bookmap.annotation;

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
     * Gives the name of the field that holds the number of entries (OCCURS DEPENDING ON).
     * An empty value means a fixed number of entries.
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
}
