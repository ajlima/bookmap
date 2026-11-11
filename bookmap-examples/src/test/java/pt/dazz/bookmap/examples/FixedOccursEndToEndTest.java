package pt.dazz.bookmap.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.examples.orders.GetOrder;
import pt.dazz.bookmap.examples.orders.ListShipments;
import pt.dazz.bookmap.examples.orders.OrderLine;
import pt.dazz.bookmap.examples.orders.ShipmentSummary;
import pt.dazz.bookmap.simulator.SimulatorTransport;

class FixedOccursEndToEndTest {

    private SimulatorTransport simulator;
    private BookmapClient client;

    @BeforeEach
    void setUp() {
        simulator = SimulatorTransport.builder().build();
        client = BookmapClient.builder().transport(simulator).build();
    }

    private static OrderLine line(int number) {
        OrderLine line = new OrderLine();
        line.setProductCode(String.format("PRD%05d", number));
        line.setQuantity(number);
        line.setUnitPrice(new BigDecimal("9.99"));
        return line;
    }

    private static List<OrderLine> lines(int from, int to) {
        List<OrderLine> lines = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            lines.add(line(i));
        }
        return lines;
    }

    private static GetOrder newOrderRequest() {
        GetOrder request = new GetOrder();
        request.setOrderId("ORD0000042");
        request.setRestartKey("");
        return request;
    }

    @Test
    void getOrderRoundTrip() {
        simulator.register(GetOrder.class, call -> {
            call.setLines(lines(1, 2));
            call.setMoreData("N");
            call.setOrderTotal(new BigDecimal("199.80"));
        });

        GetOrder result = client.execute(newOrderRequest());

        assertThat(result.getLines()).extracting(OrderLine::getProductCode)
            .containsExactly("PRD00001", "PRD00002");
        assertThat(result.getOrderTotal()).isEqualByComparingTo("199.80");
    }

    @Test
    void getOrderPagination() {
        simulator.register(GetOrder.class, call -> {
            boolean first = call.getRestartKey() == null || call.getRestartKey().isBlank();
            call.setLines(first ? lines(1, 3) : lines(4, 4));
            call.setMoreData(first ? "Y" : "N");
            call.setRestartKey(first ? "0003" : "");
            call.setOrderTotal(new BigDecimal("39.96"));
        });

        GetOrder result = client.execute(newOrderRequest());

        assertThat(result.getLines()).extracting(OrderLine::getQuantity)
            .containsExactly(1, 2, 3, 4);
        assertThat(simulator.receivedRequests()).hasSize(2);
    }

    @Test
    void listShipmentsCountMode() {
        simulator.register(ListShipments.class, call -> {
            List<ShipmentSummary> shipments = new ArrayList<>();
            for (String id : List.of("SHP000000001", "SHP000000002")) {
                ShipmentSummary summary = new ShipmentSummary();
                summary.setShipmentId(id);
                summary.setStatus("DL");
                shipments.add(summary);
            }
            call.setShipments(shipments);
            call.setReturnCode("0000");
        });
        ListShipments request = new ListShipments();
        request.setCustomerId("CUST000042");

        ListShipments result = client.execute(request);

        assertThat(result.getShipments()).extracting(ShipmentSummary::getShipmentId)
            .containsExactly("SHP000000001", "SHP000000002");
        assertThat(result.getShipmentCount()).isEqualTo(2);
        assertThat(result.getReturnCode()).isEqualTo("0000");
    }
}
