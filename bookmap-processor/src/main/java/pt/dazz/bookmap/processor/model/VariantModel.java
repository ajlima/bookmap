package pt.dazz.bookmap.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;

/**
 * Describes the layout of one subtype of a base class with {@code @BookSubTypes}.
 *
 * @param type the subtype, or the base class if the base class has its own value
 * @param value the normalized discriminator value: the text without spaces on the right, or
 *     the number as decimal digits without leading zeros
 * @param members the full layout of the subtype, base members first, sorted by order
 */
public record VariantModel(TypeElement type, String value, List<MemberModel> members) {
}
