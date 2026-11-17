package pt.dazz.copymapper.copybook.spec;

import java.util.List;
import java.util.Objects;

/**
 * A condition name (level 88).
 *
 * @param name the name
 * @param values the values and ranges
 */
public record ConditionSpec(String name, List<ConditionRange> values) {

    /** Checks the name and copies the list. */
    public ConditionSpec {
        Objects.requireNonNull(name, "name");
        values = List.copyOf(values);
    }
}
