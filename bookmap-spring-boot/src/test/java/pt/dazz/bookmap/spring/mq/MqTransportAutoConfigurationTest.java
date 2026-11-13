package pt.dazz.bookmap.spring.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.mq.MqConnector;
import pt.dazz.bookmap.mq.MqRequest;
import pt.dazz.bookmap.mq.MqSession;
import pt.dazz.bookmap.mq.MqTransport;
import pt.dazz.bookmap.mq.jms.JmsConnector;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;
import pt.dazz.bookmap.spring.BookmapAutoConfiguration;

class MqTransportAutoConfigurationTest {

    private static final String P = "bookmap.transport.mq.";
    private static final String ROUTE = P + "routes[GetInvoice].request-queue=INVOICE.REQUEST";
    private static final byte[] REPLY = {7, 7};

    private static EmbeddedBroker broker;
    private static ExecutorService server;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MqTransportAutoConfiguration.class,
            BookmapAutoConfiguration.class));

    @BeforeAll
    static void startBroker() throws Exception {
        broker = new EmbeddedBroker();
        server = Executors.newSingleThreadExecutor();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        server.shutdownNow();
        broker.close();
    }

    /** One receive call of the fake connector. */
    record Receive(String replyQueue, String correlationId, Duration waitTime) { }

    /** A connector that records each request and each receive, and gives a reply. */
    static class RecordingConnector implements MqConnector {
        final List<MqRequest> requests = new CopyOnWriteArrayList<>();
        final List<Receive> receives = new CopyOnWriteArrayList<>();
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        @Override
        public MqSession open() {
            return new MqSession() {
                @Override
                public String send(MqRequest request) {
                    requests.add(request);
                    if (release != null) {
                        entered.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return "ID:1";
                }

                @Override
                public byte[] receive(String replyQueue, String correlationId, Duration wait) {
                    receives.add(new Receive(replyQueue, correlationId, wait));
                    return REPLY;
                }

                @Override
                public void close() {
                    // nothing to close
                }
            };
        }
    }

    private static byte[] exchange(Transport transport) {
        return transport.exchange(new BookRequest("GetInvoice", new byte[] {1}, 4));
    }

    private ApplicationContextRunner withConnector(RecordingConnector connector) {
        return runner.withBean(MqConnector.class, () -> connector)
            .withPropertyValues("bookmap.transport.type=mq");
    }

    @Test
    void noTypeNoTransport() {
        runner.withBean(MqConnector.class, RecordingConnector::new).withPropertyValues(ROUTE)
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MqTransport.class));
    }

    @Test
    void createsTransportAndClient() {
        withConnector(new RecordingConnector()).withPropertyValues(ROUTE)
            .run(context -> assertThat(context).hasSingleBean(MqTransport.class)
                .hasSingleBean(BookmapClient.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MQ", "Mq"})
    void typeIsNotCaseSensitive(String type) {
        runner.withBean(MqConnector.class, RecordingConnector::new)
            .withPropertyValues("bookmap.transport.type=" + type, ROUTE)
            .run(context -> assertThat(context).hasSingleBean(MqTransport.class));
    }

    @Test
    void applicationTransportWins() {
        runner.withBean(Transport.class, () -> request -> new byte[0])
            .withPropertyValues("bookmap.transport.type=mq")
            .run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(MqTransport.class)
                .doesNotHaveBean(MqConnector.class));
    }

    @Test
    void transportModuleAbsentFailsWithMessage() {
        runner.withClassLoader(new FilteredClassLoader(MqTransport.class))
            .withPropertyValues("bookmap.transport.type=mq", ROUTE)
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.type=mq, but no Transport bean exists. Add "
                    + "bookmap-transport-mq to the class path."));
    }

    @Test
    void propertiesReachTheRequest() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(
                ROUTE,
                P + "routes[GetInvoice].reply-queue=INVOICE.REPLY",
                P + "routes[GetOrders].request-queue=ORDERS.REQUEST",
                P + "default-reply-queue=ORDERS.REPLY",
                P + "correlation=correlation-id",
                P + "timeout=20s",
                P + "persistent=true")
            .run(context -> {
                MqTransport transport = context.getBean(MqTransport.class);
                assertThat(exchange(transport)).containsExactly(REPLY);
                transport.exchange(new BookRequest("GetOrders", new byte[] {1}, 4));
                MqRequest invoice = connector.requests.get(0);
                assertThat(invoice.requestQueue()).isEqualTo("INVOICE.REQUEST");
                assertThat(invoice.replyQueue()).isEqualTo("INVOICE.REPLY");
                assertThat(invoice.correlationId()).startsWith("ID:");
                assertThat(invoice.timeToLive()).isEqualTo(Duration.ofSeconds(20));
                assertThat(invoice.persistent()).isTrue();
                assertThat(connector.receives.get(0))
                    .isEqualTo(new Receive("INVOICE.REPLY", invoice.correlationId(), Duration.ofSeconds(20)));
                assertThat(connector.requests.get(1).replyQueue()).isEqualTo("ORDERS.REPLY");
            });
    }

    @Test
    void defaultsReachTheRequest() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(ROUTE)
            .run(context -> {
                exchange(context.getBean(MqTransport.class));
                MqRequest request = connector.requests.get(0);
                assertThat(request.replyQueue()).isNull();
                assertThat(request.correlationId()).isNull();
                assertThat(request.timeToLive()).isEqualTo(Duration.ofSeconds(30));
                assertThat(request.persistent()).isFalse();
                assertThat(connector.receives.get(0).correlationId()).isEqualTo("ID:1");
            });
    }

    @Test
    void persistentWithTemporaryQueueFails() {
        withConnector(new RecordingConnector()).withPropertyValues(ROUTE, P + "persistent=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void maxSessionsAndWaitTimeoutAreApplied() {
        RecordingConnector connector = new RecordingConnector();
        connector.entered = new CountDownLatch(1);
        connector.release = new CountDownLatch(1);
        withConnector(connector).withPropertyValues(ROUTE, P + "max-sessions=1",
                P + "session-wait-timeout=0s")
            .run(context -> {
                MqTransport transport = context.getBean(MqTransport.class);
                Thread holder = new Thread(() -> exchange(transport));
                holder.start();
                assertThat(connector.entered.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    assertThatThrownBy(() -> exchange(transport)).isInstanceOf(ConnectionException.class);
                } finally {
                    connector.release.countDown();
                    holder.join();
                }
            });
    }

    @Test
    void routeWithoutRequestQueueFails() {
        withConnector(new RecordingConnector())
            .withPropertyValues(P + "routes[GetInvoice].reply-queue=INVOICE.REPLY")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.mq.routes[GetInvoice].request-queue is not set."));
    }

    @Test
    void noRouteFails() {
        withConnector(new RecordingConnector())
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("No route is set"));
    }

    @Test
    void passwordWithoutUserFails() {
        runner.withBean(ConnectionFactory.class, broker::connectionFactory)
            .withPropertyValues("bookmap.transport.type=mq", ROUTE, P + "password=secret")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.mq.password is set, but bookmap.transport.mq.user is not set.")
                .message().doesNotContain("secret"));
    }

    @Test
    void customizersRunInOrderAfterProperties() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(ROUTE)
            .withUserConfiguration(Customizers.class)
            .run(context -> {
                exchange(context.getBean(MqTransport.class));
                assertThat(connector.requests.get(0).requestQueue()).isEqualTo("SECOND");
            });
    }

    /** Two customizers. The second one in the order sets the last route. */
    static class Customizers {
        @Bean
        @Order(2)
        MqTransportBuilderCustomizer second() {
            return builder -> builder.route("GetInvoice", "SECOND");
        }

        @Bean
        @Order(1)
        MqTransportBuilderCustomizer first() {
            return builder -> builder.route("GetInvoice", "FIRST");
        }
    }

    @Test
    void closesTransportWithContext() {
        withConnector(new RecordingConnector()).withPropertyValues(ROUTE)
            .run(context -> {
                MqTransport transport = context.getBean(MqTransport.class);
                context.close();
                assertThatThrownBy(() -> exchange(transport)).isInstanceOf(TransportException.class);
            });
    }

    @Test
    void applicationConnectorWins() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(ROUTE)
            .withBean(ConnectionFactory.class, broker::connectionFactory)
            .run(context -> assertThat(context).getBean(MqConnector.class).isSameAs(connector));
    }

    @Test
    void noConnectionFactoryFailsWithMessage() {
        runner.withPropertyValues("bookmap.transport.type=mq", ROUTE)
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("No MqConnector bean. Declare one jakarta.jms.ConnectionFactory bean, for "
                    + "example with mq-jms-spring-boot-starter, or declare an MqConnector bean."));
    }

    @Test
    void twoConnectionFactoriesWithoutPrimaryFail() {
        runner.withBean("first", ConnectionFactory.class, broker::connectionFactory)
            .withBean("second", ConnectionFactory.class, broker::connectionFactory)
            .withPropertyValues("bookmap.transport.type=mq", ROUTE)
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageStartingWith("No MqConnector bean."));
    }

    @Test
    void primaryConnectionFactoryIsUsed() {
        runner.withUserConfiguration(TwoFactories.class)
            .withPropertyValues("bookmap.transport.type=mq", ROUTE)
            .run(context -> assertThat(context).getBean(MqConnector.class).isInstanceOf(JmsConnector.class));
    }

    /** Two factories. One is primary. */
    static class TwoFactories {
        @Bean
        @Primary
        ConnectionFactory main() {
            return broker.connectionFactory();
        }

        @Bean
        ConnectionFactory other() {
            return broker.connectionFactory();
        }
    }

    /** Puts one reply for the next request on the queue, with the message ID as correlation ID. */
    private static Future<byte[]> serveOne(String requestQueue) {
        return server.submit(() -> {
            try (Connection connection = broker.connectionFactory().createConnection()) {
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(session.createQueue(requestQueue));
                Message request = consumer.receive(5000);
                if (request == null) {
                    return null;
                }
                BytesMessage reply = session.createBytesMessage();
                reply.writeBytes(REPLY);
                reply.setJMSCorrelationID(request.getJMSMessageID());
                session.createProducer(request.getJMSReplyTo()).send(reply);
                BytesMessage bytes = (BytesMessage) request;
                byte[] body = new byte[(int) bytes.getBodyLength()];
                bytes.readBytes(body);
                return body;
            }
        });
    }

    @Test
    void jmsConnectorWithCredentialsExchangesThroughTheBroker() {
        runner.withBean(ConnectionFactory.class, broker::connectionFactory)
            .withPropertyValues("bookmap.transport.type=mq", P + "routes[GetInvoice].request-queue=Q.CRED",
                P + "user=" + EmbeddedBroker.USER, P + "password=" + EmbeddedBroker.PASSWORD,
                P + "mq-native-messages=false")
            .run(context -> {
                Future<byte[]> request = serveOne("Q.CRED");
                assertThat(exchange(context.getBean(MqTransport.class))).containsExactly(REPLY);
                assertThat(request.get(5, TimeUnit.SECONDS)).containsExactly(1);
            });
    }

    @Test
    void jmsConnectorWithoutCredentialsExchangesThroughTheBroker() {
        runner.withBean(ConnectionFactory.class, broker::connectionFactory)
            .withPropertyValues("bookmap.transport.type=mq", P + "routes[GetInvoice].request-queue=Q.ANON",
                P + "mq-native-messages=false")
            .run(context -> {
                serveOne("Q.ANON");
                assertThat(exchange(context.getBean(MqTransport.class))).containsExactly(REPLY);
            });
    }

    @Test
    void wrongPasswordReachesTheBroker() {
        runner.withBean(ConnectionFactory.class, broker::connectionFactory)
            .withPropertyValues("bookmap.transport.type=mq", ROUTE, P + "user=" + EmbeddedBroker.USER,
                P + "password=wrong", P + "mq-native-messages=false")
            .run(context -> assertThatThrownBy(() -> exchange(context.getBean(MqTransport.class)))
                .isInstanceOf(TransportException.class));
    }

    @Test
    void mqNativeMessagesIsOnByDefault() {
        runner.withBean(ConnectionFactory.class, broker::connectionFactory)
            .withPropertyValues("bookmap.transport.type=mq", P + "routes[GetInvoice].request-queue=Q.NATIVE",
                P + "timeout=1s")
            .run(context -> {
                Future<byte[]> request = serveOne("Q.NATIVE");
                assertThatThrownBy(() -> exchange(context.getBean(MqTransport.class)))
                    .isInstanceOf(TransportException.class);
                assertThat(request.get(10, TimeUnit.SECONDS)).isNull();
            });
    }
}
