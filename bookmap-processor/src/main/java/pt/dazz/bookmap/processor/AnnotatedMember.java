package pt.dazz.bookmap.processor;

import java.util.Map;
import javax.lang.model.element.VariableElement;
import pt.dazz.bookmap.processor.model.MemberModel;

/**
 * Gives one field with {@code @BookField} or {@code @BookCollection}, before the processor
 * resolves the conventions.
 *
 * @param field the field
 * @param collection {@code true} for {@code @BookCollection}
 * @param order the order
 * @param values the explicit attribute values, from {@link Mirrors#explicitValues}
 */
record AnnotatedMember(VariableElement field, boolean collection, int order,
                       Map<String, Object> values) {

    /**
     * Gives the simple name of the class that declares the field.
     *
     * @return the simple class name
     */
    String owner() {
        return field.getEnclosingElement().getSimpleName().toString();
    }

    /**
     * Gives the field name.
     *
     * @return the field name
     */
    String name() {
        return field.getSimpleName().toString();
    }

    /**
     * Gives the descriptor that starts each message about the member.
     *
     * @return the descriptor {@code "<owner>.<name> (order <n>)"}
     */
    String descriptor() {
        return MemberModel.descriptor(owner(), name(), order);
    }
}
