package pt.dazz.bookmap.copybook.map;

import java.util.List;
import java.util.Map;
import pt.dazz.bookmap.annotation.Direction;

/**
 * Gives the options of the map step.
 *
 * @param stripPrefixes the prefixes to remove from COBOL names
 * @param inputMarkers the name parts that mark an input alternative of a {@code REDEFINES} area
 * @param outputMarkers the name parts that mark an output alternative
 * @param directions explicit directions, by COBOL name of an alternative group
 * @param subTypes subtype decisions, by COBOL name of an alternative group
 */
public record MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                             List<String> outputMarkers, Map<String, Direction> directions,
                             Map<String, SubTypeDecision> subTypes) {

    /** The default input markers. */
    public static final List<String> INPUT_MARKERS = List.of("INPUT", "IN", "REQUEST", "REQ");
    /** The default output markers. */
    public static final List<String> OUTPUT_MARKERS =
        List.of("OUTPUT", "OUT", "RESPONSE", "RESP");

    /**
     * Creates the options.
     *
     * @param stripPrefixes the prefixes
     * @param inputMarkers the input markers
     * @param outputMarkers the output markers
     * @param directions the directions
     * @param subTypes the subtype decisions
     */
    public MappingOptions {
        stripPrefixes = List.copyOf(stripPrefixes);
        inputMarkers = List.copyOf(inputMarkers);
        outputMarkers = List.copyOf(outputMarkers);
        directions = Map.copyOf(directions);
        subTypes = Map.copyOf(subTypes);
    }

    /**
     * Gives the default options: no prefixes, the default markers, and no decisions.
     *
     * @return the default options
     */
    public static MappingOptions defaults() {
        return new MappingOptions(List.of(), INPUT_MARKERS, OUTPUT_MARKERS, Map.of(), Map.of());
    }
}
