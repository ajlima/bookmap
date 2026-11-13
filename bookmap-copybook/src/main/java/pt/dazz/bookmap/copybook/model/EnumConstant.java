package pt.dazz.bookmap.copybook.model;

import java.util.List;

/**
 * Describes one constant of a generated enum: one level 88 item.
 *
 * @param name the Java constant name
 * @param cobolName the name of the level 88 item
 * @param values the values and ranges, in source order
 */
public record EnumConstant(String name, String cobolName, List<ValueRange> values) {

    /**
     * Creates a constant.
     *
     * @param name the name
     * @param cobolName the COBOL name
     * @param values the values
     */
    public EnumConstant {
        values = List.copyOf(values);
    }
}
