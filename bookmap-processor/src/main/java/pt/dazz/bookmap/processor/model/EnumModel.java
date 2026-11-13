package pt.dazz.bookmap.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;

/**
 * Describes the COBOL values of an enum type, for one enum field. All values are checked.
 *
 * @param type the enum type
 * @param numeric {@code true} if the field has a numeric {@code pic}
 * @param constants the constants with values, in declaration order
 * @param unknown the name of the fallback constant, or {@code null}
 */
public record EnumModel(TypeElement type, boolean numeric, List<Constant> constants,
    String unknown) {

    /**
     * Creates an enum model.
     *
     * @param type the enum type
     * @param numeric the category
     * @param constants the constants
     * @param unknown the fallback constant
     */
    public EnumModel {
        constants = List.copyOf(constants);
    }

    /**
     * Describes one constant and its values. A text value has no spaces on the right. A
     * numeric value is a plain decimal number.
     *
     * @param name the constant name
     * @param values the discrete values, in declaration order
     * @param ranges the ranges, in declaration order
     */
    public record Constant(String name, List<String> values, List<Range> ranges) {

        /**
         * Creates a constant.
         *
         * @param name the name
         * @param values the values
         * @param ranges the ranges
         */
        public Constant {
            values = List.copyOf(values);
            ranges = List.copyOf(ranges);
        }

        /**
         * Gives the value that a write uses: the first discrete value, else the first value
         * of the first range.
         *
         * @return the canonical value
         */
        public String canonical() {
            return values.isEmpty() ? ranges.get(0).from() : values.get(0);
        }
    }

    /**
     * Describes one {@code THRU} range. Both bounds are in the range.
     *
     * @param from the first value
     * @param thru the last value
     */
    public record Range(String from, String thru) {
    }
}
