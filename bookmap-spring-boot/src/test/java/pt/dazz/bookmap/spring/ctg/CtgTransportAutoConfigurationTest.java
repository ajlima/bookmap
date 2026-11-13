package pt.dazz.bookmap.spring.ctg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ibm.ctg.client.JavaGateway;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.Order;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.ctg.CtgTransport;
import pt.dazz.bookmap.ctg.EciCall;
import pt.dazz.bookmap.ctg.EciConnection;
import pt.dazz.bookmap.ctg.EciConnector;
import pt.dazz.bookmap.ctg.EciReply;
import pt.dazz.bookmap.ctg.ibm.JavaGatewayConnector;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;
import pt.dazz.bookmap.spring.BookmapAutoConfiguration;

class CtgTransportAutoConfigurationTest {

    private static final String ROUTE = "bookmap.transport.ctg.routes[GetInvoice].program=PGMINV01";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CtgTransportAutoConfiguration.class,
            BookmapAutoConfiguration.class));

    /** A connector that records each call. A call can wait for a latch. */
    static class RecordingConnector implements EciConnector {
        final List<EciCall> calls = new CopyOnWriteArrayList<>();
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        @Override
        public EciConnection open() {
            return new EciConnection() {
                @Override
                public EciReply call(EciCall call) {
                    calls.add(call);
                    if (release != null) {
                        entered.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return new EciReply(0, null, call.commarea());
                }

                @Override
                public void close() {
                    // nothing to close
                }
            };
        }
    }

    private static byte[] exchange(CtgTransport transport, String operation) {
        return transport.exchange(new BookRequest(operation, new byte[] {1}, 4));
    }

    private ApplicationContextRunner withConnector(RecordingConnector connector) {
        return runner.withBean(EciConnector.class, () -> connector)
            .withPropertyValues("bookmap.transport.type=ctg");
    }

    @AfterEach
    void resetStub() {
        JavaGateway.reset();
    }

    @Test
    void noTypeNoTransport() {
        runner.withBean(EciConnector.class, RecordingConnector::new)
            .withPropertyValues(ROUTE)
            .run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(CtgTransport.class)
                .doesNotHaveBean(BookmapClient.class));
    }

    @Test
    void otherTypeNoCtgTransport() {
        runner.withBean(Transport.class, () -> request -> new byte[0])
            .withPropertyValues("bookmap.transport.type=http", ROUTE)
            .run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(CtgTransport.class)
                .hasSingleBean(BookmapClient.class));
    }

    @Test
    void createsTransportAndClient() {
        withConnector(new RecordingConnector()).withPropertyValues(ROUTE)
            .run(context -> assertThat(context).hasSingleBean(CtgTransport.class)
                .hasSingleBean(BookmapClient.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CTG", "Ctg"})
    void typeIsNotCaseSensitive(String type) {
        runner.withBean(EciConnector.class, RecordingConnector::new)
            .withPropertyValues("bookmap.transport.type=" + type, ROUTE)
            .run(context -> assertThat(context).hasSingleBean(CtgTransport.class));
    }

    @Test
    void applicationTransportWins() {
        Transport own = request -> new byte[0];
        withConnector(new RecordingConnector()).withPropertyValues(ROUTE)
            .withBean(Transport.class, () -> own)
            .run(context -> assertThat(context).doesNotHaveBean(CtgTransport.class)
                .getBean(Transport.class).isSameAs(own));
    }

    @Test
    void transportModuleAbsentFailsWithMessage() {
        runner.withClassLoader(new FilteredClassLoader(CtgTransport.class))
            .withPropertyValues("bookmap.transport.type=ctg", ROUTE)
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.type=ctg, but no Transport bean exists. Add "
                    + "bookmap-transport-ctg and the IBM library ctgclient.jar to the class path."));
    }

    @Test
    void propertiesReachTheCall() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(
                "bookmap.transport.ctg.server=CICSPROD",
                "bookmap.transport.ctg.default-transaction=TX00",
                "bookmap.transport.ctg.userid=ORDUSER",
                "bookmap.transport.ctg.password=secret",
                "bookmap.transport.ctg.timeout=20s",
                ROUTE,
                "bookmap.transport.ctg.routes[GetCustomerOrders].program=PGMORD01",
                "bookmap.transport.ctg.routes[GetCustomerOrders].transaction=TX01")
            .run(context -> {
                CtgTransport transport = context.getBean(CtgTransport.class);
                exchange(transport, "GetInvoice");
                exchange(transport, "GetCustomerOrders");
                EciCall invoice = connector.calls.get(0);
                assertThat(invoice.server()).isEqualTo("CICSPROD");
                assertThat(invoice.userid()).isEqualTo("ORDUSER");
                assertThat(invoice.password()).isEqualTo("secret");
                assertThat(invoice.program()).isEqualTo("PGMINV01");
                assertThat(invoice.transaction()).isEqualTo("TX00");
                assertThat(invoice.timeoutSeconds()).isEqualTo(20);
                EciCall orders = connector.calls.get(1);
                assertThat(orders.program()).isEqualTo("PGMORD01");
                assertThat(orders.transaction()).isEqualTo("TX01");
            });
    }

    @Test
    void defaultsReachTheCall() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(ROUTE)
            .run(context -> {
                exchange(context.getBean(CtgTransport.class), "GetInvoice");
                EciCall call = connector.calls.get(0);
                assertThat(call.server()).isNull();
                assertThat(call.userid()).isNull();
                assertThat(call.password()).isNull();
                assertThat(call.transaction()).isNull();
                assertThat(call.timeoutSeconds()).isEqualTo(30);
            });
    }

    @Test
    void useridWithoutPasswordIsApplied() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(ROUTE, "bookmap.transport.ctg.userid=ORDUSER")
            .run(context -> {
                exchange(context.getBean(CtgTransport.class), "GetInvoice");
                assertThat(connector.calls.get(0).userid()).isEqualTo("ORDUSER");
                assertThat(connector.calls.get(0).password()).isNull();
            });
    }

    @Test
    void maxConnectionsAndWaitTimeoutAreApplied() {
        RecordingConnector connector = new RecordingConnector();
        connector.entered = new CountDownLatch(1);
        connector.release = new CountDownLatch(1);
        withConnector(connector).withPropertyValues(ROUTE,
                "bookmap.transport.ctg.max-connections=1",
                "bookmap.transport.ctg.connection-wait-timeout=0s")
            .run(context -> {
                CtgTransport transport = context.getBean(CtgTransport.class);
                Thread holder = new Thread(() -> exchange(transport, "GetInvoice"));
                holder.start();
                assertThat(connector.entered.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    assertThatThrownBy(() -> exchange(transport, "GetInvoice"))
                        .isInstanceOf(ConnectionException.class);
                } finally {
                    connector.release.countDown();
                    holder.join();
                }
            });
    }

    @Test
    void routeKeyKeepsCaseWithoutBrackets() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector)
            .withPropertyValues("bookmap.transport.ctg.routes.GetInvoice.program=PGMINV01")
            .run(context -> {
                exchange(context.getBean(CtgTransport.class), "GetInvoice");
                assertThat(connector.calls).hasSize(1);
            });
    }

    @Test
    void routeWithoutProgramFails() {
        withConnector(new RecordingConnector())
            .withPropertyValues("bookmap.transport.ctg.routes[GetInvoice].transaction=TX01")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.ctg.routes[GetInvoice].program is not set."));
    }

    @Test
    void noRouteFails() {
        withConnector(new RecordingConnector())
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("Add at least one route"));
    }

    @Test
    void passwordWithoutUseridFails() {
        withConnector(new RecordingConnector())
            .withPropertyValues(ROUTE, "bookmap.transport.ctg.password=secret")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.ctg.password is set, but "
                    + "bookmap.transport.ctg.userid is not set.")
                .message().doesNotContain("secret"));
    }

    @Test
    void invalidTimeoutFails() {
        withConnector(new RecordingConnector())
            .withPropertyValues(ROUTE, "bookmap.transport.ctg.timeout=1500ms")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customizersRunInOrderAfterProperties() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(ROUTE)
            .withUserConfiguration(Customizers.class)
            .run(context -> {
                exchange(context.getBean(CtgTransport.class), "GetInvoice");
                assertThat(connector.calls.get(0).program()).isEqualTo("PGMSECND");
            });
    }

    /** Two customizers. The second one in the order sets the last value. */
    static class Customizers {
        @org.springframework.context.annotation.Bean
        @Order(2)
        CtgTransportBuilderCustomizer second() {
            return builder -> builder.route("GetInvoice", "PGMSECND");
        }

        @org.springframework.context.annotation.Bean
        @Order(1)
        CtgTransportBuilderCustomizer first() {
            return builder -> builder.route("GetInvoice", "PGMFIRST");
        }
    }

    @Test
    void closesTransportWithContext() {
        withConnector(new RecordingConnector()).withPropertyValues(ROUTE)
            .run(context -> {
                CtgTransport transport = context.getBean(CtgTransport.class);
                context.close();
                assertThatThrownBy(() -> exchange(transport, "GetInvoice"))
                    .isInstanceOf(TransportException.class);
            });
    }

    @Test
    void javaGatewayConnectorFromProperties() {
        runner.withPropertyValues("bookmap.transport.type=ctg", ROUTE,
                "bookmap.transport.ctg.url=tcp://ctg.example.com",
                "bookmap.transport.ctg.port=2007",
                "bookmap.transport.ctg.socket-connect-timeout=5s",
                "bookmap.transport.ctg.protocol-properties[ssl.keyring]=/keys/ring.kdb")
            .run(context -> {
                context.getBean(EciConnector.class).open().close();
                JavaGateway gateway = JavaGateway.last;
                assertThat(gateway.url()).isEqualTo("tcp://ctg.example.com");
                assertThat(gateway.port()).isEqualTo(2007);
                assertThat(gateway.socketConnectTimeout()).isEqualTo(5000);
                assertThat(gateway.protocolProperties().getProperty("ssl.keyring"))
                    .isEqualTo("/keys/ring.kdb");
            });
    }

    @Test
    void javaGatewayConnectorDefaults() {
        runner.withPropertyValues("bookmap.transport.type=ctg", ROUTE,
                "bookmap.transport.ctg.url=tcp://ctg.example.com")
            .run(context -> {
                assertThat(context.getBean(EciConnector.class)).isInstanceOf(JavaGatewayConnector.class);
                context.getBean(EciConnector.class).open().close();
                assertThat(JavaGateway.last.port()).isEqualTo(2006);
                assertThat(JavaGateway.last.socketConnectTimeout()).isEqualTo(10000);
                assertThat(JavaGateway.last.protocolProperties()).isEmpty();
            });
    }

    @Test
    void missingUrlFails() {
        runner.withPropertyValues("bookmap.transport.type=ctg", ROUTE)
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("Set the url of the gateway"));
    }

    @Test
    void ibmLibraryAbsentFailsWithMessage() {
        runner.withClassLoader(new FilteredClassLoader(JavaGateway.class))
            .withPropertyValues("bookmap.transport.type=ctg", ROUTE,
                "bookmap.transport.ctg.url=tcp://ctg.example.com")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("No EciConnector bean. Add the IBM library ctgclient.jar, "
                    + "or declare an EciConnector bean."));
    }

    @Test
    void applicationConnectorWins() {
        RecordingConnector connector = new RecordingConnector();
        withConnector(connector).withPropertyValues(ROUTE,
                "bookmap.transport.ctg.url=tcp://ctg.example.com")
            .run(context -> assertThat(context).getBean(EciConnector.class).isSameAs(connector));
    }
}
