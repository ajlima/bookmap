package pt.dazz.copymapper.processor;

import java.util.Map;
import javax.lang.model.element.VariableElement;
import pt.dazz.copymapper.processor.model.MemberModel;

/**
 * Gives one field with {@code @BookField}, {@code @BookCollection}, or {@code @BookGroup},
 * before the processor resolves the conventions.
 *
 * @param field the field
 * @param collection {@code true} for {@code @BookCollection}
 * @param group {@code true} for {@code @BookGroup}
 * @param order the order
 * @param values the explicit attribute values, from {@link Mirrors#explicitValues}
 */
record AnnotatedMember(VariableElement field, boolean collection, boolean group, int order,
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
     * Gives the name of the member that this member redefines.
     *
     * @return the {@code redefines} value, or an empty value for a member that is not a view
     */
    String redefines() {
        return (String) values.getOrDefault("redefines", "");
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
