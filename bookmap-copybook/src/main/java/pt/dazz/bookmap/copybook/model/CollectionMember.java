package pt.dazz.bookmap.copybook.model;

import pt.dazz.bookmap.annotation.Direction;

/**
 * Describes a generated {@code @BookCollection} field.
 *
 * @param name the Java field name
 * @param order the position in the book
 * @param direction the resolved direction
 * @param cobolName the COBOL data name
 * @param declaration the normalized COBOL declaration
 * @param maxOccurs the maximum number of items
 * @param fixed {@code true} for a fixed table
 * @param dependsOn the Java name of the count field, or {@code null}
 * @param itemClass the simple name of the item class
 */
public record CollectionMember(String name, int order, Direction direction, String cobolName,
                               String declaration, int maxOccurs, boolean fixed,
                               String dependsOn, String itemClass) implements MemberModel {
}
