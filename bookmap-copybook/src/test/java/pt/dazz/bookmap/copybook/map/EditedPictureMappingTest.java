package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pt.dazz.bookmap.copybook.map.Mapping.map;
import static pt.dazz.bookmap.copybook.map.Mapping.member;
import static pt.dazz.bookmap.copybook.map.Mapping.type;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;

/** Tests the mapping of items with an edited picture or with BLANK WHEN ZERO. */
class EditedPictureMappingTest {

    private static List<String> messages(MapResult result, Severity severity) {
        return result.diagnostics().stream().filter(d -> d.severity() == severity)
            .map(Diagnostic::message).toList();
    }

    private static FieldMember field(MapResult result, String name) {
        return (FieldMember) member(type(result, "Invoice"), name);
    }

    @Test
    void anEditedItemIsANumberWithItsPicture() {
        MapResult result = map("01 INVOICE.", "05 LINE-COUNT PIC ZZ9.",
            "05 UNITS PIC Z(9)9.", "05 TOTAL PIC $Z,ZZ9.99CR.", "05 DISCOUNT PIC 9(3)V99"
                + " BLANK WHEN ZERO.", "05 RATE PIC ZZ9 BLANK ZERO.");

        assertThat(result.hasErrors()).isFalse();
        assertThat(type(result, "Invoice").members())
            .extracting(m -> ((FieldMember) m).javaType(), m -> ((FieldMember) m).pic())
            .containsExactly(tuple(JavaType.INT, "ZZ9"), tuple(JavaType.LONG, "Z(9)9"),
                tuple(JavaType.BIG_DECIMAL, "$Z,ZZ9.99CR"),
                tuple(JavaType.BIG_DECIMAL, "9(3)V99 BLANK WHEN ZERO"),
                tuple(JavaType.INT, "ZZ9 BLANK WHEN ZERO"));
        // 3 + 10 + 11 + 5 + 3
        assertThat(messages(result, Severity.INFO)).containsExactly("record INVOICE has 32 bytes");
    }

    @Test
    void theSignOfAGroupDoesNotApplyToAnEditedItem() {
        MapResult result = map("01 INVOICE SIGN LEADING SEPARATE.", "05 TOTAL PIC -9(3).",
            "05 TAX PIC S9(3).");

        assertThat(field(result, "total").pic()).isEqualTo("-9(3)");
        assertThat(field(result, "tax").pic()).isEqualTo("S9(3) SIGN LEADING SEPARATE");
    }

    @Test
    void anEditedItemMustHaveUsageDisplay() {
        MapResult result = map("01 INVOICE.", "05 AMOUNTS COMP-3.", "10 TOTAL PIC ZZ9.99.",
            "05 COUNT PIC 9(3) COMP BLANK WHEN ZERO.");

        assertThat(messages(result, Severity.ERROR)).containsExactly(
            "TOTAL has an edited picture and must have usage DISPLAY",
            "COUNT has an edited picture and must have usage DISPLAY");
    }

    @Test
    void theConditionsAndTheValueOfAnEditedItemAreIgnored() {
        MapResult result = map("01 INVOICE.", "05 TOTAL PIC ZZ9 VALUE ' 0'.",
            "88 TOTAL-NONE VALUE '  0'.");

        assertThat(result.hasErrors()).isFalse();
        assertThat(field(result, "total").conditions()).isEmpty();
        assertThat(field(result, "total").initialValue()).isNull();
        assertThat(messages(result, Severity.INFO)).containsExactly(
            "record INVOICE has 3 bytes",
            "condition TOTAL-NONE is ignored, because TOTAL has an edited picture",
            "the VALUE of TOTAL is ignored, because TOTAL has an edited picture");
    }

    @Test
    void aDateHintOnAnEditedItemIsAnError() {
        MapResult result = map(MappingOptions.defaults().withHints(Map.of("*-DATE", "dd/MM/yy"),
                Map.of(), Map.of()),
            "01 INVOICE.", "05 INVOICE-DATE PIC 99/99/99.");

        assertThat(messages(result, Severity.ERROR)).containsExactly("INVOICE-DATE has an"
            + " edited picture. A date hint needs PIC 9(n) or PIC X(n).");
    }

    @Test
    void anEditedCountItemIsAnError() {
        MapResult result = map("01 INVOICE.", "05 LINE-COUNT PIC Z9.",
            "05 LINE OCCURS 1 TO 50 DEPENDING ON LINE-COUNT.", "10 SKU PIC X(8).");

        assertThat(messages(result, Severity.ERROR)).containsExactly(
            "DEPENDING ON item LINE-COUNT must not have an edited picture");
    }

