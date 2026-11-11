package pt.dazz.bookmap.processor.model;

import pt.dazz.bookmap.annotation.Direction;

/**
 * Describes one collection (COBOL table) of a book. All conventions are already resolved.
 *
 * @param ownerSimpleName the simple name of the class that declares the collection
 * @param name the Java field name
 * @param order the position in the book
 * @param direction the message in which the collection appears
 * @param countField the field that holds the number of entries
 * @param maxOccurs the maximum number of entries
 * @param item the layout of one entry
 * @param getter the name of the getter method
 * @param setter the name of the setter method
 */
public record CollectionModel(String ownerSimpleName, String name, int order,
    Direction direction, FieldModel countField, int maxOccurs, ItemModel item, String getter,
    String setter) implements MemberModel {
}
