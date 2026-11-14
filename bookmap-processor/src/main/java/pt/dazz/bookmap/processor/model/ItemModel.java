package pt.dazz.bookmap.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;

/**
 * Describes the layout of one entry of a collection.
 *
 * @param type the item class
 * @param members the members of the item, sorted by order: fields, and the collections of a
 *     nested collection. With subtypes, these are the members of the base class
 * @param byteLength the number of bytes of one entry. With subtypes, this is the length of the
 *     longest layout
 * @param subTypes the subtypes, or {@code null} if the class has no {@code @BookSubTypes}.
 *     The members of each variant are {@link FieldModel} instances
 */
public record ItemModel(TypeElement type, List<MemberModel> members, int byteLength,
    SubTypesModel subTypes) {
}
