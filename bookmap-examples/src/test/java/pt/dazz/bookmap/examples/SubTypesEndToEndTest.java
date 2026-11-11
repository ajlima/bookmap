package pt.dazz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.examples.accounts.CardPayment;
import pt.dazz.bookmap.examples.accounts.CheckingAccount;
import pt.dazz.bookmap.examples.accounts.GetAccount;
import pt.dazz.bookmap.examples.accounts.SavingsAccount;
import pt.dazz.bookmap.examples.accounts.StatementEntry;
import pt.dazz.bookmap.examples.accounts.Transfer;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.simulator.SimulatorTransport;

/**
 * Runs the subtypes example through the client and the simulator.
 */
class SubTypesEndToEndTest {

    private static final Charset EBCDIC = Charset.forName("IBM037");

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static GetAccount request(String accountId) {
        GetAccount request = new GetAccount();
        request.setAccountId(accountId);
        request.setRestartKey("");
        return request;
    }

    private static StatementEntry entry(int number) {
        StatementEntry entry;
        if (number % 2 == 0) {
            CardPayment card = new CardPayment();
            card.setMerchant("MERCHANT " + number);
            entry = card;
        } else {
            Transfer transfer = new Transfer();
            transfer.setCounterpartyIban("DE8937040044053201300" + number % 10);
            entry = transfer;
        }
        entry.setAmount(new BigDecimal("12.50").multiply(BigDecimal.valueOf(number)));
        return entry;
    }

    private static CheckingAccount page(GetAccount call, int first, int count, boolean more) {
        CheckingAccount account = new CheckingAccount();
        account.setAccountId(call.getAccountId());
        account.setOverdraftLimit(new BigDecimal("1500.00"));
        List<StatementEntry> entries = new ArrayList<>();
        for (int i = first; i < first + count; i++) {
            entries.add(entry(i));
        }
        account.setEntries(entries);
        account.setMoreData(more ? "Y" : "N");
        account.setRestartKey(more ? String.valueOf(first + count) : "");
        return account;
    }

    @Test
    void baseRequestReturnsTheSubtype() {
        simulator.registerResponder(GetAccount.class, call -> {
            SavingsAccount account = new SavingsAccount();
            account.setInterestRate(new BigDecimal("0.0325"));
            account.setMaturityDate(LocalDate.of(2030, 12, 31));
            account.setMoreData("N");
            return account;
        });

        GetAccount result = client.execute(request("ACC-0000001"));

        assertThat(result).isInstanceOf(SavingsAccount.class);
        SavingsAccount savings = (SavingsAccount) result;
        assertThat(savings.getAccountId()).isEqualTo("ACC-0000001");
        assertThat(savings.getAccountType()).isEqualTo(2);
        assertThat(savings.getInterestRate()).isEqualByComparingTo("0.0325");
        assertThat(savings.getMaturityDate()).isEqualTo(LocalDate.of(2030, 12, 31));
    }

    @Test
    void pagesContinueOnTheNewSubclass() {
        simulator.registerResponder(GetAccount.class, call -> {
            String key = call.getRestartKey();
            int first = key == null || key.isBlank() ? 0 : Integer.parseInt(key.trim());
            return first == 0 ? page(call, 0, 10, true) : page(call, first, 3, false);
        });

        GetAccount result = client.execute(request("ACC-0000002"));

        assertThat(result).isInstanceOf(CheckingAccount.class);
        List<StatementEntry> entries = ((CheckingAccount) result).getEntries();
        assertThat(entries).hasSize(13);
        assertThat(entries).extracting(entry -> entry.getClass().getSimpleName())
            .startsWith("CardPayment", "Transfer", "CardPayment");
        assertThat(((CardPayment) entries.get(12)).getMerchant()).isEqualTo("MERCHANT 12");
        assertThat(entries.get(12).getEntryType()).isEqualTo("C");
        assertThat(simulator.receivedRequests()).hasSize(2);
        String second = new String(simulator.receivedRequests().get(1).payload(), EBCDIC);
        assertThat(second).isEqualTo("ACC-0000002 10        01");
    }

    @Test
    void pageWithAnotherTypeFails() {
        simulator.registerResponder(GetAccount.class, call -> {
            if (call instanceof CheckingAccount) {
                SavingsAccount savings = new SavingsAccount();
                savings.setMoreData("N");
                return savings;
            }
            return page(call, 0, 1, true);
        });

        assertThatThrownBy(() -> client.execute(request("ACC-0000003")))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("Only the first response can change the type");
    }

    @Test
    void unknownAccountType() {
        simulator.register("GetAccount", bytes -> "          N07".getBytes(EBCDIC));

        assertThatThrownBy(() -> client.execute(request("ACC-0000004")))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("unknown discriminator value '7'");
    }
}
