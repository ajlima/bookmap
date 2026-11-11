package pt.dazz.bookmap.copybook.map;

import java.util.Map;

/**
 * Tells the mapper to map a {@code REDEFINES} area to subtypes.
 *
 * @param discriminator the COBOL name of the discriminator item
 * @param values the discriminator value of each alternative, by COBOL name
 * @param classNames optional class names, by COBOL name of the alternative
 */
public record SubTypeDecision(String discriminator, Map<String, String> values,
                              Map<String, String> classNames) {

    /**
     * Creates a decision.
     *
     * @param discriminator the discriminator
     * @param values the values
     * @param classNames the class names
     */
    public SubTypeDecision {
        values = Map.copyOf(values);
        classNames = Map.copyOf(classNames);
    }
}
