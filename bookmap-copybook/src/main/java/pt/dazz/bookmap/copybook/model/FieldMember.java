package pt.dazz.bookmap.copybook.model;

import java.util.List;
import pt.dazz.bookmap.annotation.Direction;

/**
 * Describes a generated {@code @BookField} field.
 *
 * @param name the Java field name
 * @param javaType the Java type
 * @param order the position in the book
 * @param pic the {@code pic} value, for example {@code S9(7)V99 COMP-3}
 * @param direction the resolved direction
 * @param cobolName the COBOL data name
 * @param declaration the normalized COBOL declaration
 * @param enumType the simple name of the enum for {@link JavaType#ENUM}, else {@code null}
 * @param initialValue the initial value from the {@code VALUE} clause, or {@code null}. It is
 *     a text without spaces on the right, a plain decimal number, or an enum constant name.
 * @param conditions the constants and boolean methods of the level 88 items
 */
public record FieldMember(String name, JavaType javaType, int order, String pic,
                          Direction direction, String cobolName, String declaration,
                          String enumType, String initialValue,
                          List<ConditionMember> conditions)
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
     * @param enumType the enum name
     * @param initialValue the initial value
     * @param conditions the conditions
     */
    public FieldMember {
        conditions = List.copyOf(conditions);
    }

    /**
     * Creates a field member without an enum, an initial value, and conditions.
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
        this(name, javaType, order, pic, direction, cobolName, declaration, null, null,
            List.of());
    }
}