    @Test
    void anEditedItemIsNotSuggestedAsACountField() {
        MapResult result = map("01 INVOICE.", "05 LINE-COUNT PIC Z9.",
            "05 LINE OCCURS 5.", "10 SKU PIC X(8).");

        assertThat(messages(result, Severity.INFO)).noneMatch(m -> m.contains("count field"));
    }

    private static MappingOptions options(Map<String, SubTypeDecision> subTypes,
                                          Map<String, String> countFields,
                                          Map<String, String> emptyKeys) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), subTypes, countFields, null, Map.of(),
            Map.of(), Map.of(), Map.of(), Map.of(), emptyKeys);
    }

    @Test
    void anEditedCountFieldOfTheOptionIsAnError() {
        MapResult result = map(options(Map.of(), Map.of("LINE", "LINE-COUNT"), Map.of()),
            "01 INVOICE.", "05 LINE-COUNT PIC Z9.", "05 LINE OCCURS 5.", "10 SKU PIC X(8).");

        assertThat(messages(result, Severity.ERROR)).containsExactly(
            "count field LINE-COUNT must not have an edited picture");
    }

    @Test
    void aCountItemWithBlankWhenZeroIsAnError() {
        MapResult result = map("01 INVOICE.", "05 LINE-COUNT PIC 9(2) BLANK WHEN ZERO.",
            "05 LINE OCCURS 1 TO 50 DEPENDING ON LINE-COUNT.", "10 SKU PIC X(8).");

        assertThat(messages(result, Severity.ERROR)).containsExactly(
            "DEPENDING ON item LINE-COUNT must not have an edited picture");
    }

    @Test
    void anEditedEmptyKeyIsAnError() {
        MapResult result = map(options(Map.of(), Map.of(), Map.of("LINE", "QUANTITY")),
            "01 INVOICE.", "05 LINE OCCURS 5.", "10 QUANTITY PIC ZZ9.", "10 SKU PIC X(8).");

        assertThat(messages(result, Severity.ERROR)).containsExactly(
            "empty key option: QUANTITY must not have an edited picture");
    }

    @Test
    void anEditedDiscriminatorIsAnError() {
        MapResult result = map(options(Map.of("CARD-DATA", new SubTypeDecision("ENTRY-TYPE",
                Map.of("CARD-DATA", "1", "BANK-DATA", "2"), Map.of())), Map.of(), Map.of()),
            "01 INVOICE.", "05 ENTRY-TYPE PIC Z9.", "05 CARD-DATA.", "10 CARD-NO PIC X(4).",
            "05 BANK-DATA REDEFINES CARD-DATA.", "10 IBAN PIC X(4).");

        assertThat(messages(result, Severity.ERROR)).containsExactly(
            "the discriminator ENTRY-TYPE must not have an edited picture");
    }

    @Test
    void aDateHintIgnoresBlankWhenZero() {
        MapResult result = map(MappingOptions.defaults().withHints(Map.of("*-DATE", "yyyyMMdd"),
                Map.of(), Map.of()),
            "01 INVOICE.", "05 DUE-DATE PIC 9(8) BLANK WHEN ZERO.");

        assertThat(result.hasErrors()).isFalse();
        assertThat(field(result, "dueDate").pic()).isEqualTo("9(8)");
        assertThat(field(result, "dueDate").javaType()).isEqualTo(JavaType.LOCAL_DATE);
        assertThat(messages(result, Severity.INFO)).contains(
            "BLANK WHEN ZERO on DUE-DATE is ignored, because DUE-DATE has a date hint");
    }

    @Test
    void theConditionsOfANumberWithBlankWhenZeroAreKept() {
        MapResult result = map("01 INVOICE.", "05 STATUS-CODE PIC 9 BLANK WHEN ZERO VALUE 1.",
            "88 STATUS-OPEN VALUE 1.", "88 STATUS-CLOSED VALUE 2.");

        assertThat(result.hasErrors()).isFalse();
        assertThat(field(result, "statusCode").pic()).isEqualTo("9 BLANK WHEN ZERO");
        assertThat(field(result, "statusCode").enumType()).isNotNull();
        assertThat(field(result, "statusCode").initialValue()).isNotNull();
    }
}
