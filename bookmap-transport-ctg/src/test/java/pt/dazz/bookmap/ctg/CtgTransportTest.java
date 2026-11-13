package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;

class CtgTransportTest {

    private final FakeConnector connector = new FakeConnector();

    private CtgTransport.Builder builder() {
        return CtgTransport.builder()
            .connector(connector)
            .route("GetCustomerOrders", "PGMORD01", "TX01")
            .route("GetInvoice", "PGMINV01");
    }

    private final CtgTransport transport = builder().build();

    private static BookRequest request(String operation, int payloadLength, int maxResponse) {
        return new BookRequest(operation, new byte[payloadLength], maxResponse);
    }

    private Throwable failWith(int returnCode, String abendCode) {
        connector.thenReply(new EciReply(returnCode, abendCode, new byte[8]));
        return org.assertj.core.api.Assertions.catchThrowable(
            () -> transport.exchange(request("GetInvoice", 4, 8)));
    }

    @Test
    void sendsCallWithRouteAndSettings() {
        CtgTransport configured = builder()
            .server("CICSPROD")
            .credentials("ORDUSER", "secret")
            .timeout(Duration.ofSeconds(45))
            .build();
        byte[] payload = {1, 2, 3};
        configured.exchange(new BookRequest("GetCustomerOrders", payload, 10));
        EciCall call = connector.calls().getFirst();
        assertThat(call.server()).isEqualTo("CICSPROD");
        assertThat(call.userid()).isEqualTo("ORDUSER");
        assertThat(call.password()).isEqualTo("secret");
        assertThat(call.program()).isEqualTo("PGMORD01");
        assertThat(call.transaction()).isEqualTo("TX01");
        assertThat(call.commarea()).isSameAs(payload);
        assertThat(call.timeoutSeconds()).isEqualTo(45);
    }

    @Test
    void routeWithoutTransactionUsesDefaultTransaction() {
        builder().defaultTransaction("ORD1").build().exchange(request("GetInvoice", 4, 8));
        assertThat(connector.calls().getLast().transaction()).isEqualTo("ORD1");
        transport.exchange(request("GetInvoice", 4, 8));
        assertThat(connector.calls().getLast().transaction()).isNull();
    }

    @Test
    void commareaLengthIsLargerOfPayloadAndResponse() {
        transport.exchange(request("GetInvoice", 120, 80));
        assertThat(connector.calls().getLast().commareaLength()).isEqualTo(120);
        transport.exchange(request("GetInvoice", 20, 300));
        assertThat(connector.calls().getLast().commareaLength()).isEqualTo(300);
    }

    @Test
    void returnsReplyCommareaOnSuccess() {
        byte[] commarea = {9, 8, 7};
        connector.thenReply(new EciReply(0, null, commarea));
        assertThat(transport.exchange(request("GetInvoice", 3, 3))).isSameAs(commarea);
    }

    @Test
    void unknownOperationIsOperationNotFound() {
        assertThatThrownBy(() -> transport.exchange(request("GetPayment", 4, 8)))
            .isExactlyInstanceOf(OperationNotFoundException.class)
            .hasMessageContaining("GetPayment");
        assertThat(connector.opened()).isZero();
    }

    @Test
    void commareaOverLimitFails() {
        assertThatThrownBy(() -> transport.exchange(request("GetInvoice", 10, 32_501)))
            .isExactlyInstanceOf(TransportException.class)
            .hasMessageContaining("32501")
            .hasMessageContaining("32500");
        assertThat(connector.opened()).isZero();
    }

    @Test
    void commareaAtLimitIsSent() {
        transport.exchange(request("GetInvoice", 10, CtgTransport.MAX_COMMAREA_LENGTH));
        assertThat(connector.calls().getLast().commareaLength()).isEqualTo(32_500);
    }

    @Test
    void abendIsRemoteAbend() {
        assertThat(failWith(-7, "ASRA")).isExactlyInstanceOf(RemoteAbendException.class)
            .extracting(e -> ((RemoteAbendException) e).abendCode()).isEqualTo("ASRA");
    }

    @Test
    void aei0AbendIsOperationNotFound() {
        assertThat(failWith(-7, "AEI0")).isExactlyInstanceOf(OperationNotFoundException.class)
            .hasMessageContaining("PGMINV01")
            .hasMessageContaining("AEI0");
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -6})
    void timeoutCodesAreTimeout(int code) {
        assertThat(failWith(code, null)).isExactlyInstanceOf(TransportTimeoutException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-3, -4, -16, -17, -22, -26, -28, -29})
    void connectionCodesAreConnectionException(int code) {
        assertThat(failWith(code, null)).isExactlyInstanceOf(ConnectionException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-27, -1, -9, -30, -999})
    void otherCodesAreTransportException(int code) {
        assertThat(failWith(code, null)).isExactlyInstanceOf(TransportException.class);
    }

    @Test
    void messageNamesOperationProgramAndCode() {
        assertThat(failWith(-22, null))
            .hasMessage("GetInvoice (PGMINV01): ECI return code -22 (ECI_ERR_UNKNOWN_SERVER)");
    }

    @Test
    void abendMessageNamesTheAbendCode() {
        assertThat(failWith(-7, "ASRA"))
            .hasMessage("GetInvoice (PGMINV01): ECI return code -7 (ECI_ERR_TRANSACTION_ABEND), abend ASRA");
    }

    @Test
    void connectionIsReusedAfterErrorReturnCode() {
        failWith(-22, null);
        transport.exchange(request("GetInvoice", 4, 8));
        assertThat(connector.opened()).isEqualTo(1);
    }

    @Test
    void callIoFailureIsTransportExceptionAndDiscardsConnection() {
        IOException failure = new IOException("reset");
        connector.thenFail(failure);
        assertThatThrownBy(() -> transport.exchange(request("GetInvoice", 4, 8)))
            .isExactlyInstanceOf(TransportException.class)
            .hasCause(failure)
            .hasMessageContaining("GetInvoice (PGMINV01)");
        assertThat(connector.connections().getFirst().closed()).isTrue();
        transport.exchange(request("GetInvoice", 4, 8));
        assertThat(connector.opened()).isEqualTo(2);
    }

    @Test
    void exchangeAfterCloseFails() {
        transport.close();
        assertThatThrownBy(() -> transport.exchange(request("GetInvoice", 4, 8)))
            .isExactlyInstanceOf(TransportException.class);
    }

    @Test
    void closeIsIdempotent() {
        transport.exchange(request("GetInvoice", 4, 8));
        transport.close();
        transport.close();
        assertThat(connector.connections().getFirst().closed()).isTrue();
    }

    @Test
    void uncheckedCallFailureIsTransportExceptionAndDiscardsConnection() {
        CtgTransport single = builder().maxConnections(1)
            .connectionWaitTimeout(Duration.ofMillis(50)).build();
        IllegalStateException failure = new IllegalStateException("broken library");
        connector.thenFail(failure);
        assertThatThrownBy(() -> single.exchange(request("GetInvoice", 4, 8)))
            .isExactlyInstanceOf(TransportException.class)
            .hasCause(failure);
        assertThat(connector.connections().getFirst().closed()).isTrue();
        single.exchange(request("GetInvoice", 4, 8));
        assertThat(connector.opened()).isEqualTo(2);
    }
}
