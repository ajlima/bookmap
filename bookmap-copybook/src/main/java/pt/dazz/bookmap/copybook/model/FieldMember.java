package pt.dazz.bookmap.copybook.model;

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
 */
public record FieldMember(String name, JavaType javaType, int order, String pic,
                          Direction direction, String cobolName, String declaration)
    implements MemberModel {
}
