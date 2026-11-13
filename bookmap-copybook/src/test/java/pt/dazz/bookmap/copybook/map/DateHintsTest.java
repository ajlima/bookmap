package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pt.dazz.bookmap.copybook.map.Mapping.map;
import static pt.dazz.bookmap.copybook.map.Mapping.member;
import static pt.dazz.bookmap.copybook.map.Mapping.type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;

class DateHintsTest {

    private static MappingOptions dates(String... keysAndPatterns) {
        Map<String, String> dates = new LinkedHashMap<>();
        for (int i = 0; i < keysAndPatterns.length; i += 2) {
            dates.put(keysAndPatterns[i], keysAndPatterns[i + 1]);
        }
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), dates);
    }

    private static List<String> errors(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR)
            .map(Diagnostic::message).toList();
    }

    private static FieldMember field(MapResult result, String className, String name) {
        return (FieldMember) member(type(result, className), name);
    }

    @Test
    void nameHintGivesDateField() {
        MapResult result = map(dates("ORDER-DATE", "yyyyMMdd"),
            "01 GET-ORDER.", "05 ORDER-ID PIC X(10).", "05 ORDER-DATE PIC 9(8).");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(field(result, "GetOrder", "orderDate")).isEqualTo(new FieldMember(
            "orderDate", JavaType.LOCAL_DATE, 20, "9(8)", Direction.INPUT_OUTPUT, "ORDER-DATE",
            "05 ORDER-DATE PIC 9(8).", "yyyyMMdd"));
        assertThat(field(result, "GetOrder", "orderId").pattern()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "9(8)  | yyyyMMdd       | LOCAL_DATE",
        "9(6)  | yyMMdd         | LOCAL_DATE",
        "9(7)  | yyyyDDD        | LOCAL_DATE",
        "X(10) | yyyy-MM-dd     | LOCAL_DATE",
        "9(6)  | HHmmss         | LOCAL_TIME",
        "X(8)  | HH:mm:ss       | LOCAL_TIME",
        "9(14) | yyyyMMddHHmmss | LOCAL_DATE_TIME",
    })
    void patternGivesJavaType(String pic, String pattern, JavaType type) {
        MapResult result = map(dates("STAMP", pattern), "01 GET-ORDER.",
            "05 STAMP PIC " + pic + ".");
        assertThat(errors(result)).isEmpty();
        FieldMember stamp = field(result, "GetOrder", "stamp");
        assertThat(stamp.javaType()).isEqualTo(type);
        assertThat(stamp.pattern()).isEqualTo(pattern);
    }

    @Test
    void namePatternMatchesFieldsInAllClasses() {
        MapResult result = map(dates("*-date", "yyyyMMdd"),
            "01 GET-ORDER.",
            "05 ORDER-DATE PIC 9(8).",
            "05 FILLER PIC 9(8).",
            "05 DATES.",
            "   10 SHIP-DATE PIC 9(8).",
            "05 LINES OCCURS 3.",
            "   10 DELIVERY-DATE PIC 9(8).",
            "05 PAYMENT-DATE PIC 9(8) OCCURS 2.",
            "05 UPDATE-TIME PIC 9(6).");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(type(result, "GetOrder").members())
            .filteredOn(m -> m instanceof FieldMember)
            .extracting(m -> m.name(), m -> ((FieldMember) m).javaType())
            .containsExactly(tuple("orderDate", JavaType.LOCAL_DATE),
                tuple("filler1", JavaType.INT), tuple("shipDate", JavaType.LOCAL_DATE),
                tuple("updateTime", JavaType.INT));
        assertThat(field(result, "Lines", "deliveryDate").javaType())
            .isEqualTo(JavaType.LOCAL_DATE);
        assertThat(field(result, "PaymentDate", "value").javaType())
            .isEqualTo(JavaType.LOCAL_DATE);
    }

    @Test
    void nameHintHasPriorityOverNamePattern() {
        MapResult result = map(dates("*-DATE", "yyyyMMdd", "SHORT-DATE", "yyMMdd"),
            "01 GET-ORDER.", "05 ORDER-DATE PIC 9(8).", "05 SHORT-DATE PIC 9(6).");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(field(result, "GetOrder", "shortDate").pattern()).isEqualTo("yyMMdd");
        assertThat(field(result, "GetOrder", "orderDate").pattern()).isEqualTo("yyyyMMdd");
    }

    @Test
    void namePatternsWithTheSamePatternDoNotConflict() {
        MapResult result = map(dates("*-DATE", "yyyyMMdd", "ORDER-*", "yyyyMMdd"),
            "01 GET-ORDER.", "05 ORDER-DATE PIC 9(8).");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void namePatternsWithDifferentPatternsConflict() {
        MapResult result = map(dates("*-DATE", "yyyyMMdd", "ORDER-*", "ddMMyyyy"),
            "01 GET-ORDER.", "05 ORDER-DATE PIC 9(8).");
        assertThat(errors(result)).containsExactly("ORDER-DATE matches the date hints *-DATE,"
            + " ORDER-* with different patterns. Add a date hint for ORDER-DATE.");
    }

    @Test
    void namePatternThatMatchesNoFieldIsAWarning() {
        MapResult result = map(dates("*-DTE", "yyyyMMdd", "*", "yyyyMMdd"),
            "01 GET-ORDER.", "05 FILLER PIC 9(8).", "05 HEADER.", "10 ORDER-DATE PIC 9(8).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.diagnostics()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(tuple(Severity.WARNING, 1,
                "date hint: the name pattern *-DTE matches no field"));
    }

    @Test
    void namePatternMustBeOneWord() {
        MapResult result = map(dates("*-DATE OF HEADER", "yyyyMMdd"),
            "01 GET-ORDER.", "05 ORDER-DATE PIC 9(8).");
        assertThat(errors(result)).containsExactly("date hint: the name pattern"
            + " '*-DATE OF HEADER' must be one word, without OF or IN");
    }

    @Test
    void qualifiedNameSelectsOneItem() {
        MapResult result = map(dates("CREATED OF SHIPPING", "yyyyMMdd"),
            "01 GET-ORDER.", "05 BILLING.", "10 CREATED PIC 9(8).", "05 SHIPPING.",
            "10 CREATED PIC 9(8).");
        assertThat(errors(result)).isEmpty();
        assertThat(type(result, "GetOrder").members())
            .extracting(m -> ((FieldMember) m).javaType())
            .containsExactly(JavaType.INT, JavaType.LOCAL_DATE);
    }

    @Test
    void nameErrors() {
        MapResult result = map(dates("NOPE", "yyyyMMdd", "CREATED", "yyyyMMdd", "BILLING",
                "yyyyMMdd", "A OF", "yyyyMMdd"),
            "01 GET-ORDER.", "05 BILLING.", "10 CREATED PIC 9(8).", "05 SHIPPING.",
            "10 CREATED PIC 9(8).");
        assertThat(result.diagnostics()).extracting(Diagnostic::line).containsOnly(1);
        assertThat(errors(result)).containsExactly(
            "date hint: item NOPE does not exist",
            "date hint: item CREATED is ambiguous. Qualify it: CREATED OF BILLING OF"
                + " GET-ORDER, CREATED OF SHIPPING OF GET-ORDER",
            "date hint: BILLING is a group. A date hint applies to an elementary item.",
            "date hint: 'A OF' is not a valid data name");
    }

    @Test
    void nameHintOfSkippedItemIsAnError() {
        MapResult result = map(dates("ORDER-TEXT", "yyyyMMdd"),
            "01 GET-ORDER.", "05 ORDER-DATE PIC 9(8).",
            "05 ORDER-TEXT REDEFINES ORDER-DATE PIC X(8).");
        assertThat(errors(result)).containsExactly(
            "date hint: ORDER-TEXT gives no field. The generator skips this item.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "9(8) COMP-3 | yyyyMMdd  | D: a date or time field must have the usage DISPLAY",
        "S9(8)       | yyyyMMdd  | D: a date or time field must not have S or V in the picture",
        "9(6)V99     | yyyyMMdd  | D: a date or time field must not have S or V in the picture",
        "X(10)       | d.M.yyyy  | D: the date pattern 'd.M.yyyy' gives text of variable length",
        "9(6)        | yyyyMMdd  | D: the date pattern 'yyyyMMdd' gives 8 characters, but the"
            + " picture 9(6) has 6",
        "9(4)        | MMdd      | D: the date pattern 'MMdd' does not fit LocalDateTime,"
            + " LocalDate, or LocalTime",
    })
    void patternMustFitTheItem(String pic, String pattern, String message) {
        MapResult result = map(dates("D", pattern), "01 GET-ORDER.", "05 D PIC " + pic + ".");
        assertThat(errors(result)).containsExactly(message);
    }

    @Test
    void invalidPatternIsAnError() {
        MapResult result = map(dates("D", "yyyy{MM"), "01 GET-ORDER.", "05 D PIC 9(6).");
        assertThat(errors(result)).singleElement().asString()
            .startsWith("D: the date pattern 'yyyy{MM' is not valid: ");
    }

    @Test
    void dateFieldIsNotADiscriminator() {
        MapResult result = map(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of("CARD-DATA",
                    new SubTypeDecision("KIND", Map.of("CARD-DATA", "20260101",
                        "LOAN-DATA", "20270101"), Map.of())), Map.of("KIND", "yyyyMMdd")),
            "01 GET-ACCOUNT.", "05 KIND PIC 9(8).", "05 CARD-DATA.", "10 LIMIT PIC 9(5).",
            "05 LOAN-DATA REDEFINES CARD-DATA.", "10 RATE PIC 9(5).");
        assertThat(errors(result)).containsExactly(
            "the discriminator KIND must be text or an integer");
    }
}
