package pt.daaz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.daaz.bookmap.BookmapClient;
import pt.daaz.bookmap.exception.RemoteAbendException;
import pt.daaz.bookmap.simulator.SimulatedAbend;
import pt.daaz.bookmap.simulator.SimulatorTransport;

class EndToEndTest {

    private static final int TOTAL_ORDERS = 120;
    private static final int PAGE_SIZE = 50;

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static Order order(int number) {
        Order order = new Order();
        order.setOrderNumber(String.format("ORD%07d", number));
        order.setTotalAmount(new BigDecimal("10.50").multiply(BigDecimal.valueOf(number)));
        order.setOrderDate(LocalDate.of(2026, 1, 1).plusDays(number));
        return order;
    }

    private void registerPagedHandler() {
        simulator.register(GetCustomerOrders.class, call -> {
            String key = call.getRestartKey();
            int start = key == null || key.isBlank() ? 0 : Integer.parseInt(key.trim());
            int end = Math.min(start + PAGE_SIZE, TOTAL_ORDERS);
            List<Order> page = new ArrayList<>();
            for (int i = start; i < end; i++) {
                page.add(order(i + 1));
            }
            call.setOrders(page);
            call.setReturnCode("0000");
            call.setReturnMessage("OK");
            boolean more = end < TOTAL_ORDERS;
            call.setMoreData(more ? "Y" : "N");
            call.setRestartKey(more ? String.valueOf(end) : "");
        });
    }

    private static GetCustomerOrders newRequest() {
        GetCustomerOrders request = new GetCustomerOrders();
        request.setCustomerId("CUST000042");
        request.setRestartKey("");
        return request;
    }

    @Test
    void paginates120OrdersInPagesOf50() {
        registerPagedHandler();

        GetCustomerOrders result = client.execute(newRequest());

        assertThat(result.getOrders()).hasSize(TOTAL_ORDERS);
        assertThat(result.getOrders().get(119).getOrderNumber()).isEqualTo("ORD0000120");
        assertThat(simulator.receivedRequests()).hasSize(3);
        assertThat(result.getOrderCount()).isEqualTo(20);
        assertThat(result.getMoreData()).isEqualTo("N");
    }

    @Test
    void businessErrorStopsPagination() {
        simulator.register(GetCustomerOrders.class, call -> {
            boolean secondPage = "50".equals(call.getRestartKey() == null ? "" : call.getRestartKey().trim());
            List<Order> page = new ArrayList<>();
            if (secondPage) {
                call.setReturnCode("E001");
                call.setReturnMessage("Customer blocked");
                call.setMoreData("Y");
            } else {
                for (int i = 0; i < PAGE_SIZE; i++) {
                    page.add(order(i + 1));
                }
                call.setReturnCode("0000");
                call.setReturnMessage("OK");
                call.setMoreData("Y");
                call.setRestartKey("50");
            }
            call.setOrders(page);
        });

        assertThatThrownBy(() -> client.execute(newRequest()))
            .isInstanceOfSatisfying(OrderQueryException.class,
                e -> assertThat(e.returnCode()).isEqualTo("E001"))
            .hasMessageContaining("Customer blocked");
        assertThat(simulator.receivedRequests()).hasSize(2);
    }

    @Test
    void abendBecomesRemoteAbendException() {
        simulator.register(GetCustomerOrders.class, call -> {
            throw new SimulatedAbend("ASRA");
        });

        assertThatThrownBy(() -> client.execute(newRequest()))
            .isInstanceOfSatisfying(RemoteAbendException.class,
                e -> assertThat(e.abendCode()).isEqualTo("ASRA"));
    }

    @Test
    void executingTheSamePojoTwiceDoesNotDuplicate() {
        registerPagedHandler();
        GetCustomerOrders request = newRequest();

        client.execute(request);
        assertThat(request.getOrders()).hasSize(TOTAL_ORDERS);
        client.execute(request);

        assertThat(request.getOrders()).hasSize(TOTAL_ORDERS);
        assertThat(request.getOrders().get(0).getOrderNumber()).isEqualTo("ORD0000001");
        assertThat(simulator.receivedRequests()).hasSize(6);
    }

    @Test
    void amountsAndDatesSurviveTheRoundTrip() {
        simulator.register(GetCustomerOrders.class, call -> {
            Order refund = new Order();
            refund.setOrderNumber("ORD0000001");
            refund.setTotalAmount(new BigDecimal("-1234567890123.45"));
            refund.setOrderDate(LocalDate.of(2026, 2, 28));
            call.setOrders(List.of(refund));
            call.setReturnCode("0000");
            call.setReturnMessage("OK");
            call.setMoreData("N");
        });

        GetCustomerOrders result = client.execute(newRequest());

        assertThat(result.getOrders()).hasSize(1);
        assertThat(result.getOrders().get(0).getTotalAmount()).isEqualByComparingTo("-1234567890123.45");
        assertThat(result.getOrders().get(0).getOrderDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }
}
