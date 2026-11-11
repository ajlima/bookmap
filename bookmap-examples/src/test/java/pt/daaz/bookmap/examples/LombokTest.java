package pt.daaz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.daaz.bookmap.BookmapClient;
import pt.daaz.bookmap.codec.BookCodecRegistry;
import pt.daaz.bookmap.convert.CodecContext;
import pt.daaz.bookmap.examples.billing.Invoice;
import pt.daaz.bookmap.examples.billing.ListInvoices;
import pt.daaz.bookmap.simulator.SimulatorTransport;
import pt.daaz.bookmap.spi.BookRequest;

class LombokTest {

    private static final Charset EBCDIC = CodecContext.defaults().charset();

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    @Test
    void dataClassRoundTrip() {
        simulator.register(CustomerProfile.class, call -> {
            call.setName("Maria Santos");
            call.setAge(41);
            call.setVisits(7);
            call.setLoyaltyPoints(123456789012L);
            call.setLifetimeSpend(99L);
            call.setCreditLimit(new BigDecimal("5000.25"));
            call.setBalance(new BigDecimal("-12.50"));
            call.setBirthDate(LocalDate.of(1985, 3, 9));
            call.setPreferredContact(LocalTime.of(9, 30, 15));
            call.setLastLogin(LocalDateTime.of(2026, 11, 11, 8, 5, 1));
        });
        CustomerProfile request = new CustomerProfile();
        request.setCustomerId("CUST000042");

        CustomerProfile result = client.execute(request);

        assertThat(result.getCustomerId()).isEqualTo("CUST000042");
        assertThat(result.getName()).isEqualTo("Maria Santos");
        assertThat(result.getAge()).isEqualTo(41);
        assertThat(result.getVisits()).isEqualTo(7);
        assertThat(result.getLoyaltyPoints()).isEqualTo(123456789012L);
        assertThat(result.getLifetimeSpend()).isEqualTo(99L);
        assertThat(result.getCreditLimit()).isEqualByComparingTo("5000.25");
        assertThat(result.getBalance()).isEqualByComparingTo("-12.50");
        assertThat(result.getBirthDate()).isEqualTo(LocalDate.of(1985, 3, 9));
        assertThat(result.getPreferredContact()).isEqualTo(LocalTime.of(9, 30, 15));
        assertThat(result.getLastLogin()).isEqualTo(LocalDateTime.of(2026, 11, 11, 8, 5, 1));
    }

    @Test
    void partialLombokWithInputCollection() {
        simulator.register(UpdateShipment.class, call -> call.setStatus("OK"));
        UpdateShipment request = new UpdateShipment();
        request.setShipmentId("SHP000000001");
        request.setWeightKg(new BigDecimal("-12.50"));
        request.setLines(List.of(line("SKU0000001", 3), line("SKU0000002", 12), line("SKU0000003", 1)));

        UpdateShipment result = client.execute(request);

        assertThat(result.getStatus()).isEqualTo("OK");
        BookRequest sent = simulator.receivedRequests().get(0);
        assertThat(sent.operation()).isEqualTo("UpdateShipment");
        // shipmentId (12) + weightKg (9 digits COMP-3 = 5 bytes) + lineCount (3) + 3 lines x 14 bytes
        assertThat(sent.payload()).hasSize(12 + 5 + 3 + 3 * 14);
        assertThat(new String(sent.payload(), 17, 3, EBCDIC)).isEqualTo("003");
        assertThat(sent.payload()[16]).isEqualTo((byte) 0x0D);
    }

    @Test
    void inheritedFieldIsInLayout() {
        simulator.register(ListInvoices.class, call -> call.setInvoices(List.of()));
        ListInvoices request = new ListInvoices();
        request.setAccountId("ACC0000042");

        client.execute(request);

        byte[] payload = simulator.receivedRequests().get(0).payload();
        assertThat(payload).hasSize(10);
        assertThat(new String(payload, EBCDIC)).isEqualTo("ACC0000042");
    }

    @Test
    void packageDefaultsApply() {
        ListInvoices call = new ListInvoices();
        call.setAccountId("ACC0000042");
        call.setInvoices(List.of(invoice("INV0000001", "12.34", "1.50"), invoice("INV0000002", "-12.34", "-1.50")));
        var codec = BookCodecRegistry.loadDefault().codecFor(ListInvoices.class);

        byte[] response = codec.encodeResponse(call, CodecContext.defaults());

        // accountId (10) + invoiceCount (unsigned 9(3), COMP-3 from the defaults = 2 bytes)
        // + 2 x (invoiceNumber 10 + amount 5 + tax 4)
        assertThat(response).hasSize(10 + 2 + 2 * 19);
        assertThat(Arrays.copyOfRange(response, 10, 12)).containsExactly(0x00, 0x2F);
        assertThat(Arrays.copyOfRange(response, 12, 22))
            .isEqualTo("INV0000001".getBytes(EBCDIC));
        assertThat(Arrays.copyOfRange(response, 22, 27))
            .containsExactly(0x00, 0x00, 0x01, 0x23, 0x4C);
        assertThat(Arrays.copyOfRange(response, 27, 31)).containsExactly(0x00, 0x00, 0x15, 0x0C);
        assertThat(Arrays.copyOfRange(response, 41, 46))
            .containsExactly(0x00, 0x00, 0x01, 0x23, 0x4D);
        assertThat(Arrays.copyOfRange(response, 46, 50)).containsExactly(0x00, 0x00, 0x15, 0x0D);

        ListInvoices decoded = codec.newInstance();
        codec.decodeResponse(response, decoded, CodecContext.defaults());
        assertThat(decoded.getInvoices().get(1).getAmount()).isEqualByComparingTo("-12.34");
        assertThat(decoded.getInvoices().get(1).getTax()).isEqualByComparingTo("-1.50");
    }

    private static ShipmentLine line(String sku, int quantity) {
        ShipmentLine line = new ShipmentLine();
        line.setSku(sku);
        line.setQuantity(quantity);
        return line;
    }

    private static Invoice invoice(String number, String amount, String tax) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(number);
        invoice.setAmount(new BigDecimal(amount));
        invoice.setTax(new BigDecimal(tax));
        return invoice;
    }
}
