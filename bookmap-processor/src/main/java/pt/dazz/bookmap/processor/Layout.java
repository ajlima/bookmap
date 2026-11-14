package pt.dazz.bookmap.processor;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.TypeElement;
import pt.dazz.bookmap.processor.model.MemberModel;
import pt.dazz.bookmap.processor.model.SubTypesModel;

/**
 * One layout of a codec or of an item class.
 *
 * @param type the class of the layout
 * @param value the normalized discriminator value, or null for a base class that is not in the
 *     list
 * @param members the members, sorted by order
 */
record Layout(TypeElement type, String value, List<MemberModel> members) {

    /**
     * Gives the layouts of an operation class or an item class. Without subtypes, the one
     * layout is the class itself. With
     * subtypes, a base class that is not abstract and not in the list comes first, then the
     * listed classes in the order of the annotation.
     */
    static List<Layout> of(TypeElement base, List<? extends MemberModel> members,
                           SubTypesModel subTypes) {
        List<Layout> result = new ArrayList<>();
        if (subTypes == null) {
            result.add(new Layout(base, null, List.copyOf(members)));
            return result;
        }
        boolean baseListed = subTypes.variants().stream()
            .anyMatch(variant -> variant.type().equals(base));
        if (!subTypes.baseAbstract() && !baseListed) {
            result.add(new Layout(base, null, List.copyOf(members)));
        }
        subTypes.variants().forEach(variant ->
            result.add(new Layout(variant.type(), variant.value(), variant.members())));
        return result;
    }
}
