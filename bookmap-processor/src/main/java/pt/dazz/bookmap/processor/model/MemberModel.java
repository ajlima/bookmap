package pt.dazz.bookmap.processor.model;

import pt.dazz.bookmap.annotation.Direction;

/**
 * Describes one member of a book: an elementary field or a collection.
 * All conventions are already resolved.
 */
public sealed interface MemberModel permits FieldModel, CollectionModel {

    /**
     * Gives the Java field name.
     *
     * @return the field name
     */
    String name();

    /**
     * Gives the position of the member in the book.
     *
     * @return the order
     */
    int order();

    /**
     * Gives the message in which the member appears.
     *
     * @return the direction
     */
    Direction direction();

    /**
     * Gives the simple name of the class that declares the member.
     *
     * @return the simple class name
     */
    String ownerSimpleName();

    /**
     * Gives the text that identifies the member in messages.
     *
     * @return the descriptor, in the format {@code "<ownerSimpleName>.<name> (order <n>)"}
     */
    default String descriptor() {
        return descriptor(ownerSimpleName(), name(), order());
    }

    /**
     * Gives the text that identifies a member in messages.
     *
     * @param ownerSimpleName the simple name of the declaring class
     * @param name the field name
     * @param order the order
     * @return the descriptor, in the format {@code "<ownerSimpleName>.<name> (order <n>)"}
     */
    static String descriptor(String ownerSimpleName, String name, int order) {
        return ownerSimpleName + "." + name + " (order " + order + ")";
    }
}
