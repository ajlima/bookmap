package pt.dazz.bookmap.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;

class MqTransportTest {

    private static final byte[] PAYLOAD = {1, 2, 3};

    private final FakeConnector fake = new FakeConnector();

    private MqTransport.Builder builder() {
        return MqTransport.builder().connector(fake)
            .route("GetInvoice", "INVOICE.REQUEST", "INVOICE.REPLY");
    }

    private static BookRequest request(String operation) {
        return new BookRequest(operation, PAYLOAD, 100);
    }

    // Builder rules

    @Test
    void buildWithoutConnectorThrows() {
        assertThatThrownBy(() -> MqTransport.builder().route("GetInvoice", "INVOICE.REQUEST").build())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("connector");
    }

    @Test
    void buildWithoutRouteThrows() {
        assertThatThrownBy(() -> MqTransport.builder().connector(fake).build())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("route");
    }

    @Test
    void timeoutZeroOrNegativeThrows() {
        assertThatThrownBy(() -> builder().timeout(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder().timeout(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxSessionsBelowOneThrows() {
        assertThatThrownBy(() -> builder().maxSessions(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeSessionWaitThrows() {
        assertThatThrownBy(() -> builder().sessionWaitTimeout(Duration.ofMillis(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroSessionWaitIsAccepted() {
        builder().sessionWaitTimeout(Duration.ZERO).build().close();
    }

    @Test
    void nullArgumentsThrowNpe() {
        MqTransport.Builder b = MqTransport.builder();
        assertThatThrownBy(() -> b.connector(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.route(null, "Q")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.route("Op", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.route(null, "Q", "R")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.route("Op", "Q", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.routes(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.correlation(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.timeout(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> b.sessionWaitTimeout(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void routesMapRejectsNullKeyOrValue() {
        java.util.HashMap<String, MqRoute> nullKey = new java.util.HashMap<>();
        nullKey.put(null, new MqRoute("Q", null));
        java.util.HashMap<String, MqRoute> nullValue = new java.util.HashMap<>();
        nullValue.put("Op", null);
        assertThatThrownBy(() -> MqTransport.builder().routes(nullKey))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MqTransport.builder().routes(nullValue))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankDefaultReplyQueueThrows() {
        assertThatThrownBy(() -> builder().defaultReplyQueue(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultReplyQueueNullResets() {
        try (MqTransport transport = MqTransport.builder().connector(fake)
                .route("GetInvoice", "INVOICE.REQUEST")
                .defaultReplyQueue("ORDERS.REPLY").defaultReplyQueue(null).build()) {
            transport.exchange(request("GetInvoice"));
        }
        assertThat(fake.sent.get(0).replyQueue()).isNull();
    }

    @Test
    void laterRouteReplacesEarlier() {
        try (MqTransport transport = builder().route("GetInvoice", "INVOICE.REQUEST.V2").build()) {
            transport.exchange(request("GetInvoice"));
        }
        assertThat(fake.sent.get(0).requestQueue()).isEqualTo("INVOICE.REQUEST.V2");
        assertThat(fake.sent.get(0).replyQueue()).isNull();
    }

    @Test
    void routesMapAddsAll() {
        try (MqTransport transport = MqTransport.builder().connector(fake)
                .routes(Map.of("GetInvoice", new MqRoute("INVOICE.REQUEST", "INVOICE.REPLY"),
                    "GetCustomerOrders", new MqRoute("ORDERS.REQUEST", "ORDERS.REPLY")))
                .build()) {
            transport.exchange(request("GetInvoice"));
            transport.exchange(request("GetCustomerOrders"));
        }
        assertThat(fake.sent).extracting(MqRequest::requestQueue)
            .containsExactly("INVOICE.REQUEST", "ORDERS.REQUEST");
    }

    @Test
    void persistentWithTemporaryReplyQueueThrows() {
        assertThatThrownBy(() -> builder().route("GetCustomerOrders", "ORDERS.REQUEST")
                .persistent(true).build())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("GetCustomerOrders");
    }

    @Test
    void persistentWithNamedReplyQueueIsAccepted() {
        try (MqTransport transport = builder().route("GetCustomerOrders", "ORDERS.REQUEST")
                .defaultReplyQueue("ORDERS.REPLY").persistent(true).build()) {
            transport.exchange(request("GetCustomerOrders"));
        }
        assertThat(fake.sent.get(0).persistent()).isTrue();
    }

    @Test
    void persistentWithRouteReplyQueuesAndNoDefaultIsAccepted() {
        try (MqTransport transport = builder().persistent(true).build()) {
            transport.exchange(request("GetInvoice"));
        }
        assertThat(fake.sent.get(0).persistent()).isTrue();
    }

    // Exchange

    @Test
    void sendsPayloadToRouteQueue() {
        byte[] reply;
        try (MqTransport transport = builder().build()) {
            reply = transport.exchange(request("GetInvoice"));
        }
        assertThat(reply).containsExactly(0xF1);
        MqRequest sent = fake.sent.get(0);
        assertThat(sent.requestQueue()).isEqualTo("INVOICE.REQUEST");
        assertThat(sent.replyQueue()).isEqualTo("INVOICE.REPLY");
        assertThat(sent.payload()).containsExactly(PAYLOAD);
        assertThat(sent.correlationId()).isNull();
        assertThat(sent.timeToLive()).isEqualTo(Duration.ofSeconds(30));
        assertThat(sent.persistent()).isFalse();
    }

    @Test
    void receivesWithMessageIdAsCorrelation() {
        try (MqTransport transport = builder().timeout(Duration.ofSeconds(5)).build()) {
            transport.exchange(request("GetInvoice"));
        }
        assertThat(fake.sent.get(0).timeToLive()).isEqualTo(Duration.ofSeconds(5));
        assertThat(fake.received).containsExactly(
            new FakeConnector.Received("INVOICE.REPLY", "ID:1", Duration.ofSeconds(5)));
    }

    @Test
    void correlationIdModeGeneratesId() {
        try (MqTransport transport = builder().correlation(MqCorrelation.CORRELATION_ID).build()) {
            transport.exchange(request("GetInvoice"));
            transport.exchange(request("GetInvoice"));
        }
        String first = fake.sent.get(0).correlationId();
        String second = fake.sent.get(1).correlationId();
        assertThat(first).matches("ID:[0-9A-F]{48}");
        assertThat(second).matches("ID:[0-9A-F]{48}").isNotEqualTo(first);
        assertThat(fake.received).extracting(FakeConnector.Received::correlationId)
            .containsExactly(first, second);
    }

    @Test
    void routeReplyQueueWins() {
        try (MqTransport transport = builder().defaultReplyQueue("ORDERS.REPLY").build()) {
            transport.exchange(request("GetInvoice"));
        }
        assertThat(fake.received.get(0).replyQueue()).isEqualTo("INVOICE.REPLY");
    }

    @Test
    void defaultReplyQueueUsed() {
        try (MqTransport transport = builder().route("GetCustomerOrders", "ORDERS.REQUEST")
                .defaultReplyQueue("ORDERS.REPLY").build()) {
            transport.exchange(request("GetCustomerOrders"));
        }
        assertThat(fake.sent.get(0).replyQueue()).isEqualTo("ORDERS.REPLY");
        assertThat(fake.received.get(0).replyQueue()).isEqualTo("ORDERS.REPLY");
    }

    @Test
    void temporaryQueueIsNull() {
        try (MqTransport transport = builder().route("GetCustomerOrders", "ORDERS.REQUEST").build()) {
            transport.exchange(request("GetCustomerOrders"));
        }
        assertThat(fake.sent.get(0).replyQueue()).isNull();
        assertThat(fake.received.get(0).replyQueue()).isNull();
    }

    @Test
    void nullRequestThrowsNpe() {
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // Failure translation

    @Test
    void unknownOperationThrowsOperationNotFound() {
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetShipment")))
                .isInstanceOf(OperationNotFoundException.class)
                .hasMessageContaining("GetShipment");
        }
        assertThat(fake.opened).hasValue(0);
    }

    @Test
    void openNotAuthorizedIsTransportException() {
        MqException failure = FakeConnector.failure(MqFailure.NOT_AUTHORIZED);
        fake.openFailure.set(failure);
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetInvoice")))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): connect failed (NOT_AUTHORIZED)")
                .hasCause(failure);
        }
    }

    @Test
    void openOtherFailureIsConnectionException() {
        MqException failure = FakeConnector.failure(MqFailure.OTHER);
        fake.openFailure.set(failure);
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetInvoice")))
                .isInstanceOf(ConnectionException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): connect failed (OTHER)")
                .hasCause(failure);
        }
    }

    @ParameterizedTest
    @EnumSource(value = MqFailure.class, names = {"INVALID_QUEUE", "NOT_AUTHORIZED", "OTHER"})
    void sendFailureIsTransportException(MqFailure type) {
        MqException failure = FakeConnector.failure(type);
        fake.sendFailure = failure;
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetInvoice")))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): send failed (" + type + ")")
                .hasCause(failure);
        }
        assertThat(fake.received).isEmpty();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = " ")
    void missingMessageIdFailsClosed(String messageId) {
        fake.useFixedMessageId = true;
        fake.fixedMessageId = messageId;
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetInvoice")))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): the MQ session gave no message ID");
        }
        assertThat(fake.received).isEmpty();
        assertThat(fake.closed).hasValue(1);
    }

    @Test
    void noReplyIsTimeout() {
        fake.responder = (queue, correlationId) -> null;
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetInvoice")))
                .isInstanceOf(TransportTimeoutException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): no reply after PT30S");
        }
    }

    @Test
    void notBytesIsTransportException() {
        MqException failure = FakeConnector.failure(MqFailure.NOT_BYTES);
        fake.responder = (queue, correlationId) -> {
            throw failure;
        };
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetInvoice")))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): the reply is not a bytes message"
                    + " (NOT_BYTES)")
                .hasCause(failure);
        }
    }

    @Test
    void receiveFailureIsTransportException() {
        MqException failure = FakeConnector.failure(MqFailure.OTHER);
        fake.responder = (queue, correlationId) -> {
            throw failure;
        };
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(request("GetInvoice")))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): receive failed (OTHER)")
                .hasCause(failure);
        }
    }
}
