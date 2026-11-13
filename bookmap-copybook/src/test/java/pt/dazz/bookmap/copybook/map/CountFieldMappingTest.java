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
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.CollectionMember;

class CountFieldMappingTest {

    private static MappingOptions countFields(Map<String, String> countFields) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), countFields);
    }

    /** Gives the diagnostics without the record length info of the map step. */
    private static List<Diagnostic> diagnostics(MapResult result) {
        return result.diagnostics().stream()
            .filter(d -> !d.message().matches("record \\S+ has (at most )?\\d+ bytes"))
            .toList();
    }

    private static List<String> messages(MapResult result, Severity severity) {
        return diagnostics(result).stream().filter(d -> d.severity() == severity)
            .map(Diagnostic::message).toList();
    }

    @Test
    void optionLinksTheCountFieldToTheFixedTable() {
        MapResult result = map(countFields(Map.of("INVOICE-LINE", "INVOICE-LINE-COUNT")),
            "01 GET-INVOICE.", "05 INVOICE-ID PIC X(12).", "05 INVOICE-LINE-COUNT PIC 9(3).",
            "05 INVOICE-LINE OCCURS 100 TIMES.", "10 PRODUCT-CODE PIC X(12).",
            "05 RETURN-CODE PIC X(4).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(diagnostics(result)).isEmpty();
        assertThat(member(type(result, "GetInvoice"), "invoiceLine")).isEqualTo(
            new CollectionMember("invoiceLine", 30, Direction.INPUT_OUTPUT, "INVOICE-LINE",
                "05 INVOICE-LINE OCCURS 100 TIMES.", 100, true, "invoiceLineCount",
                "InvoiceLine"));
    }

    @Test
    void optionAcceptsQualifiedNames() {
        MapResult result = map(countFields(Map.of("LINE OF ORDER-A", "N OF ORDER-A",
                "LINE OF ORDER-B", "N OF ORDER-B")),
            "01 ORDER-A.", "05 N PIC 9(2).", "05 LINE OCCURS 10 PIC X.",
            "01 ORDER-B.", "05 N PIC S9(4) COMP.", "05 LINE OCCURS 10 PIC X.");
        assertThat(result.hasErrors()).isFalse();
        assertThat(((CollectionMember) member(type(result, "OrderA"), "line")).dependsOn())
            .isEqualTo("n");
        assertThat(((CollectionMember) member(type(result, "OrderB"), "line")).dependsOn())
            .isEqualTo("n");
    }

    @Test
    void countFieldInAGroupIsAccepted() {
        MapResult result = map(countFields(Map.of("LINE", "LINE-COUNT")),
            "01 ORDER.", "05 HEADER.", "10 LINE-COUNT PIC 9(2).", "05 LINE OCCURS 10 PIC X.");
        assertThat(result.hasErrors()).isFalse();
        assertThat(((CollectionMember) member(type(result, "Order"), "line")).dependsOn())
            .isEqualTo("lineCount");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "LINE|NONE|count field option: item NONE does not exist",
        "NONE|N|count field option: item NONE does not exist",
        "LINE|A OF|count field option: 'A OF' is not a valid data name",
        "N|N|count field option: N has no OCCURS",
        "ODO|N|count field option: ODO has OCCURS DEPENDING ON. Remove the option: the"
            + " DEPENDING ON item is the count field.",
        "LINE|TOTALS|count field option: TOTALS must be an elementary numeric item without"
            + " decimals",
        "LINE|CODE|count field option: CODE must be an elementary numeric item without"
            + " decimals",
        "LINE|AMOUNT|count field option: AMOUNT must be an elementary numeric item without"
            + " decimals",
        "LINE|LATE|count field LATE must be in the same class, before the table",
        "LINE|HUGE|count field HUGE must be an integer",
        "LINE|SMALL|count field SMALL has 1 digits and cannot hold 20",
        "LINE|ITEM-N|count field ITEM-N must be in the same class, before the table",
    })
    void optionErrors(String table, String count, String message) {
        MapResult result = map(countFields(Map.of(table, count)),
            "01 ORDER.", "05 N PIC 9(2).", "05 CODE PIC X(2).", "05 AMOUNT PIC 9(3)V99.",
            "05 HUGE PIC 9(19).", "05 SMALL PIC 9.", "05 TOTALS.", "10 T1 PIC 9(2).",
            "05 ITEMS OCCURS 2.", "10 ITEM-N PIC 9(2).",
            "05 ODO OCCURS 1 TO 5 DEPENDING ON N PIC X.", "05 LINE OCCURS 20 PIC X.",
            "05 LATE PIC 9(2).");
        assertThat(messages(result, Severity.ERROR)).containsExactly(message);
        assertThat(result.model()).isNull();
    }

    @Test
    void nameErrorsHaveTheCopybookPosition() {
        MapResult result = map(countFields(Map.of("LINE", "NONE")),
            "01 ORDER.", "05 LINE OCCURS 2 PIC X.");
        assertThat(diagnostics(result)).extracting(Diagnostic::line, Diagnostic::column)
            .containsExactly(org.assertj.core.api.Assertions.tuple(1, 1));
    }

    @Test
    void oneCountFieldServesOnlyOneTable() {
        MapResult result = map(countFields(Map.of("LINE-A", "N", "LINE-B", "N")),
            "01 ORDER.", "05 N PIC 9(2).", "05 LINE-A OCCURS 5 PIC X.",
            "05 LINE-B OCCURS 5 PIC X.");
        assertThat(messages(result, Severity.ERROR))
            .containsExactly("count field option: N is already the count field of LINE-A");
    }

    @Test
    void tableInASkippedRedefinesIsAnError() {
        MapResult result = map(countFields(Map.of("CODES", "N")),
            "01 ORDER.", "05 N PIC 9(2).", "05 TEXT PIC X(5).",
            "05 CODES REDEFINES TEXT OCCURS 5 PIC X.");
        assertThat(messages(result, Severity.ERROR)).containsExactly("count field option:"
            + " CODES is not in a generated class, so the count field has no effect");
    }

    @Test
    void countItemBeforeAFixedTableGivesASuggestion() {
        MapResult result = map("01 GET-INVOICE.", "05 INVOICE-LINE-COUNT PIC 9(3).",
            "05 INVOICE-LINE OCCURS 100 TIMES.", "10 PRODUCT-CODE PIC X(12).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(((CollectionMember) member(type(result, "GetInvoice"), "invoiceLine"))
            .dependsOn()).isNull();
        assertThat(diagnostics(result)).singleElement().satisfies(d -> {
            assertThat(d.severity()).isEqualTo(Severity.INFO);
            assertThat(d.line()).isEqualTo(3);
            assertThat(d.message()).isEqualTo("INVOICE-LINE: INVOICE-LINE-COUNT can be the"
                + " count field of this fixed table. To link them, set \"countFields\":"
                + " {\"INVOICE-LINE\": \"INVOICE-LINE-COUNT\"} in the options file, or"
                + " --count-field INVOICE-LINE=INVOICE-LINE-COUNT.");
        });
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "05 LINE OCCURS 5 PIC X.",
        "05 LINE-COUNT PIC 9(2). 05 FILLER OCCURS 5 PIC X.",
        "05 LINE-COUNT. 10 N PIC 9(2). 05 LINE OCCURS 5 PIC X.",
        "05 LINE-TOTAL PIC 9(2). 05 LINE OCCURS 5 PIC X.",
        "05 LINE-COUNT PIC X(2). 05 LINE OCCURS 5 PIC X.",
        "05 LINE-COUNT PIC 9V9. 05 LINE OCCURS 5 PIC X.",
        "05 LINE-COUNT PIC 9(2). 05 LINE OCCURS 1 TO 5 DEPENDING ON LINE-COUNT PIC X.",
    })
    void noSuggestion(String items) {
        MapResult result = map("01 ORDER.", items);
        assertThat(result.hasErrors()).isFalse();
        assertThat(messages(result, Severity.INFO)).isEmpty();
    }

    @Test
    void linkedCompTableGetsNoLowValuesInfo() {
        MapResult result = map(countFields(Map.of("QTY", "QTY-COUNT")),
            "01 ORDER.", "05 QTY-COUNT PIC 9(2).", "05 QTY OCCURS 5 PIC 9(4) COMP.");
        assertThat(diagnostics(result)).isEmpty();
        assertThat(((CollectionMember) member(type(result, "Order"), "qty")).fixed()).isTrue();
    }

    @Test
    void linkedTableGetsNoSuggestion() {
        MapResult result = map(countFields(Map.of("LINE", "OTHER-COUNT")),
            "01 ORDER.", "05 OTHER-COUNT PIC 9(2).", "05 LINE-COUNT PIC 9(2).",
            "05 LINE OCCURS 5 PIC X.");
        assertThat(diagnostics(result)).isEmpty();
        assertThat(((CollectionMember) member(type(result, "Order"), "line")).dependsOn())
            .isEqualTo("otherCount");
    }

    @Test
    void defaultsHaveNoCountFields() {
        assertThat(MappingOptions.defaults().countFields()).isEmpty();
        assertThat(new MappingOptions(List.of(), List.of(), List.of(), Map.of(), Map.of())
            .countFields()).isEmpty();
    }
}
