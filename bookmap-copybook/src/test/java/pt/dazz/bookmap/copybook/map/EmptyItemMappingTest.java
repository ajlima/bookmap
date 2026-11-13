package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.copybook.map.Mapping.map;
import static pt.dazz.bookmap.copybook.map.Mapping.member;
import static pt.dazz.bookmap.copybook.map.Mapping.type;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.CollectionMember;

class EmptyItemMappingTest {

    private static MappingOptions options(Map<String, String> countFields,
                                          Map<String, String> emptyItems,
                                          Map<String, String> emptyKeys) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), countFields, null, Map.of(),
            Map.of(), Map.of(), Map.of(), emptyItems, emptyKeys);
    }

    private static MappingOptions options(Map<String, String> emptyItems,
                                          Map<String, String> emptyKeys) {
        return options(Map.of(), emptyItems, emptyKeys);
    }

    private static List<String> messages(MapResult result, Severity severity) {
        return result.diagnostics().stream().filter(d -> d.severity() == severity)
            .map(Diagnostic::message)
            .filter(m -> !m.matches("record \\S+ has (at most )?\\d+ bytes")).toList();
    }

    private static CollectionMember collection(MapResult result, String type, String name) {
        return (CollectionMember) member(type(result, type), name);
    }

    @Test
    void optionsSetTheFillAndTheKey() {
        MapResult result = map(options(Map.of("INVOICE-LINE", "SPACES"),
                Map.of("INVOICE-LINE", "INVOICE-NUMBER")),
            "01 LIST-INVOICES.", "05 INVOICE-LINE OCCURS 5 TIMES.",
            "10 INVOICE-NUMBER PIC X(10).", "10 AMOUNT-DUE PIC S9(7)V99 COMP-3.");
        assertThat(result.hasErrors()).isFalse();
        assertThat(collection(result, "ListInvoices", "invoiceLine")).isEqualTo(
            new CollectionMember("invoiceLine", 10, Direction.INPUT_OUTPUT, "INVOICE-LINE",
                "05 INVOICE-LINE OCCURS 5 TIMES.", 5, true, null, "InvoiceLine",
                EmptyItem.SPACES, "invoiceNumber"));
    }

    @Test
    void keyCanBeInAGroupOfTheEntryOrTheTableItself() {
        MapResult result = map(options(Map.of("LINE", "LOW-VALUES"),
                Map.of("LINE", "ID OF LINE", "CODES", "CODES")),
            "01 ORDER.", "05 LINE OCCURS 5.", "10 HEAD.", "15 ID PIC 9(6).",
            "10 QTY PIC 9(4) COMP.", "05 CODES OCCURS 3 PIC X(2).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(messages(result, Severity.INFO)).isEmpty();
        assertThat(collection(result, "Order", "line").emptyKey()).isEqualTo("id");
        assertThat(collection(result, "Order", "line").emptyItem())
            .isEqualTo(EmptyItem.LOW_VALUES);
        assertThat(collection(result, "Order", "codes").emptyKey()).isEqualTo("value");
    }

    @Test
    void emptyItemWorksWithACountField() {
        MapResult result = map(options(Map.of("LINE", "N"), Map.of("LINE", "SPACES"), Map.of()),
            "01 ORDER.", "05 N PIC 9(2).", "05 LINE OCCURS 5 PIC X.");
        assertThat(result.hasErrors()).isFalse();
        assertThat(collection(result, "Order", "line").dependsOn()).isEqualTo("n");
        assertThat(collection(result, "Order", "line").emptyItem()).isEqualTo(EmptyItem.SPACES);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "NONE|SPACES|empty item option: item NONE does not exist",
        "N|SPACES|empty item option: N has no OCCURS",
        "ODO|SPACES|empty item option: ODO has OCCURS DEPENDING ON. Remove the option: the"
            + " DEPENDING ON item gives the number of items.",
        "LINE|ZEROS|empty item option: LINE has the unknown value ZEROS. Use SPACES or"
            + " LOW-VALUES.",
    })
    void emptyItemErrors(String table, String value, String message) {
        MapResult result = map(options(Map.of(table, value), Map.of()), source());
        assertThat(messages(result, Severity.ERROR)).containsExactly(message);
        assertThat(result.model()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "NONE|ID|empty key option: item NONE does not exist",
        "LINE|NONE|empty key option: item NONE does not exist",
        "N|ID|empty key option: N has no OCCURS",
        "ODO|ID|empty key option: ODO has OCCURS DEPENDING ON. Remove the option: the"
            + " DEPENDING ON item gives the number of items.",
        "LINE|N|empty key option: N is not an elementary item of the entry of LINE",
        "LINE|ENTRY|empty key option: ENTRY is not an elementary item of the entry of LINE",
        "LINE|ALT-ID|empty key option: ALT-ID is not an elementary item of the entry of LINE",
        "LINE|SHIP-DATE|empty key option: SHIP-DATE must map to a text or a number field",
        "LINE|STATE|empty key option: STATE must map to a text or a number field",
    })
    void emptyKeyErrors(String table, String key, String message) {
        MapResult result = map(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of(), null,
                Map.of("SHIP-DATE", "yyyyMMdd"), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(table, key)), source());
        assertThat(messages(result, Severity.ERROR)).containsExactly(message);
        assertThat(result.model()).isNull();
    }

    private static String[] source() {
        return new String[] {"01 ORDER.", "05 N PIC 9(2).",
            "05 ODO OCCURS 1 TO 5 DEPENDING ON N PIC X.",
            "05 LINE OCCURS 4.", "10 ENTRY.", "15 ID PIC 9(6).",
            "15 ALT-ID REDEFINES ID PIC X(6).",
            "10 SHIP-DATE PIC 9(8).", "10 STATE PIC X.", "88 STATE-OPEN VALUE 'O'.",
            "88 STATE-CLOSED VALUE 'C'."};
    }

    @Test
    void emptyKeyIsNotPermittedWithACountField() {
        MapResult result = map(options(Map.of("LINE", "N"), Map.of(), Map.of("LINE", "LINE")),
            "01 ORDER.", "05 N PIC 9(2).", "05 LINE OCCURS 5 PIC X.");
        assertThat(messages(result, Severity.ERROR)).containsExactly(
            "empty key option: LINE has a count field. Remove one of the two options.");
    }

    @Test
    void tableInASkippedRedefinesIsAnError() {
        MapResult result = map(options(Map.of("CODES", "SPACES"), Map.of("CODES", "CODES")),
            "01 ORDER.", "05 TEXT PIC X(5).", "05 CODES REDEFINES TEXT OCCURS 5 PIC X.");
        assertThat(messages(result, Severity.ERROR)).containsExactly("empty item options:"
            + " CODES is not in a generated class, so the options have no effect");
    }

    @Test
    void compTableWithoutAKeyGetsTheInfo() {
        MapResult result = map(options(Map.of("QTY", "SPACES"), Map.of()),
            "01 ORDER.", "05 QTY OCCURS 5 PIC 9(4) COMP.");
        assertThat(messages(result, Severity.INFO)).containsExactly("QTY: a COMP value of 0 is"
            + " all LOW-VALUES and cannot be written. Link the count field with the option"
            + " countFields, or the key item with the option emptyKeys, if the program has"
            + " one.");
    }

    @Test
    void defaultsHaveNoMarkers() {
        assertThat(MappingOptions.defaults().emptyItems()).isEmpty();
        assertThat(MappingOptions.defaults().emptyKeys()).isEmpty();
        assertThat(MappingOptions.defaults().withHints(Map.of(), Map.of(), Map.of())
            .emptyKeys()).isEmpty();
    }
}
