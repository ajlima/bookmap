package pt.dazz.copymapper.copybook.spec;

import java.util.List;

/**
 * A condition name (level 88).
 *
 * @param name the name
 * @param values the values and ranges
 */
public record ConditionSpec(String name, List<ConditionRange> values) {

    /** Copies the list. */
    public ConditionSpec {
        values = List.copyOf(values);
    }
}
