package pt.dazz.bookmap.mq.jms;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.InvalidDestinationException;
import jakarta.jms.JMSException;
import jakarta.jms.JMSSecurityException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.mq.MqException;
import pt.dazz.bookmap.mq.MqFailure;
import pt.dazz.bookmap.mq.MqRequest;
import pt.dazz.bookmap.mq.MqSession;

class JmsConnectorTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final byte[] INVOICE = "INV-2026-0042".getBytes(US_ASCII);
    private static final byte[] PAID = "PAID".getBytes(US_ASCII);

    private static EmbeddedBroker broker;
    private static ActiveMQConnectionFactory factory;

    private final ExecutorService server = Executors.newSingleThreadExecutor();

    /** Makes the reply message of the test server. */
    @FunctionalInterface
    private interface ReplyMaker {
        Message reply(Session session, Message request) throws JMSException;
    }

    @BeforeAll
    static void startBroker() throws Exception {
        broker = new EmbeddedBroker();
        factory = broker.connectionFactory();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        factory.close();
        broker.close();
    }

    @AfterEach
    void stopServer() {
        server.shutdownNow();
    }

    private static JmsConnector connector() {
        return JmsConnector.builder().connectionFactory(factory).mqNativeMessages(false).build();
    }

    private static Message bytesReply(Session session, Message request) throws JMSException {
        BytesMessage reply = session.createBytesMessage();
        reply.writeBytes(PAID);
        return reply;
    }

    /**
     * Starts a server that gets one request and puts one reply to the reply-to queue. The reply
     * gets the message ID of the request as its correlation ID, or the correlation ID of the
     * request. The future gives the request.
     */
    private Future<Message> serveOne(String requestQueue, boolean copyCorrelationId,
                                     ReplyMaker maker) {
        return server.submit(() -> {
            try (Connection connection = factory.createConnection()) {
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(session.createQueue(requestQueue));
                Message request = consumer.receive(WAIT.toMillis());
                Message reply = maker.reply(session, request);
                reply.setJMSCorrelationID(copyCorrelationId
                    ? request.getJMSCorrelationID() : request.getJMSMessageID());
                session.createProducer(request.getJMSReplyTo()).send(reply);
                return request;
            }
        });
    }

    private static MqRequest request(String requestQueue, String replyQueue, String correlationId) {
        return new MqRequest(requestQueue, replyQueue, INVOICE, correlationId,
            Duration.ofSeconds(30), false);
    }

    private static byte[] body(Message message) throws JMSException {
        BytesMessage bytes = (BytesMessage) message;
        bytes.reset();
        byte[] data = new byte[(int) bytes.getBodyLength()];
        bytes.readBytes(data);
        return data;
    }

    @Test
    void roundTripWithNamedReplyQueue() throws Exception {
        Future<Message> served = serveOne("INVOICE.REQUEST.1", false, JmsConnectorTest::bytesReply);
        try (MqSession session = connector().open()) {
            String messageId = session.send(request("INVOICE.REQUEST.1", "INVOICE.REPLY.1", null));
            assertThat(session.receive("INVOICE.REPLY.1", messageId, WAIT)).containsExactly(PAID);
        }
        Message request = served.get(10, TimeUnit.SECONDS);
        assertThat(request).isInstanceOf(BytesMessage.class);
        assertThat(body(request)).containsExactly(INVOICE);
        assertThat(request.getJMSReplyTo().toString()).contains("INVOICE.REPLY.1");
    }

    @Test
    void temporaryQueueRoundTrip() throws Exception {
        serveOne("INVOICE.REQUEST.2", false, JmsConnectorTest::bytesReply);
        try (MqSession session = connector().open()) {
            String messageId = session.send(request("INVOICE.REQUEST.2", null, null));
            assertThat(session.receive(null, messageId, WAIT)).containsExactly(PAID);
            // The second exchange uses the same temporary queue.
            serveOne("INVOICE.REQUEST.2", false, JmsConnectorTest::bytesReply);
            String second = session.send(request("INVOICE.REQUEST.2", null, null));
            assertThat(session.receive(null, second, WAIT)).containsExactly(PAID);
        }
    }

    @Test
    void correlationIdMode() throws Exception {
        String correlationId = "ID:" + "0A".repeat(24);
        Future<Message> served = serveOne("INVOICE.REQUEST.3", true, JmsConnectorTest::bytesReply);
        try (MqSession session = connector().open()) {
            session.send(request("INVOICE.REQUEST.3", "INVOICE.REPLY.3", correlationId));
            assertThat(session.receive("INVOICE.REPLY.3", correlationId, WAIT))
                .containsExactly(PAID);
        }
        assertThat(served.get(10, TimeUnit.SECONDS).getJMSCorrelationID()).isEqualTo(correlationId);
    }

    @Test
    void otherReplyStaysOnQueue() throws Exception {
        try (Connection connection = factory.createConnection()) {
            Session other = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            BytesMessage foreign = other.createBytesMessage();
            foreign.writeBytes(new byte[] {9});
            foreign.setJMSCorrelationID("ID:OTHER");
            other.createProducer(other.createQueue("ORDERS.REPLY.4")).send(foreign);

            serveOne("ORDERS.REQUEST.4", false, JmsConnectorTest::bytesReply);
            try (MqSession session = connector().open()) {
                String messageId = session.send(request("ORDERS.REQUEST.4", "ORDERS.REPLY.4", null));
                assertThat(session.receive("ORDERS.REPLY.4", messageId, WAIT))
                    .containsExactly(PAID);
            }

            connection.start();
            QueueBrowser browser = other.createBrowser(other.createQueue("ORDERS.REPLY.4"));
            assertThat(Collections.list(browser.getEnumeration()))
                .singleElement()
                .satisfies(message -> assertThat(((Message) message).getJMSCorrelationID())
                    .isEqualTo("ID:OTHER"));
        }
    }

    @Test
    void receiveReturnsNullAfterWait() throws Exception {
        try (MqSession session = connector().open()) {
            assertThat(session.receive("INVOICE.REPLY.5", "ID:NONE", Duration.ofMillis(100)))
                .isNull();
        }
    }

    @Test
    void textReplyIsNotBytes() throws Exception {
        serveOne("INVOICE.REQUEST.6", false, (session, request) -> session.createTextMessage("PAID"));
        try (MqSession session = connector().open()) {
            String messageId = session.send(request("INVOICE.REQUEST.6", "INVOICE.REPLY.6", null));
            assertThatThrownBy(() -> session.receive("INVOICE.REPLY.6", messageId, WAIT))
                .isInstanceOfSatisfying(MqException.class,
                    e -> assertThat(e.failure()).isEqualTo(MqFailure.NOT_BYTES));
        }
    }

    @Test
    void emptyPayloadAndReply() throws Exception {
        Future<Message> served = serveOne("INVOICE.REQUEST.7", false, (session, request) -> {
            BytesMessage reply = session.createBytesMessage();
            reply.writeBytes(new byte[0]);
            return reply;
        });
        try (MqSession session = connector().open()) {
            String messageId = session.send(new MqRequest("INVOICE.REQUEST.7", "INVOICE.REPLY.7",
                new byte[0], null, Duration.ofSeconds(30), false));
            assertThat(session.receive("INVOICE.REPLY.7", messageId, WAIT)).isEmpty();
        }
        assertThat(body(served.get(10, TimeUnit.SECONDS))).isEmpty();
    }

    @Test
    void timeToLiveAndDeliveryMode() throws Exception {
        Future<Message> first = serveOne("INVOICE.REQUEST.8", false, JmsConnectorTest::bytesReply);
        try (MqSession session = connector().open()) {
            String id = session.send(new MqRequest("INVOICE.REQUEST.8", "INVOICE.REPLY.8",
                INVOICE, null, Duration.ofSeconds(30), true));
            session.receive("INVOICE.REPLY.8", id, WAIT);
            Message request = first.get(10, TimeUnit.SECONDS);
            assertThat(request.getJMSExpiration()).isPositive();
            assertThat(request.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
            assertThat(request.getJMSPriority()).isEqualTo(4);

            Future<Message> second = serveOne("INVOICE.REQUEST.8", false,
                JmsConnectorTest::bytesReply);
            id = session.send(new MqRequest("INVOICE.REQUEST.8", "INVOICE.REPLY.8",
                INVOICE, null, Duration.ZERO, false));
            session.receive("INVOICE.REPLY.8", id, WAIT);
            request = second.get(10, TimeUnit.SECONDS);
            assertThat(request.getJMSExpiration()).isZero();
            assertThat(request.getJMSDeliveryMode()).isEqualTo(DeliveryMode.NON_PERSISTENT);
        }
    }

    @Test
    void credentialsAreUsed() throws Exception {
        JmsConnector good = JmsConnector.builder().connectionFactory(factory)
            .credentials(EmbeddedBroker.USER, EmbeddedBroker.PASSWORD).build();
        good.open().close();

        JmsConnector bad = JmsConnector.builder().connectionFactory(factory)
            .credentials(EmbeddedBroker.USER, "wrong").build();
        assertThatThrownBy(bad::open)
            .isInstanceOfSatisfying(MqException.class,
                e -> assertThat(e.failure()).isEqualTo(MqFailure.NOT_AUTHORIZED))
            .hasCauseInstanceOf(JMSSecurityException.class);

        JmsConnector reset = JmsConnector.builder().connectionFactory(factory)
            .credentials(EmbeddedBroker.USER, "wrong").credentials(null, null).build();
        reset.open().close();
    }

    @Test
    void failureAfterCloseIsMqException() throws Exception {
        MqSession session = connector().open();
        session.close();
        assertThatThrownBy(() -> session.send(request("INVOICE.REQUEST.10", "INVOICE.REPLY.10", null)))
            .isInstanceOfSatisfying(MqException.class,
                e -> assertThat(e.failure()).isEqualTo(MqFailure.OTHER));
        assertThatThrownBy(() -> session.receive("INVOICE.REPLY.10", "ID:X", WAIT))
            .isInstanceOfSatisfying(MqException.class,
                e -> assertThat(e.failure()).isEqualTo(MqFailure.OTHER));
        session.close();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void openFailureClosesConnection(boolean failClose) {
        StubConnectionFactory stub = new StubConnectionFactory(null, failClose);
        JmsConnector connector = JmsConnector.builder().connectionFactory(stub).build();
        assertThatThrownBy(connector::open)
            .isInstanceOfSatisfying(MqException.class, e -> {
                assertThat(e.failure()).isEqualTo(MqFailure.OTHER);
                assertThat(e.getCause().getSuppressed()).hasSize(failClose ? 1 : 0);
            })
            .hasMessageContaining("no session");
        assertThat(stub.closed).isTrue();
    }

    @Test
    void closeFailureIsMqException() throws Exception {
        try (Connection delegate = factory.createConnection()) {
            JmsConnector connector = JmsConnector.builder()
                .connectionFactory(new StubConnectionFactory(delegate, true)).build();
            MqSession session = connector.open();
            assertThatThrownBy(session::close)
                .isInstanceOfSatisfying(MqException.class,
                    e -> assertThat(e.failure()).isEqualTo(MqFailure.OTHER))
                .hasMessageContaining("close failed too");
        }
    }

    @Test
    void failureOfMapsTypes() {
        assertThat(JmsConnector.failureOf(new JMSSecurityException("2035")))
            .isEqualTo(MqFailure.NOT_AUTHORIZED);
        assertThat(JmsConnector.failureOf(new InvalidDestinationException("2085")))
            .isEqualTo(MqFailure.INVALID_QUEUE);
        assertThat(JmsConnector.failureOf(new JMSException("2009")))
            .isEqualTo(MqFailure.OTHER);
    }

    @Test
    void queueNameForms() {
        JmsConnector ibm = JmsConnector.builder().connectionFactory(factory).build();
        assertThat(ibm.queueName("ORDERS.REQUEST"))
            .isEqualTo("queue:///ORDERS.REQUEST?targetClient=1");
        assertThat(ibm.queueName("queue://QM1/ORDERS.REQUEST"))
            .isEqualTo("queue://QM1/ORDERS.REQUEST");
        assertThat(connector().queueName("ORDERS.REQUEST")).isEqualTo("ORDERS.REQUEST");
    }

    @Test
    void builderRules() {
        assertThatThrownBy(() -> JmsConnector.builder().build())
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JmsConnector.builder().connectionFactory(null))
            .isInstanceOf(NullPointerException.class);
    }
}
