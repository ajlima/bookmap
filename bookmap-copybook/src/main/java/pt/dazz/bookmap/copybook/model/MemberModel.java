package pt.dazz.bookmap.copybook.model;

import pt.dazz.bookmap.annotation.Direction;

/**
 * Describes one member of a generated class.
 */
public sealed interface MemberModel permits FieldMember, CollectionMember {

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
