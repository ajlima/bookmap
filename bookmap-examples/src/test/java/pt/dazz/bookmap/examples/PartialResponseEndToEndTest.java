package pt.dazz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.examples.orders.ListOpenOrders;
import pt.dazz.bookmap.examples.orders.OpenOrder;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.simulator.SimulatorTransport;

class PartialResponseEndToEndTest {

    private static final Charset EBCDIC = Charset.forName("IBM037");

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static ListOpenOrders request() {
        ListOpenOrders request = new ListOpenOrders();
        request.setCustomerId("CUST000042");
        return request;
    }

    @Test
    void responseWithTheUsedEntriesOnly() {
        // The program sends 4 + 2 + 2 x 14 = 34 bytes, not the full 146 bytes.
        simulator.register("ListOpenOrders",
            bytes -> "000002ORD000000001OPORD000000002SH".getBytes(EBCDIC));

        ListOpenOrders result = client.execute(request());

        assertThat(result.getOrders()).extracting(OpenOrder::getOrderId)
            .containsExactly("ORD000000001", "ORD000000002");
        assertThat(result.getReturnCode()).isEqualTo("0000");
    }

    @Test
    void fullResponseFromTheTypedSimulator() {
        simulator.register(ListOpenOrders.class, call -> {
            List<OpenOrder> orders = new ArrayList<>();
            OpenOrder order = new OpenOrder();
            order.setOrderId("ORD000000003");
            order.setOrderStatus("OP");
            orders.add(order);
            call.setOrders(orders);
            call.setReturnCode("0000");
        });

        ListOpenOrders result = client.execute(request());

        assertThat(result.getOrders()).extracting(OpenOrder::getOrderId)
            .containsExactly("ORD000000003");
        assertThat(result.getOrderCount()).isEqualTo(1);
    }

    @Test
    void responseThatEndsInsideAnEntry() {
        simulator.register("ListOpenOrders",
            bytes -> "000002ORD000000001OPORD0000".getBytes(EBCDIC));

        assertThatThrownBy(() -> client.execute(request()))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListOpenOrders.orders (order 40): the response ends inside slot 1,"
                + " after 7 of 14 bytes");
    }

    @Test
    void responseWithFewerEntriesThanTheCount() {
        simulator.register("ListOpenOrders",
            bytes -> "000002ORD000000001OP".getBytes(EBCDIC));

        assertThatThrownBy(() -> client.execute(request()))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListOpenOrders.orders (order 40): count 2 is more than the number of"
                + " slots in the response (1)");
    }
}
