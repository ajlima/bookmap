package pt.dazz.bookmap.copybook.map;

import java.util.Collections;
import java.util.LinkedHashMap;
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
 * @param dates date hints: a date or time pattern, by COBOL name or by name pattern with
 *     {@code *}. The map keeps the order of the hints.
 * @param continuable the handler classes of {@code Continuable} records, by record name
 * @param validatable the handler classes of {@code Validatable} records, by record name
 * @param conditions the mode of the level 88 items, by COBOL name of an elementary item
 * @param emptyItems the bytes of an unused slot of a fixed table ({@code SPACES} or
 *     {@code LOW-VALUES}), by COBOL name of the table
 * @param emptyKeys the key item that marks an empty slot of a fixed table: COBOL name of the
 *     table to COBOL name of the key
 */
public record MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                             List<String> outputMarkers, Map<String, Direction> directions,
                             Map<String, SubTypeDecision> subTypes,
                             Map<String, String> countFields, Integer maxRecordLength,
                             Map<String, String> dates, Map<String, String> continuable,
                             Map<String, String> validatable,
                             Map<String, ConditionMode> conditions,
                             Map<String, String> emptyItems, Map<String, String> emptyKeys) {

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
     * @param dates the date hints
     * @param continuable the {@code Continuable} handlers
     * @param validatable the {@code Validatable} handlers
     * @param conditions the condition modes
     * @param emptyItems the bytes of the unused slots of fixed tables
     * @param emptyKeys the key items of fixed tables
     * @throws IllegalArgumentException if the maximum record length is less than 1
     */
    public MappingOptions {
        stripPrefixes = List.copyOf(stripPrefixes);
        inputMarkers = List.copyOf(inputMarkers);
        outputMarkers = List.copyOf(outputMarkers);
        directions = Map.copyOf(directions);
        subTypes = Map.copyOf(subTypes);
        countFields = Map.copyOf(countFields);
        dates = ordered(dates);
        continuable = ordered(continuable);
        validatable = ordered(validatable);
        conditions = Map.copyOf(conditions);
        emptyItems = Map.copyOf(emptyItems);
        emptyKeys = Map.copyOf(emptyKeys);
        if (maxRecordLength != null && maxRecordLength < 1) {
            throw new IllegalArgumentException("the maximum record length must be 1 or more,"
                + " not " + maxRecordLength);
        }
    }

    /**
     * Creates the options without empty-item markers.
     *
     * @param stripPrefixes the prefixes
     * @param inputMarkers the input markers
     * @param outputMarkers the output markers
     * @param directions the directions
     * @param subTypes the subtype decisions
     * @param countFields the count fields of fixed tables
     * @param maxRecordLength the maximum record length
     * @param dates the date hints
     * @param continuable the {@code Continuable} handlers
     * @param validatable the {@code Validatable} handlers
     * @param conditions the condition modes
     */
    public MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                          List<String> outputMarkers, Map<String, Direction> directions,
                          Map<String, SubTypeDecision> subTypes,
                          Map<String, String> countFields, Integer maxRecordLength,
                          Map<String, String> dates, Map<String, String> continuable,
                          Map<String, String> validatable,
                          Map<String, ConditionMode> conditions) {
        this(stripPrefixes, inputMarkers, outputMarkers, directions, subTypes, countFields,
            maxRecordLength, dates, continuable, validatable, conditions, Map.of(), Map.of());
    }

    /**
     * Creates the options without date hints and without {@code Continuable} and
     * {@code Validatable} handlers.
     *
     * @param stripPrefixes the prefixes
     * @param inputMarkers the input markers
     * @param outputMarkers the output markers
     * @param directions the directions
     * @param subTypes the subtype decisions
     * @param countFields the count fields of fixed tables
     * @param maxRecordLength the maximum record length
     * @param conditions the condition modes
     */
    public MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                          List<String> outputMarkers, Map<String, Direction> directions,
                          Map<String, SubTypeDecision> subTypes,
                          Map<String, String> countFields, Integer maxRecordLength,
                          Map<String, ConditionMode> conditions) {
        this(stripPrefixes, inputMarkers, outputMarkers, directions, subTypes, countFields,
            maxRecordLength, Map.of(), Map.of(), Map.of(), conditions);
    }

    /**
     * Creates the options without date hints, without {@code Continuable} and
     * {@code Validatable} handlers, and without condition modes.
     *
     * @param stripPrefixes the prefixes
     * @param inputMarkers the input markers
     * @param outputMarkers the output markers
     * @param directions the directions
     * @param subTypes the subtype decisions
     * @param countFields the count fields of fixed tables
     * @param maxRecordLength the maximum record length
     */
    public MappingOptions(List<String> stripPrefixes, List<String> inputMarkers,
                          List<String> outputMarkers, Map<String, Direction> directions,
                          Map<String, SubTypeDecision> subTypes,
                          Map<String, String> countFields, Integer maxRecordLength) {
        this(stripPrefixes, inputMarkers, outputMarkers, directions, subTypes, countFields,
            maxRecordLength, Map.of(), Map.of(), Map.of(), Map.of());
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

    /**
     * Gives a copy of these options with date hints and {@code Continuable} and
     * {@code Validatable} handlers.
     *
     * @param newDates the date hints
     * @param newContinuable the {@code Continuable} handlers
     * @param newValidatable the {@code Validatable} handlers
     * @return the new options
     */
    public MappingOptions withHints(Map<String, String> newDates,
                                    Map<String, String> newContinuable,
                                    Map<String, String> newValidatable) {
        return new MappingOptions(stripPrefixes, inputMarkers, outputMarkers, directions,
            subTypes, countFields, maxRecordLength, newDates, newContinuable, newValidatable,
            conditions, emptyItems, emptyKeys);
    }

    /** Copies a map and keeps its order, for deterministic diagnostics. */
    private static <V> Map<String, V> ordered(Map<String, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
