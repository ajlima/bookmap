package pt.dazz.copymapper.copybook.model;

import java.util.Objects;
import pt.dazz.copymapper.annotation.Direction;
import pt.dazz.copymapper.annotation.EmptyItem;

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
 * @param emptyItem the bytes of an unused slot of a fixed table, never {@code null}
 * @param emptyKey the Java name of the key field that marks an empty slot, or {@code null}
 * @param partial {@code true} if a response can end on a slot boundary inside the table
 * @param redefines the place in a {@code REDEFINES} view area: {@code null} outside an area,
 *     an empty value for the redefined member, or the Java name of the redefined member for a
 *     view
 */
public record CollectionMember(String name, int order, Direction direction, String cobolName,
                               String declaration, int maxOccurs, boolean fixed,
                               String dependsOn, String itemClass, EmptyItem emptyItem,
                               String emptyKey, boolean partial, String redefines)
    implements MemberModel {

    /**
     * Creates a collection member.
     *
     * @param name the name
     * @param order the order
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param maxOccurs the maximum number of items
     * @param fixed {@code true} for a fixed table
     * @param dependsOn the count field
     * @param itemClass the item class
     * @param emptyItem the bytes of an unused slot
     * @param emptyKey the key field
     * @param partial {@code true} for a partial table
     * @param redefines the place in a view area
     * @throws NullPointerException if {@code emptyItem} is {@code null}
     */
    public CollectionMember {
        Objects.requireNonNull(emptyItem, "emptyItem");
    }

    /**
     * Creates a collection member outside a {@code REDEFINES} view area.
     *
     * @param name the name
     * @param order the order
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param maxOccurs the maximum number of items
     * @param fixed {@code true} for a fixed table
     * @param dependsOn the count field
     * @param itemClass the item class
     * @param emptyItem the bytes of an unused slot
     * @param emptyKey the key field
     * @param partial {@code true} for a partial table
     * @throws NullPointerException if {@code emptyItem} is {@code null}
     */
    public CollectionMember(String name, int order, Direction direction, String cobolName,
                            String declaration, int maxOccurs, boolean fixed, String dependsOn,
                            String itemClass, EmptyItem emptyItem, String emptyKey,
                            boolean partial) {
        this(name, order, direction, cobolName, declaration, maxOccurs, fixed, dependsOn,
            itemClass, emptyItem, emptyKey, partial, null);
    }

    /**
     * Creates a collection member that needs the full table in a response.
     *
     * @param name the name
     * @param order the order
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param maxOccurs the maximum number of items
     * @param fixed {@code true} for a fixed table
     * @param dependsOn the count field
     * @param itemClass the item class
     * @param emptyItem the bytes of an unused slot
     * @param emptyKey the key field
     * @throws NullPointerException if {@code emptyItem} is {@code null}
     */
    public CollectionMember(String name, int order, Direction direction, String cobolName,
                            String declaration, int maxOccurs, boolean fixed, String dependsOn,
                            String itemClass, EmptyItem emptyItem, String emptyKey) {
        this(name, order, direction, cobolName, declaration, maxOccurs, fixed, dependsOn,
            itemClass, emptyItem, emptyKey, false);
    }

    /**
     * Creates a collection member without empty-item markers: LOW-VALUES and no key.
     *
     * @param name the name
     * @param order the order
     * @param direction the direction
     * @param cobolName the COBOL name
     * @param declaration the declaration
     * @param maxOccurs the maximum number of items
     * @param fixed {@code true} for a fixed table
     * @param dependsOn the count field
     * @param itemClass the item class
     */
    public CollectionMember(String name, int order, Direction direction, String cobolName,
                            String declaration, int maxOccurs, boolean fixed, String dependsOn,
                            String itemClass) {
        this(name, order, direction, cobolName, declaration, maxOccurs, fixed, dependsOn,
            itemClass, EmptyItem.LOW_VALUES, null);
    }
}
