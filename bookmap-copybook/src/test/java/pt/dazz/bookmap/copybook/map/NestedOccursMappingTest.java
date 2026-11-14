package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.copybook.map.Mapping.map;
import static pt.dazz.bookmap.copybook.map.Mapping.member;
import static pt.dazz.bookmap.copybook.map.Mapping.type;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.CollectionMember;

/** Tests an {@code OCCURS} in the entry of another {@code OCCURS}. */
class NestedOccursMappingTest {

    private static final String[] GET_ORDER = {
        "01 GET-ORDER.",
        "   05 ORDER-ID PIC X(10).",
        "   05 ORDER-LINE OCCURS 10.",
        "      10 PRODUCT-CODE PIC X(8).",
        "      10 TAX-COUNT PIC 9.",
        "      10 LINE-TAX OCCURS 3.",
        "         15 TAX-CODE PIC X(4).",
        "         15 TAX-AMOUNT PIC S9(7)V99 COMP-3.",
        "   05 ORDER-TOTAL PIC S9(9)V99 COMP-3.",
    };

    private static MappingOptions options(Map<String, String> countFields,
                                          Map<String, String> continuable,
                                          Map<String, String> emptyItems,
                                          Map<String, String> emptyKeys) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), countFields, null, Map.of(),
            continuable, Map.of(), Map.of(), emptyItems, emptyKeys);
    }

    private static MappingOptions countFields(Map<String, String> countFields) {
        return options(countFields, Map.of(), Map.of(), Map.of());
    }

    private static List<String> messages(MapResult result, Severity severity) {
        return result.diagnostics().stream().filter(d -> d.severity() == severity)
            .map(Diagnostic::message)
            .filter(m -> !m.matches("record \\S+ has (at most )?\\d+ bytes")).toList();
    }

    private static CollectionMember lineTax(MapResult result) {
        return (CollectionMember) member(type(result, "OrderLine"), "lineTax");
    }

    @Test
    void fixedOccursInOccursMapsToAnInnerCollection() {
        MapResult result = map(GET_ORDER);
        assertThat(result.hasErrors()).isFalse();
        assertThat(lineTax(result)).isEqualTo(new CollectionMember("lineTax", 30,
            Direction.INPUT_OUTPUT, "LINE-TAX", "10 LINE-TAX OCCURS 3.", 3, true, null,
            "LineTax"));
        assertThat(type(result, "LineTax").members()).extracting(m -> m.name())
            .containsExactly("taxCode", "taxAmount");
        assertThat(result.diagnostics()).extracting(Diagnostic::message)
            .contains("record GET-ORDER has " + (10 + 10 * (8 + 1 + 3 * (4 + 5)) + 6)
                + " bytes");
    }

    @Test
    void occursDependingOnInOccursIsAnError() {
        MapResult result = map("01 GET-ORDER.", "05 TAX-COUNT PIC 9.", "05 ORDER-LINE OCCURS 10.",
            "10 PRODUCT-CODE PIC X(8).", "10 LINE-TAX OCCURS 0 TO 3 DEPENDING ON TAX-COUNT.",
            "15 TAX-CODE PIC X(4).");
        assertThat(messages(result, Severity.ERROR)).containsExactly("OCCURS LINE-TAX DEPENDING"
            + " ON TAX-COUNT is inside OCCURS ORDER-LINE. BookMap does not support a DEPENDING ON"
            + " table in a table entry (complex OCCURS DEPENDING ON).");
    }

    @Test
    void countFieldOptionLinksACountInTheEntry() {
        MapResult result = map(countFields(Map.of("LINE-TAX OF ORDER-LINE", "TAX-COUNT")),
            GET_ORDER);
        assertThat(result.hasErrors()).isFalse();
        assertThat(lineTax(result).dependsOn()).isEqualTo("taxCount");
    }

    @Test
    void countFieldOutsideTheEntryIsAnError() {
        MapResult result = map(countFields(Map.of("LINE-TAX", "N")), "01 GET-ORDER.",
            "05 N PIC 9.", "05 ORDER-LINE OCCURS 10.", "10 LINE-TAX OCCURS 3.",
            "15 TAX-CODE PIC X(4).");
        assertThat(messages(result, Severity.ERROR))
            .containsExactly("count field N must be in the same class, before the table");
    }

    @Test
    void emptyItemOptionsApplyToAnInnerTable() {
        MapResult result = map(options(Map.of(), Map.of(), Map.of("LINE-TAX", "SPACES"),
            Map.of("LINE-TAX", "TAX-CODE")), GET_ORDER);
        assertThat(result.hasErrors()).isFalse();
        assertThat(lineTax(result).emptyItem()).isEqualTo(EmptyItem.SPACES);
        assertThat(lineTax(result).emptyKey()).isEqualTo("taxCode");
    }

    @Test
    void outerKeyInTheInnerEntryIsAnError() {
        MapResult result = map(options(Map.of(), Map.of(), Map.of(),
            Map.of("ORDER-LINE", "TAX-CODE")), GET_ORDER);
        assertThat(messages(result, Severity.ERROR)).containsExactly("empty key option: TAX-CODE"
            + " is not an elementary item of the entry of ORDER-LINE");
    }

    @Test
    void innerTableInAContinuableRecordIsNotAnError() {
        MapResult result = map(options(Map.of(), Map.of("LIST-ORDERS", "OrderPaging"), Map.of(),
                Map.of()),
            "01 LIST-ORDERS.", "05 MORE-DATA PIC X.", "05 ORDER-RESPONSE.",
            "10 ORDER-LINE OCCURS 3.", "15 PRODUCT-CODE PIC X(8).", "15 LINE-TAX OCCURS 2.",
            "20 TAX-CODE PIC X(4).");
        // Only the top-level table is an INPUT_OUTPUT collection.
        assertThat(messages(result, Severity.ERROR)).containsExactly("ORDER-LINE is an"
            + " INPUT_OUTPUT collection. BookMap does not permit it in a Continuable class. Put"
            + " the table in the output alternative of a REDEFINES area.");
    }

    @Test
    void compInfoIgnoresTheInnerCollection() {
        MapResult result = map("01 GET-ORDER.", "05 ORDER-LINE OCCURS 2.",
            "10 QTY PIC 9(4) COMP.", "10 LINE-TAX OCCURS 2.", "15 TAX-CODE PIC X(4).");
        assertThat(messages(result, Severity.INFO)).containsExactly("ORDER-LINE: a COMP value of"
            + " 0 is all LOW-VALUES and cannot be written. Link the count field with the option"
            + " countFields, or the key item with the option emptyKeys, if the program has one.");
    }

    @Test
    void countSuggestionAppliesToAnInnerTable() {
        MapResult result = map(GET_ORDER);
        assertThat(messages(result, Severity.INFO)).containsExactly("LINE-TAX: TAX-COUNT can be"
            + " the count field of this fixed table. To link them, set \"countFields\":"
            + " {\"LINE-TAX\": \"TAX-COUNT\"} in the options file, or --count-field"
            + " LINE-TAX=TAX-COUNT.");
    }
}
