package pt.dazz.bookmap.copybook.model;

import java.util.List;

/**
 * Describes the constant and the boolean method that one level 88 item gives in the class of
 * its field.
 *
 * @param cobolName the name of the level 88 item
 * @param constant the name of the constant, which holds the first value
 * @param method the name of the boolean method, which tests all values
 * @param values the values and ranges, in source order
 */
public record ConditionMember(String cobolName, String constant, String method,
                              List<ValueRange> values) {

    /**
     * Creates a condition member.
     *
     * @param cobolName the COBOL name
     * @param constant the constant name
     * @param method the method name
     * @param values the values
     */
    public ConditionMember {
        values = List.copyOf(values);
    }
}
