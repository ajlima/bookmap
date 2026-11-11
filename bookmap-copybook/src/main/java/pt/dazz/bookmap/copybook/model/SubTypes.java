package pt.dazz.bookmap.copybook.model;

import java.util.List;

/**
 * Describes the subtypes of a base class.
 *
 * @param discriminator the Java name of the discriminator field
 * @param types the subclasses, in source order
 */
public record SubTypes(String discriminator, List<SubTypeEntry> types) {

    /**
     * Creates the subtypes.
     *
     * @param discriminator the discriminator
     * @param types the types
     */
    public SubTypes {
        types = List.copyOf(types);
    }
}
