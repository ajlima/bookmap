package pt.dazz.copymapper.copybook.model;

import java.util.List;
import pt.dazz.copymapper.annotation.Direction;

/**
 * Describes a generated {@code @Field} field.
 *
 * @param name the Java field name
 * @param javaType the Java type
 * @param order the position in the book
 * @param pic the {@code pic} value, for example {@code S9(7)V99 COMP-3}
 * @param direction the resolved direction
 * @param cobolName the COBOL data name
 * @param declaration the normalized COBOL declaration
 * @param pattern the date or time pattern of a temporal field, or {@code null}
 * @param enumType the simple name of the enum for {@link JavaType#ENUM}, else {@code null}
 * @param initialValue the initial value from the {@code VALUE} clause, or {@code null}. It is
 *     a text without spaces on the right, a plain decimal number, or an enum constant name.
 * @param conditions the constants and boolean methods of the level 88 items
 * @param redefines the place in a {@code REDEFINES} view area: {@code null} outside an area,
 *     an empty value for the redefined member, or the Java name of the redefined member for a
 *     view
 */
public record FieldMember(String name, JavaType javaType, int order, String pic,
                          Direction direction, String cobolName, String declaration,
                          String pattern, String enumType, String initialValue,
                          List<ConditionMember> conditions, String redefines)
    implements MemberModel {

    /**
     * Creates a field member.
     *
     * @param name the name
     * @param javaType the Java type
     * @param order the order
     * @param pic the pic
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param pattern the date or time pattern
     * @param enumType the enum name
     * @param initialValue the initial value
     * @param conditions the conditions
     * @param redefines the place in a view area
     */
    public FieldMember {
        conditions = List.copyOf(conditions);
    }

    /**
     * Creates a field outside a {@code REDEFINES} view area.
     *
     * @param name the name
     * @param javaType the Java type
     * @param order the order
     * @param pic the pic
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param pattern the date or time pattern
     * @param enumType the enum name
     * @param initialValue the initial value
     * @param conditions the conditions
     */
    public FieldMember(String name, JavaType javaType, int order, String pic,
                       Direction direction, String cobolName, String declaration,
                       String pattern, String enumType, String initialValue,
                       List<ConditionMember> conditions) {
        this(name, javaType, order, pic, direction, cobolName, declaration, pattern, enumType,
            initialValue, conditions, null);
    }

    /**
     * Gives a copy of this field as a member of a {@code REDEFINES} view area, without an
     * initial value.
     *
     * @param place an empty value for the redefined member, or the Java name of the redefined
     *     member
     * @return the copy
     */
    public FieldMember inViewArea(String place) {
        return new FieldMember(name, javaType, order, pic, direction, cobolName, declaration,
            pattern, enumType, null, conditions, place);
    }

    /**
     * Creates a field without a date or time pattern.
     *
     * @param name the name
     * @param javaType the Java type
     * @param order the order
     * @param pic the pic
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param enumType the enum name
     * @param initialValue the initial value
     * @param conditions the conditions
     */
    public FieldMember(String name, JavaType javaType, int order, String pic,
                       Direction direction, String cobolName, String declaration,
                       String enumType, String initialValue, List<ConditionMember> conditions) {
        this(name, javaType, order, pic, direction, cobolName, declaration, null, enumType,
            initialValue, conditions);
    }

    /**
     * Creates a field without an enum, an initial value, and conditions.
     *
     * @param name the name
     * @param javaType the Java type
     * @param order the order
     * @param pic the pic
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param pattern the date or time pattern
     */
    public FieldMember(String name, JavaType javaType, int order, String pic,
                       Direction direction, String cobolName, String declaration,
                       String pattern) {
        this(name, javaType, order, pic, direction, cobolName, declaration, pattern, null,
            null, List.of());
    }

    /**
     * Creates a field without a date or time pattern, an enum, an initial value, and
     * conditions.
     *
     * @param name the name
     * @param javaType the Java type
     * @param order the order
     * @param pic the pic
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     */
    public FieldMember(String name, JavaType javaType, int order, String pic,
                       Direction direction, String cobolName, String declaration) {
        this(name, javaType, order, pic, direction, cobolName, declaration, null);
    }
}
