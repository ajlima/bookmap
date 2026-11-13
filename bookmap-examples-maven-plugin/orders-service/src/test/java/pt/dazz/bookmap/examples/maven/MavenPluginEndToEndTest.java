package pt.dazz.bookmap.examples.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.examples.maven.billing.GetInvoice;
import pt.dazz.bookmap.examples.maven.billing.PartyRole;
import pt.dazz.bookmap.examples.maven.billing.InvoiceLine;
import pt.dazz.bookmap.examples.maven.orders.GetOrder;
import pt.dazz.bookmap.examples.maven.orders.OrderLine;
import pt.dazz.bookmap.simulator.SimulatorTransport;

/**
 * Uses the classes that the Maven plugin generates in the module {@code mainframe-model}.
 */
class MavenPluginEndToEndTest {

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static OrderLine line(String product, int quantity) {
        OrderLine line = new OrderLine();
        line.setProductCode(product);
        line.setQuantity(quantity);
        return line;
    }

    @Test
    void orderFromTheOrdersSubpackage() {
        simulator.register(GetOrder.class, call -> {
            assertThat(call.getOrderId()).isEqualTo("ORD0000042");
            call.setOrderTotal(new BigDecimal("149.85"));
            call.setOrderLine(List.of(line("PRD000000001", 2), line("PRD000000002", 1)));
        });
        GetOrder request = new GetOrder();
        request.setOrderId("ORD0000042");

        GetOrder result = client.execute(request);

        assertThat(result.getOrderTotal()).isEqualByComparingTo("149.85");
        assertThat(result.getOrderLine()).extracting(OrderLine::getProductCode)
            .containsExactly("PRD000000001", "PRD000000002");
    }

    @Test
    void invoiceWithTheCopyMemberAndTheEnum() {
        simulator.register(GetInvoice.class, call -> {
            assertThat(call.getInvoiceId()).isEqualTo("INV000000007");
            assertThat(call.getPartyRole()).isEqualTo(PartyRole.BILL_TO);
            call.setAmountDue(new BigDecimal("75.00"));
            call.setPartyRole(PartyRole.SHIP_TO);
            call.setStreet("12 Harbour Road");
            call.setCity("Rotterdam");
            call.setCountryCode("NL");
        });
        GetInvoice request = new GetInvoice();
        request.setInvoiceId("INV000000007");
        request.setPartyRole(PartyRole.BILL_TO);

        GetInvoice result = client.execute(request);

        assertThat(result.getAmountDue()).isEqualByComparingTo("75.00");
        assertThat(result.getCity()).isEqualTo("Rotterdam");
        assertThat(result.getCountryCode()).isEqualTo("NL");
        assertThat(result.getPartyRole()).isEqualTo(PartyRole.SHIP_TO);
    }

    @Test
    void countFieldsGiveTheNumberOfUsedEntries() {
        simulator.register(GetInvoice.class, call -> {
            InvoiceLine first = new InvoiceLine();
            first.setProductCode("PRD000000001");
            first.setLineAmount(new BigDecimal("50.00"));
            InvoiceLine second = new InvoiceLine();
            second.setProductCode("PRD000000002");
            second.setLineAmount(new BigDecimal("25.00"));
            call.setInvoiceLine(List.of(first, second));
            call.setTaxSummaryEntry(List.of());
        });
        GetInvoice request = new GetInvoice();
        request.setInvoiceId("INV000000007");

        GetInvoice result = client.execute(request);

        assertThat(result.getInvoiceLineCount()).isEqualTo(2);
        assertThat(result.getInvoiceLine()).extracting(InvoiceLine::getProductCode)
            .containsExactly("PRD000000001", "PRD000000002");
        assertThat(result.getTaxSummaryCount()).isZero();
        assertThat(result.getTaxSummaryEntry()).isEmpty();
    }
}
