package pt.dazz.bookmap.processor.model;

import java.util.List;

/**
 * Describes the subtypes of a base class with {@code @BookSubTypes}.
 *
 * @param discriminator the discriminator field of the base class
 * @param variants the layouts, in the order of the annotation
 * @param baseAbstract {@code true} if the base class is abstract
 */
public record SubTypesModel(FieldModel discriminator, List<VariantModel> variants,
    boolean baseAbstract) {
}
