package pt.dazz.bookmap.copybook.ast;

import java.util.List;

/**
 * Describes a level 88 condition name.
 *
 * @param name the condition name
 * @param values the values and ranges, in source order
 * @param line the line of the level number
 * @param column the column of the level number
 */
public record Condition(String name, List<ConditionValue> values, int line, int column) {

    /**
     * Creates a condition.
     *
     * @param name the name
     * @param values the values
     * @param line the line
     * @param column the column
     */
    public Condition {
        values = List.copyOf(values);
    }
}
