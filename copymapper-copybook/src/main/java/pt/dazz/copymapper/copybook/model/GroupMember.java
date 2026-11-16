package pt.dazz.copymapper.copybook.model;

import pt.dazz.copymapper.annotation.Direction;

/**
 * Describes a generated {@code @BookGroup} field: a group in a {@code REDEFINES} view area.
 *
 * @param name the Java field name
 * @param order the position in the book
 * @param direction the resolved direction
 * @param cobolName the COBOL data name
 * @param declaration the normalized COBOL declaration
 * @param groupClass the simple name of the group class
 * @param redefines an empty value for the redefined member, or the Java name of the redefined
 *     member for a view
 */
public record GroupMember(String name, int order, Direction direction, String cobolName,
                          String declaration, String groupClass, String redefines)
    implements MemberModel {
}
