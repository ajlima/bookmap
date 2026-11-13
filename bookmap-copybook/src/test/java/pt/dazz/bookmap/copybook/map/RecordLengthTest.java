package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.bookmap.copybook.map.Mapping.map;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;

class RecordLengthTest {

    private static final String[] INVOICE = {
        "01 GET-INVOICE.",
        "05 INVOICE-ID PIC X(10).",
        "05 CUSTOMER-NOTE OCCURS 2 TIMES PIC X(20).",
        "05 TAX-LINE OCCURS 5 TIMES.",
        "10 TAX-CODE PIC X(4).",
        "10 TAX-AMOUNT PIC S9(7)V99 COMP-3.",
        "05 INVOICE-LINE OCCURS 100 TIMES.",
        "10 PRODUCT-CODE PIC X(8).",
        "10 QUANTITY PIC 9(9).",
        "05 TOTALS COMP-3.",
        "10 SHIPPING OCCURS 4 TIMES PIC S9(5).",
        "10 GRAND-TOTAL PIC S9(11)V99."};

    private static MappingOptions max(Integer maxRecordLength) {
        MappingOptions defaults = MappingOptions.defaults();
        return new MappingOptions(defaults.stripPrefixes(), defaults.inputMarkers(),
            defaults.outputMarkers(), Map.of(), Map.of(), maxRecordLength);
    }

    private static List<String> messages(MapResult result, Severity severity) {
        return result.diagnostics().stream().filter(d -> d.severity() == severity)
            .map(Diagnostic::message).toList();
    }

    @Test
    void infoGivesTheRecordLength() {
        // 10 + 2 x 20 + 5 x (4 + 5) + 100 x (8 + 9) + 4 x 3 + 7 = 1814
        MapResult result = map(INVOICE);
        assertThat(messages(result, Severity.INFO))
            .containsExactly("record GET-INVOICE has 1814 bytes");
        assertThat(messages(result, Severity.WARNING)).isEmpty();
        Diagnostic info = result.diagnostics().get(0);
        assertThat(info.line()).isEqualTo(1);
        assertThat(info.column()).isEqualTo(1);
    }

    @Test
    void longerRecordGivesAWarningWithTheLargestTables() {
        MapResult result = map(max(1000), INVOICE);
        assertThat(messages(result, Severity.WARNING)).containsExactly("record GET-INVOICE"
            + " has 1814 bytes, more than the maximum record length of 1000 bytes. The largest"
            + " tables: INVOICE-LINE (100 x 17 = 1700 bytes), TAX-LINE (5 x 9 = 45 bytes),"
            + " CUSTOMER-NOTE (2 x 20 = 40 bytes).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.model()).isNotNull();
    }

    @Test
    void recordOfTheMaximumLengthGivesNoWarning() {
        assertThat(messages(map(max(1814), INVOICE), Severity.WARNING)).isEmpty();
        assertThat(messages(map(max(1813), INVOICE), Severity.WARNING)).hasSize(1);
    }

    @Test
    void recordWithoutTablesNamesNoTable() {
        MapResult result = map(max(5), "01 ORDER.", "05 ORDER-ID PIC X(10).");
        assertThat(messages(result, Severity.WARNING)).containsExactly("record ORDER has 10"
            + " bytes, more than the maximum record length of 5 bytes.");
    }

    @Test
    void variableTableGivesTheMaximumLength() {
        MapResult result = map(max(100), "01 LIST-ORDERS.", "05 ORDER-COUNT PIC 9(3).",
            "05 ORDER-ENTRY OCCURS 1 TO 20 TIMES DEPENDING ON ORDER-COUNT.",
            "10 ORDER-ID PIC X(10).");
        assertThat(messages(result, Severity.INFO))
            .containsExactly("record LIST-ORDERS has at most 203 bytes");
        assertThat(messages(result, Severity.WARNING)).containsExactly("record LIST-ORDERS"
            + " has at most 203 bytes, more than the maximum record length of 100 bytes. The"
            + " largest tables: ORDER-ENTRY (20 x 10 = 200 bytes).");
    }

    @Test
    void recordUsageAppliesToTheTables() {
        MapResult result = map(max(1), "01 TOTALS COMP-3.", "05 AMOUNT OCCURS 2 TIMES"
            + " PIC S9(5).");
        assertThat(messages(result, Severity.WARNING)).containsExactly("record TOTALS has 6"
            + " bytes, more than the maximum record length of 1 bytes. The largest tables:"
            + " AMOUNT (2 x 3 = 6 bytes).");
    }

    @Test
    void eachRecordHasItsOwnLength() {
        MapResult result = map("01 ORDER.", "05 ORDER-ID PIC X(10).", "01 INVOICE.",
            "05 INVOICE-ID PIC X(12).");
        assertThat(messages(result, Severity.INFO)).containsExactly(
            "record ORDER has 10 bytes", "record INVOICE has 12 bytes");
    }

    @Test
    void maximumRecordLengthMustBePositive() {
        assertThat(MappingOptions.defaults().maxRecordLength()).isNull();
        assertThatThrownBy(() -> max(0)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("the maximum record length must be 1 or more, not 0");
    }
}
