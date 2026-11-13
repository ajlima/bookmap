package pt.dazz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.simulator.SimulatedAbend;
import pt.dazz.bookmap.simulator.SimulatorTransport;

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
    void requestLayoutIs22BytesAndRestartKeyGoesBack() {
        registerPagedHandler();

        client.execute(newRequest());

        Charset ibm037 = CodecContext.defaults().charset();
        byte[] first = simulator.receivedRequests().get(0).payload();
        byte[] second = simulator.receivedRequests().get(1).payload();
        assertThat(first).hasSize(22);
        assertThat(new String(Arrays.copyOfRange(first, 0, 10), ibm037)).isEqualTo("CUST000042");
        assertThat(new String(Arrays.copyOfRange(second, 10, 22), ibm037)).isEqualTo("50          ");
    }

    @Test
    void responseLayoutIs80BytesPlus26PerOrder() {
        var codec = BookCodecRegistry.loadDefault().codecFor(GetCustomerOrders.class);
        GetCustomerOrders page = newRequest();
        page.setReturnCode("0000");
        page.setReturnMessage("OK");
        page.setMoreData("N");

        page.setOrders(orders(50));
        assertThat(codec.encodeResponse(page, CodecContext.defaults())).hasSize(80 + 50 * 26);
        page.setOrders(orders(3));
        assertThat(codec.encodeResponse(page, CodecContext.defaults())).hasSize(80 + 3 * 26);
    }

    private static List<Order> orders(int count) {
        List<Order> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(order(i));
        }
        return list;
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
        assertThat(simulator.receivedRequests()).hasSize(1);
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

    @Test
    void amountAndDateBytes() {
        var codec = BookCodecRegistry.loadDefault().codecFor(GetCustomerOrders.class);
        Order refund = new Order();
        refund.setOrderNumber("ORD0000001");
        refund.setTotalAmount(new BigDecimal("-1234567890123.45"));
        refund.setOrderDate(LocalDate.of(2026, 2, 28));
        GetCustomerOrders page = newRequest();
        page.setReturnCode("0000");
        page.setReturnMessage("OK");
        page.setMoreData("N");
        page.setOrders(List.of(refund));

        byte[] response = codec.encodeResponse(page, CodecContext.defaults());

        // The order starts at byte 80: number (10), amount (COMP-3, 8), date (8).
        assertThat(Arrays.copyOfRange(response, 90, 98))
            .containsExactly(0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34, 0x5D);
        assertThat(Arrays.copyOfRange(response, 98, 106))
            .containsExactly(0xF2, 0xF0, 0xF2, 0xF6, 0xF0, 0xF2, 0xF2, 0xF8);
    }
}
