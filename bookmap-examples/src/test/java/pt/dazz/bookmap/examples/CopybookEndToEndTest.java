package pt.dazz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.examples.copybook.CardAccount;
import pt.dazz.bookmap.examples.copybook.GetAccountSummary;
import pt.dazz.bookmap.examples.copybook.InvoiceLine;
import pt.dazz.bookmap.examples.copybook.ListOpenInvoices;
import pt.dazz.bookmap.examples.copybook.LoanAccount;
import pt.dazz.bookmap.simulator.SimulatorTransport;

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
