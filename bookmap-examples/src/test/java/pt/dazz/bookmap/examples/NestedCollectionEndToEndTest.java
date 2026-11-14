package pt.dazz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.examples.orders.GetOrderTaxes;
import pt.dazz.bookmap.examples.orders.LineTax;
import pt.dazz.bookmap.examples.orders.TaxedOrderLine;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.simulator.SimulatorTransport;

class NestedCollectionEndToEndTest {

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static LineTax tax(String code, String amount) {
        LineTax tax = new LineTax();
        tax.setTaxCode(code);
        tax.setTaxAmount(new BigDecimal(amount));
        return tax;
    }

    private static TaxedOrderLine line(String product, int quantity, LineTax... taxes) {
        TaxedOrderLine line = new TaxedOrderLine();
        line.setProductCode(product);
        line.setQuantity(quantity);
        line.setTaxes(List.of(taxes));
        return line;
    }

    private static GetOrderTaxes request() {
        GetOrderTaxes request = new GetOrderTaxes();
        request.setOrderId("ORD0000042");
        return request;
    }

    @Test
    void getOrderTaxesRoundTrip() {
        simulator.register(GetOrderTaxes.class, call -> {
            call.setLines(List.of(
                line("PRD00001", 2, tax("VAT1", "4.60"), tax("ECO1", "0.10")),
                line("PRD00002", 1)));
            call.setOrderTotal(new BigDecimal("24.70"));
        });

        GetOrderTaxes result = client.execute(request());

        assertThat(result.getLineCount()).isEqualTo(2);
        assertThat(result.getLines()).extracting(TaxedOrderLine::getTaxCount)
            .containsExactly(2, 0);
        assertThat(result.getLines().get(0).getTaxes()).extracting(LineTax::getTaxCode)
            .containsExactly("VAT1", "ECO1");
        assertThat(result.getLines().get(1).getTaxes()).isEmpty();
        assertThat(result.getOrderTotal()).isEqualByComparingTo("24.70");
    }

    @Test
    void tooManyTaxesNameTheLine() {
        simulator.register(GetOrderTaxes.class, call -> call.setLines(List.of(
            line("PRD00001", 1),
            line("PRD00002", 1, tax("VAT1", "1"), tax("VAT2", "1"), tax("VAT3", "1"),
                tax("VAT4", "1")))));

        assertThatThrownBy(() -> client.execute(request()))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("GetOrderTaxes.lines (order 30) item 1: TaxedOrderLine.taxes"
                + " (order 40): the list has 4 items, more than maxOccurs 3");
    }
}
