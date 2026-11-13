package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.copybook.map.Mapping.map;
import static pt.dazz.bookmap.copybook.map.Mapping.type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.ClassModel;

class OperationHandlersTest {

    private static final String[] PAGED = {
        "01 LIST-ORDERS.",
        "   05 RESTART-KEY PIC X(12).",
        "   05 ORDER-REQUEST.",
        "      10 CUSTOMER-ID PIC X(10).",
        "   05 ORDER-RESPONSE REDEFINES ORDER-REQUEST.",
        "      10 MORE-DATA PIC X.",
        "      10 ORDER-LINE OCCURS 3.",
        "         15 ORDER-ID PIC X(10).",
        "01 GET-ORDER.",
        "   05 ORDER-ID PIC X(10).",
    };

    private static MappingOptions handlers(Map<String, String> continuable,
                                           Map<String, String> validatable) {
        return MappingOptions.defaults().withHints(Map.of(), continuable, validatable);
    }

    /** Gives the errors and warnings. The record length is an INFO diagnostic of each record. */
    private static List<Diagnostic> problems(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() != Severity.INFO)
            .toList();
    }

    private static List<String> errors(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR)
            .map(Diagnostic::message).toList();
    }

    @Test
    void handlersGoToTheOperationClassOfTheRecord() {
        MapResult result = map(handlers(Map.of("list-orders", "com.example.OrderPaging"),
            Map.of(" LIST-ORDERS ", "ReturnCodes")), PAGED);
        assertThat(problems(result)).isEmpty();
        ClassModel list = type(result, "ListOrders");
        assertThat(list.continuable()).isEqualTo("com.example.OrderPaging");
        assertThat(list.validatable()).isEqualTo("ReturnCodes");
        assertThat(type(result, "OrderLine").continuable()).isNull();
        ClassModel get = type(result, "GetOrder");
        assertThat(get.continuable()).isNull();
        assertThat(get.validatable()).isNull();
        assertThat(list.members()).filteredOn(m -> m.name().equals("orderLine"))
            .extracting(m -> m.direction()).containsExactly(Direction.OUTPUT);
    }

    @Test
    void validatableClassPermitsAnInputOutputCollection() {
        MapResult result = map(handlers(Map.of(), Map.of("GET-ORDER", "ReturnCodes")),
            "01 GET-ORDER.", "05 ORDER-LINE OCCURS 3.", "10 ORDER-ID PIC X(10).");
        assertThat(problems(result)).isEmpty();
        assertThat(type(result, "GetOrder").validatable()).isEqualTo("ReturnCodes");
    }

    @Test
    void continuableClassRejectsAnInputOutputCollection() {
        MapResult result = map(handlers(Map.of("GET-ORDER", "OrderPaging"), Map.of()),
            "01 GET-ORDER.", "05 ORDER-LINE OCCURS 3.", "10 ORDER-ID PIC X(10).");
        assertThat(problems(result)).extracting(Diagnostic::line).containsExactly(2);
        assertThat(errors(result)).containsExactly("ORDER-LINE is an INPUT_OUTPUT collection."
            + " BookMap does not permit it in a Continuable class. Put the table in the output"
            + " alternative of a REDEFINES area.");
    }

    @Test
    void optionErrors() {
        Map<String, String> continuable = new LinkedHashMap<>();
        continuable.put("LIST-ORDRS", "OrderPaging");
        continuable.put("LIST-ORDERS", "com.example.1Paging");
        MapResult result = map(handlers(continuable, Map.of("GET-ORDER", "Return Codes")),
            PAGED);
        assertThat(problems(result)).extracting(Diagnostic::line).containsOnly(1);
        assertThat(errors(result)).containsExactly(
            "continuable: LIST-ORDRS is not a record. Use the name of a level 01 item.",
            "continuable: 'com.example.1Paging' is not a Java class name",
            "validatable: 'Return Codes' is not a Java class name");
    }
}
