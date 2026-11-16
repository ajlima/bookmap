package pt.dazz.bookmap.copybook.model;

import pt.dazz.bookmap.annotation.Direction;

/**
 * Describes one member of a generated class.
 */
public sealed interface MemberModel permits FieldMember, CollectionMember, GroupMember {

    /**
     * Gives the place of the member in a {@code REDEFINES} view area.
     *
     * @return {@code null} outside an area, an empty value for the redefined member, or the
     *     Java name of the redefined member for a view
     */
    String redefines();

    /**
     * Gives the Java field name.
     *
     * @return the name
     */
    String name();

    /**
     * Gives the position in the book.
     *
     * @return the order
     */
    int order();

    /**
     * Gives the resolved direction.
     *
     * @return the direction
     */
    Direction direction();

    /**
     * Gives the COBOL data name.
     *
     * @return the COBOL name
     */
    String cobolName();

    /**
     * Gives the normalized COBOL declaration.
     *
     * @return the declaration
     */
    String declaration();
}
