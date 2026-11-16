package pt.dazz.copymapper.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a Java field to a COBOL group without {@code OCCURS}, in a {@code REDEFINES} area. The
 * type of the field is a group class. A group class has the rules of an item class: it is
 * public, not generic, with a public no-argument constructor, at least one {@link BookField},
 * and only fixed collections. Its members have no direction.
 *
 * <p>A {@code REDEFINES} area is one redefined member and the members that name it in
 * {@code redefines} (the views). All members of the area start at the same byte. The area has
 * the length of its longest member. On read, the codec decodes each member of the area. A
 * member with bytes that are not valid for its type is {@code null}. On write, the codec writes
 * the first member in {@code order} that is not {@code null}, and fills the rest of the area
 * with spaces. A member at the top of an area must not have a primitive type.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface BookGroup {

    /**
     * Gives the position of the group in the book. Lower values come first.
     *
     * @return the order
     */
    int order();

    /**
     * Gives the message in which the group appears. Set it only on the redefined member of an
     * area in an operation class. A view uses the direction of the redefined member.
     *
     * @return the direction
     */
    Direction direction() default Direction.INPUT_OUTPUT;

    /**
     * Gives the Java field name of the member that this group redefines. See
     * {@link BookField#redefines()}.
     *
     * @return the field name, or an empty value for the redefined member of an area
     */
    String redefines() default "";
}
