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
     * value means no count field: {@link #emptyKey()} or {@link #emptyItem()} marks the empty
     * slots.
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
     * always holds {@code maxOccurs} entries. The codec writes the bytes of
     * {@link #emptyItem()} in the unused slots. Without {@code dependsOn}, a read stops after
     * the last slot that is not empty.
     *
     * @return {@code true} for a fixed table
     */
    boolean fixed() default false;

    /**
     * Gives the bytes of an unused slot of a fixed table. Without {@link #emptyKey()} and
     * without {@code dependsOn}, these bytes also mark an empty slot on read:
     * {@link EmptyItem#LOW_VALUES} needs only {@code 0x00} bytes, and {@link EmptyItem#SPACES}
     * needs only spaces or {@code 0x00} bytes. Use this attribute only with
     * {@code fixed = true}.
     *
     * @return the bytes of an unused slot
     */
    EmptyItem emptyItem() default EmptyItem.LOW_VALUES;

    /**
     * Gives the name of the key field of a fixed table. The key is a field of the item class
     * (with subtypes, of the base class). A slot is empty if its key is blank or zero. The
     * other bytes of the slot have no effect. In an unused slot, a write puts the bytes of
     * {@link #emptyItem()}, and the value zero in a number key. The key must be
     * {@code String}, {@code int}, {@code Integer}, {@code long}, {@code Long}, or
     * {@code BigDecimal}, without a converter. Use this attribute only with
     * {@code fixed = true} and without {@code dependsOn}.
     *
     * @return the field name, or an empty value for no key
     */
    String emptyKey() default "";
}
