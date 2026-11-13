package pt.dazz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.examples.copybook.CardAccount;
import pt.dazz.bookmap.examples.copybook.GetAccountSummary;
import pt.dazz.bookmap.examples.copybook.InvoiceLine;
import pt.dazz.bookmap.examples.copybook.ListOpenInvoices;
import pt.dazz.bookmap.examples.copybook.LoanAccount;
import pt.dazz.bookmap.simulator.SimulatorTransport;
import pt.dazz.bookmap.spi.BookCodec;

/**
 * Uses the classes that the build generates from the copybooks in {@code src/main/copybook}.
 */
class CopybookEndToEndTest {

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static InvoiceLine line(String number, String amount) {
        InvoiceLine line = new InvoiceLine();
        line.setInvoiceNumber(number);
        line.setAmountDue(new BigDecimal(amount));
        return line;
    }

    @Test
    void generatedInvoiceListRoundTrip() {
        simulator.register(ListOpenInvoices.class, call -> {
            assertThat(call.getCustomerId()).isEqualTo("CUST000042");
            call.setReturnCode("0000");
            call.setInvoiceLine(List.of(line("INV0000001", "120.50"),
                line("INV0000002", "75.00")));
        });
        ListOpenInvoices request = new ListOpenInvoices();
        request.setReturnCode("");
        request.setCustomerId("CUST000042");
        request.setFromDate(20260101);

        ListOpenInvoices result = client.execute(request);

        assertThat(result.getReturnCode()).isEqualTo("0000");
        assertThat(result.getInvoiceLine()).extracting(InvoiceLine::getInvoiceNumber)
            .containsExactly("INV0000001", "INV0000002");
    }

    @Test
    void generatedInvoiceListFindsInitializedEntriesThroughTheKey() {
        BookCodec<ListOpenInvoices> codec = BookCodecRegistry.loadDefault()
            .codecFor(ListOpenInvoices.class);
        CodecContext latin = new CodecContext(StandardCharsets.ISO_8859_1);
        ListOpenInvoices response = new ListOpenInvoices();
        response.setReturnCode("0000");
        response.setInvoiceLine(List.of(line("INV0000001", "120.50")));
        byte[] bytes = codec.encodeResponse(response, latin);
        // The unused entries get spaces: 4 entries of 15 bytes after the first entry.
        assertThat(Arrays.copyOfRange(bytes, 19, 79)).containsOnly((byte) ' ');

        // INITIALIZE gives spaces in the invoice number and a packed zero in the amount.
        byte[] initialized = Arrays.copyOf(bytes, bytes.length);
        for (int entry = 1; entry < 5; entry++) {
            int amount = 4 + entry * 15 + 10;
            Arrays.fill(initialized, amount, amount + 4, (byte) 0);
            initialized[amount + 4] = 0x0C;
        }
        ListOpenInvoices target = new ListOpenInvoices();
        codec.decodeResponse(initialized, target, latin);
        assertThat(target.getInvoiceLine()).extracting(InvoiceLine::getInvoiceNumber)
            .containsExactly("INV0000001");
    }

    @Test
    void generatedAccountSubtypeRoundTrip() {
        simulator.registerResponder(GetAccountSummary.class, call -> {
            LoanAccount loan = new LoanAccount();
            loan.setAccountId(call.getAccountId());
            loan.setLoanAmount(new BigDecimal("25000.00"));
            loan.setInstalmentsLeft(36);
            return loan;
        });
        GetAccountSummary request = new GetAccountSummary();
        request.setAccountId("ACC-00000042");

        GetAccountSummary result = client.execute(request);

        assertThat(result).isInstanceOf(LoanAccount.class)
            .isNotInstanceOf(CardAccount.class);
        assertThat(result.getAccountKind()).isEqualTo("L");
        assertThat(((LoanAccount) result).getInstalmentsLeft()).isEqualTo(36);
    }
}
