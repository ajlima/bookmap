package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.spi.BookRequest;

class CtgTransportBuilderTest {

    private final FakeConnector connector = new FakeConnector();

    private CtgTransport.Builder builder() {
        return CtgTransport.builder().connector(connector).route("GetInvoice", "PGMINV01");
    }

    private EciCall send(CtgTransport transport, String operation) {
        transport.exchange(new BookRequest(operation, new byte[4], 8));
        return connector.calls().getLast();
    }

    @Test
    void buildWithoutConnectorFails() {
        assertThatIllegalStateException()
            .isThrownBy(() -> CtgTransport.builder().route("GetInvoice", "PGMINV01").build())
            .withMessageContaining("connector");
    }

    @Test
    void buildWithoutRoutesFails() {
        assertThatIllegalStateException()
            .isThrownBy(() -> CtgTransport.builder().connector(connector).build())
            .withMessageContaining("route");
    }

    @Test
    void laterRouteReplacesEarlierRoute() {
        CtgTransport transport = builder().route("GetInvoice", "PGMINV02", "INV2").build();
        EciCall call = send(transport, "GetInvoice");
        assertThat(call.program()).isEqualTo("PGMINV02");
        assertThat(call.transaction()).isEqualTo("INV2");
    }

    @Test
    void routesAddsAllEntries() {
        CtgTransport transport = builder()
            .routes(Map.of("GetCustomerOrders", new CtgRoute("PGMORD01", "TX01"),
                "GetShipment", new CtgRoute("PGMSHP01", null)))
            .build();
        assertThat(send(transport, "GetCustomerOrders").program()).isEqualTo("PGMORD01");
        assertThat(send(transport, "GetShipment").program()).isEqualTo("PGMSHP01");
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThatIllegalArgumentException().isThrownBy(() -> builder().timeout(Duration.ofSeconds(-1)));
    }

    @Test
    void rejectsFractionalTimeout() {
        assertThatIllegalArgumentException().isThrownBy(() -> builder().timeout(Duration.ofMillis(1500)));
    }

    @Test
    void rejectsTimeoutOverShortMax() {
        assertThatIllegalArgumentException().isThrownBy(() -> builder().timeout(Duration.ofSeconds(32_768)));
    }

    @Test
    void acceptsTimeoutAtShortMax() {
        CtgTransport transport = builder().timeout(Duration.ofSeconds(32_767)).build();
        assertThat(send(transport, "GetInvoice").timeoutSeconds()).isEqualTo(32_767);
    }

    @Test
    void defaultTimeoutIsThirtySeconds() {
        assertThat(send(builder().build(), "GetInvoice").timeoutSeconds()).isEqualTo(30);
    }

    @Test
    void zeroTimeoutMeansNoTimeout() {
        CtgTransport transport = builder().timeout(Duration.ZERO).build();
        assertThat(send(transport, "GetInvoice").timeoutSeconds()).isZero();
    }

    @Test
    void rejectsMaxConnectionsBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> builder().maxConnections(0));
    }

    @Test
    void rejectsBadDefaultTransaction() {
        assertThatIllegalArgumentException().isThrownBy(() -> builder().defaultTransaction("TX001"));
    }

    @Test
    void rejectsNullValues() {
        CtgTransport.Builder builder = CtgTransport.builder();
        assertThatNullPointerException().isThrownBy(() -> builder.connector(null));
        assertThatNullPointerException().isThrownBy(() -> builder.route(null, "PGMINV01"));
        assertThatNullPointerException().isThrownBy(() -> builder.route("GetInvoice", null));
        assertThatNullPointerException().isThrownBy(() -> builder.route(null, "PGMINV01", "INV1"));
        assertThatNullPointerException().isThrownBy(() -> builder.routes(null));
        assertThatNullPointerException().isThrownBy(() -> builder.timeout(null));
        assertThatNullPointerException().isThrownBy(() -> builder.connectionWaitTimeout(null));
    }

    @Test
    void nullResetsServerTransactionAndCredentials() {
        CtgTransport transport = builder()
            .server("CICSPROD").server(null)
            .defaultTransaction("ORD1").defaultTransaction(null)
            .credentials("ORDUSER", "secret").credentials(null, null)
            .build();
        EciCall call = send(transport, "GetInvoice");
        assertThat(call.server()).isNull();
        assertThat(call.transaction()).isNull();
        assertThat(call.userid()).isNull();
        assertThat(call.password()).isNull();
    }

    @Test
    void buildOpensNoConnection() {
        builder().maxConnections(3).connectionWaitTimeout(Duration.ofSeconds(1)).build();
        assertThat(connector.opened()).isZero();
    }
}
