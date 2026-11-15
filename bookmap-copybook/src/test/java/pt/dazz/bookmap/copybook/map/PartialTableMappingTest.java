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

class PartialTableMappingTest {

    private static MappingOptions options(Map<String, SubTypeDecision> subTypes,
                                          Map<String, String> countFields,
                                          List<String> partialTables) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), subTypes, countFields, null, Map.of(),
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), partialTables);
    }

    private static MappingOptions options(String... partialTables) {
        return options(Map.of(), Map.of(), List.of(partialTables));
    }

    private static List<String> errors(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR)
            .map(Diagnostic::message).toList();
    }

    private static CollectionMember collection(MapResult result, String type, String name) {
        return (CollectionMember) member(type(result, type), name);
    }

    @Test
    void theOptionMarksATableAtTheEndOfTheRecord() {
        MapResult result = map(options(Map.of(), Map.of("OPEN-ORDER", "ORDER-COUNT"),
                List.of("open-order")),
            "01 LIST-OPEN-ORDERS.", "05 RETURN-CODE PIC X(4).", "05 ORDER-COUNT PIC 9(2).",
            "05 OPEN-ORDER OCCURS 10.", "10 ORDER-ID PIC X(12).", "10 ORDER-STATUS PIC X(2).");
        assertThat(errors(result)).isEmpty();
        assertThat(collection(result, "ListOpenOrders", "openOrder")).isEqualTo(
            new CollectionMember("openOrder", 30, Direction.INPUT_OUTPUT, "OPEN-ORDER",
                "05 OPEN-ORDER OCCURS 10.", 10, true, "orderCount", "OpenOrder",
                EmptyItem.LOW_VALUES, null, true));
    }

    @Test
    void aTableWithoutTheOptionIsNotPartial() {
        MapResult result = map("01 ORDER.", "05 LINE OCCURS 3 PIC X(4).");
        assertThat(collection(result, "Order", "line").partial()).isFalse();
    }

    @Test
    void anInputItemAfterTheTableIsPermitted() {
        MapResult result = map(options("OPEN-ORDER"),
            "01 LIST-OPEN-ORDERS.",
            "05 RESPONSE-DATA.", "10 OPEN-ORDER OCCURS 3 PIC X(14).",
            "05 REQUEST-DATA REDEFINES RESPONSE-DATA.", "10 CUSTOMER-ID PIC X(10).");
        assertThat(errors(result)).isEmpty();
        assertThat(member(type(result, "ListOpenOrders"), "customerId").direction())
            .isEqualTo(Direction.INPUT);
        assertThat(collection(result, "ListOpenOrders", "openOrder").partial()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "NONE|partial table option: item NONE does not exist",
        "N|partial table option: N has no OCCURS",
        "ODO|partial table option: ODO has OCCURS DEPENDING ON. Remove the option: the"
            + " response has only the used entries.",
        "TAX|partial table option: TAX is in an entry of LINE. Only a top-level table can end"
            + " the response.",
        "LINE|partial table option: LINE is not at the end of the response: CODES comes after"
            + " it",
    })
    void optionErrors(String table, String message) {
        MapResult result = map(options(table),
            "01 ORDER.", "05 N PIC 9(2).", "05 ODO OCCURS 1 TO 5 DEPENDING ON N PIC X.",
            "05 LINE OCCURS 4.", "10 ID PIC 9(6).", "10 TAX OCCURS 2 PIC X(3).",
            "05 CODES OCCURS 3 PIC X(2).");
        assertThat(errors(result)).containsExactly(message);
        assertThat(result.model()).isNull();
    }

    @Test
    void aTableInAnInputAreaIsAnError() {
        MapResult result = map(options("ORDER-LINE"),
            "01 SEND-ORDER.",
            "05 REQUEST-DATA.", "10 ORDER-LINE OCCURS 3 PIC X(14).",
            "05 RESPONSE-DATA REDEFINES REQUEST-DATA.", "10 RETURN-CODE PIC X(4).");
        assertThat(errors(result)).containsExactly("partial table option: ORDER-LINE has the"
            + " direction INPUT. A request always has the full table.");
    }

    @Test
    void anOutputItemOfASubtypeAfterTheTableIsAnError() {
        MapResult result = map(options(Map.of("EXPRESS-DATA", new SubTypeDecision("KIND",
                Map.of("EXPRESS-DATA", "E", "STANDARD-DATA", "S"), Map.of())), Map.of(),
                List.of("PARCEL")),
            "01 GET-SHIPMENT.", "05 KIND PIC X.", "05 PARCEL OCCURS 3 PIC X(5).",
            "05 EXPRESS-DATA.", "10 CARRIER PIC X(8).",
            "05 STANDARD-DATA REDEFINES EXPRESS-DATA.", "10 DEPOT PIC X(8).");
        assertThat(errors(result)).containsExactly("partial table option: PARCEL is not at the"
            + " end of the response: CARRIER comes after it");
    }

    @Test
    void aSubtypeWithoutOutputItemsAfterTheTableIsPermitted() {
        MapResult result = map(options(Map.of("EXPRESS-DATA", new SubTypeDecision("KIND",
                Map.of("EXPRESS-DATA", "E", "STANDARD-DATA", "S"), Map.of())), Map.of(),
                List.of("PARCEL")),
            "01 GET-SHIPMENT.", "05 KIND PIC X.",
            "05 EXPRESS-DATA.", "10 PARCEL OCCURS 3 PIC X(5).",
            "05 STANDARD-DATA REDEFINES EXPRESS-DATA.", "10 DEPOT PIC X(8).");
        assertThat(errors(result)).isEmpty();
    }

    @Test
    void aTableInASkippedRedefinesIsAnError() {
        MapResult result = map(options("CODES"),
            "01 ORDER.", "05 TEXT PIC X(5).", "05 CODES REDEFINES TEXT OCCURS 5 PIC X.");
        assertThat(errors(result)).containsExactly("partial table option: CODES is not in a"
            + " generated class, so the option has no effect");
    }

    @Test
    void anUnmappedTableIsNotReportedWithAnotherError() {
        MapResult result = map(options("CODES", "NONE"),
            "01 ORDER.", "05 TEXT PIC X(5).", "05 CODES REDEFINES TEXT OCCURS 5 PIC X.");
        assertThat(errors(result)).containsExactly(
            "partial table option: item NONE does not exist");
    }
}
