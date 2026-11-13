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
 * @param countFields the count field of a fixed table: COBOL name of the table to COBOL name
 *     of the count field
 * @param maxRecordLength the maximum record length in bytes, or {@code null} for no check. A
 *     longer record gives a warning.
 */
public record MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                             List<String> outputMarkers, Map<String, Direction> directions,
                             Map<String, SubTypeDecision> subTypes,
                             Map<String, String> countFields, Integer maxRecordLength) {

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
     * @param countFields the count fields of fixed tables
     * @param maxRecordLength the maximum record length
     * @throws IllegalArgumentException if the maximum record length is less than 1
     */
    public MappingOptions {
        stripPrefixes = List.copyOf(stripPrefixes);
        inputMarkers = List.copyOf(inputMarkers);
        outputMarkers = List.copyOf(outputMarkers);
        directions = Map.copyOf(directions);
        subTypes = Map.copyOf(subTypes);
        countFields = Map.copyOf(countFields);
        if (maxRecordLength != null && maxRecordLength < 1) {
            throw new IllegalArgumentException("the maximum record length must be 1 or more,"
                + " not " + maxRecordLength);
        }
    }

    /**
     * Creates the options without a maximum record length.
     *
     * @param stripPrefixes the prefixes
     * @param inputMarkers the input markers
     * @param outputMarkers the output markers
     * @param directions the directions
     * @param subTypes the subtype decisions
     * @param countFields the count fields of fixed tables
     */
    public MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                          List<String> outputMarkers, Map<String, Direction> directions,
                          Map<String, SubTypeDecision> subTypes,
                          Map<String, String> countFields) {
        this(stripPrefixes, inputMarkers, outputMarkers, directions, subTypes, countFields,
            null);
    }

    /**
     * Creates the options without count fields.
     *
     * @param stripPrefixes the prefixes
     * @param inputMarkers the input markers
     * @param outputMarkers the output markers
     * @param directions the directions
     * @param subTypes the subtype decisions
     * @param maxRecordLength the maximum record length
     */
    public MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                          List<String> outputMarkers, Map<String, Direction> directions,
                          Map<String, SubTypeDecision> subTypes, Integer maxRecordLength) {
        this(stripPrefixes, inputMarkers, outputMarkers, directions, subTypes, Map.of(),
            maxRecordLength);
    }

    /**
     * Creates the options without count fields and without a maximum record length.
     *
     * @param stripPrefixes the prefixes
     * @param inputMarkers the input markers
     * @param outputMarkers the output markers
     * @param directions the directions
     * @param subTypes the subtype decisions
     */
    public MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                          List<String> outputMarkers, Map<String, Direction> directions,
                          Map<String, SubTypeDecision> subTypes) {
        this(stripPrefixes, inputMarkers, outputMarkers, directions, subTypes, Map.of(),
            null);
    }

    /**
     * Gives the default options: no prefixes, the default markers, no decisions, no count
     * fields, and no maximum record length.
     *
     * @return the default options
     */
    public static MappingOptions defaults() {
        return new MappingOptions(List.of(), INPUT_MARKERS, OUTPUT_MARKERS, Map.of(), Map.of());
    }
}
