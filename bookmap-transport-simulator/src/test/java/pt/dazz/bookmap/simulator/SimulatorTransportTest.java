package pt.dazz.bookmap.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;

class SimulatorTransportTest {

    private static final Charset EBCDIC = Charset.forName("IBM037");

    private static BookRequest request(String operation, byte[] payload) {
        return new BookRequest(operation, payload, 64);
    }

    private static SimulatorTransport transportWith(String codecCharset) {
        return SimulatorTransport.builder()
            .codecRegistry(BookCodecRegistry.of(new GreetingCodec(codecCharset)))
            .build();
    }

    /** A codec for a hierarchy: {@link Greeting} and {@link PoliteGreeting}. */
    private static final class HierarchyCodec extends GreetingCodec {
        HierarchyCodec() {
            super("");
        }

        @Override
        public List<Class<? extends Greeting>> types() {
            return List.of(Greeting.class, PoliteGreeting.class);
        }
    }

    @Test
    void registerResponderAnswersWithAnotherObject() {
        SimulatorTransport transport = transportWith("");
        transport.registerResponder(Greeting.class, call -> {
            Greeting answer = new PoliteGreeting();
            answer.message = "Good morning, " + call.name;
            return answer;
        });

        byte[] response = transport.exchange(request("Greet", "Ada".getBytes(EBCDIC)));

        assertThat(response).isEqualTo("Good morning, Ada".getBytes(EBCDIC));
    }

    @Test
    void responderReturnsNull() {
        SimulatorTransport transport = transportWith("");
        transport.registerResponder(Greeting.class, call -> null);

        assertThatThrownBy(() -> transport.exchange(request("Greet", "Ada".getBytes(EBCDIC))))
            .isInstanceOf(MappingException.class)
            .hasMessage("Greet: the responder returned null");
    }

    @Test
    void registerWithASubtypeFails() {
        SimulatorTransport transport = SimulatorTransport.builder()
            .codecRegistry(BookCodecRegistry.of(new HierarchyCodec())).build();
        String message = PoliteGreeting.class.getName() + " is a subtype of "
            + Greeting.class.getName() + ". Register " + Greeting.class.getName() + ".";

        assertThatThrownBy(() -> transport.register(PoliteGreeting.class, call -> { }))
            .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        assertThatThrownBy(() -> transport.registerResponder(PoliteGreeting.class, call -> call))
            .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        transport.register(Greeting.class, call -> call.message = "Hello");
    }

    @Test
    void byteLevelOperation() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        transport.register("Echo", req -> req);

        byte[] response = transport.exchange(request("Echo", new byte[] {1, 2, 3}));

        assertThat(response).containsExactly(1, 2, 3);
    }

    @Test
    void registerReplacesAnEarlierOperation() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        transport.register("Echo", req -> new byte[] {1});
        transport.register("Echo", req -> new byte[] {2});

        assertThat(transport.exchange(request("Echo", new byte[0]))).containsExactly(2);
    }

    @Test
    void typedHandlerUsesCodecWithSimulatorCharsetWhenCodecHasNone() {
        SimulatorTransport transport = transportWith("");
        transport.register(Greeting.class, call -> call.message = "Hello, " + call.name);

        byte[] response = transport.exchange(request("Greet", "Ada".getBytes(EBCDIC)));

        assertThat(response).isEqualTo("Hello, Ada".getBytes(EBCDIC));
    }

    @Test
    void typedHandlerUsesCodecCharsetWhenPresent() {
        SimulatorTransport transport = transportWith("ISO-8859-1");
        transport.register(Greeting.class, call -> call.message = "Hello, " + call.name);

        byte[] payload = "Zoë".getBytes(StandardCharsets.ISO_8859_1);
        byte[] first = transport.exchange(request("Greet", payload));
        byte[] second = transport.exchange(request("Greet", payload));

        byte[] expected = "Hello, Zoë".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(first).isEqualTo(expected);
        assertThat(second).isEqualTo(expected);
    }

    @Test
    void builderCharsetOverridesTheDefault() {
        SimulatorTransport transport = SimulatorTransport.builder()
            .codecRegistry(BookCodecRegistry.of(new GreetingCodec("")))
            .charset(StandardCharsets.US_ASCII)
            .build();
        transport.register(Greeting.class, call -> call.message = "Hi " + call.name);

        byte[] response = transport.exchange(request("Greet", "Bo".getBytes(StandardCharsets.US_ASCII)));

        assertThat(response).isEqualTo("Hi Bo".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void invalidCodecCharsetsAreReportedAsMappingExceptions() {
        for (String name : new String[] {"NO-SUCH-CHARSET", "bad name!", "UTF-8"}) {
            SimulatorTransport transport = transportWith(name);

            assertThatThrownBy(() -> transport.register(Greeting.class, call -> { }))
                .isExactlyInstanceOf(MappingException.class)
                .hasMessage("Greet: invalid charset " + name)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unknownOperation() {
        SimulatorTransport transport = SimulatorTransport.builder().build();

        assertThatThrownBy(() -> transport.exchange(request("Missing", new byte[0])))
            .isInstanceOf(OperationNotFoundException.class)
            .hasMessageContaining("Missing");
        assertThat(transport.receivedRequests()).hasSize(1);
    }

    @Test
    void abendIsTranslated() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        SimulatedAbend abend = new SimulatedAbend("ASRA");
        transport.register("Boom", req -> { throw abend; });

        assertThatThrownBy(() -> transport.exchange(request("Boom", new byte[0])))
            .isInstanceOfSatisfying(RemoteAbendException.class, e -> {
                assertThat(e.abendCode()).isEqualTo("ASRA");
                assertThat(e.getMessage()).isEqualTo(abend.getMessage());
                assertThat(e.getCause()).isSameAs(abend);
            });
    }

    @Test
    void abendKeepsAnExplicitMessage() {
        SimulatedAbend abend = new SimulatedAbend("AEI9", "Program not found");

        assertThat(abend.abendCode()).isEqualTo("AEI9");
        assertThat(abend).hasMessage("Program not found");
        assertThat(new SimulatedAbend("ASRA").getMessage()).contains("ASRA");
    }

    @Test
    void slowOperationTimesOut() {
        SimulatorTransport transport = SimulatorTransport.builder()
            .timeout(Duration.ofMillis(100)).build();
        transport.register("Slow", req -> {
            sleepQuietly(1_000);
            return req;
        });

        assertThatThrownBy(() -> transport.exchange(request("Slow", new byte[0])))
            .isInstanceOf(TransportTimeoutException.class)
            .hasMessageContaining("Slow");
    }

    @Test
    void otherExceptionIsWrapped() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        IllegalStateException failure = new IllegalStateException("bad state");
        transport.register("Bad", req -> { throw failure; });

        assertThatThrownBy(() -> transport.exchange(request("Bad", new byte[0])))
            .isExactlyInstanceOf(TransportException.class)
            .hasCause(failure);
    }

    @Test
    void errorIsWrapped() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        AssertionError failure = new AssertionError("broken");
        transport.register("Bad", req -> { throw failure; });

        assertThatThrownBy(() -> transport.exchange(request("Bad", new byte[0])))
            .isExactlyInstanceOf(TransportException.class)
            .hasCause(failure);
    }

    @Test
    void bookmapExceptionPassesThrough() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        ConnectionException failure = new ConnectionException("link down");
        transport.register("Down", req -> { throw failure; });

        assertThatThrownBy(() -> transport.exchange(request("Down", new byte[0])))
            .isSameAs(failure);
    }

    @Test
    void timedOutHandlerIsInterrupted() throws InterruptedException {
        CountDownLatch interrupted = new CountDownLatch(1);
        SimulatorTransport transport = SimulatorTransport.builder()
            .timeout(Duration.ofMillis(100)).build();
        transport.register("Slow", req -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return req;
        });

        assertThatThrownBy(() -> transport.exchange(request("Slow", new byte[0])))
            .isInstanceOf(TransportTimeoutException.class);

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @RepeatedTest(20)
    void interruptedCallerGetsATransportException() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        transport.register("Block", req -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return req;
        });

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> transport.exchange(request("Block", new byte[0])))
                .isExactlyInstanceOf(TransportException.class)
                .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void recordsReceivedRequests() {
        SimulatorTransport transport = SimulatorTransport.builder().build();
        transport.register("Echo", req -> req);

        transport.exchange(request("Echo", new byte[] {1}));
        transport.exchange(request("Echo", new byte[] {2}));

        List<BookRequest> received = transport.receivedRequests();
        assertThat(received).hasSize(2);
        assertThat(received.get(0).payload()).containsExactly(1);
        assertThat(received.get(1).payload()).containsExactly(2);
        assertThatThrownBy(() -> received.add(request("X", new byte[0])))
            .isInstanceOf(UnsupportedOperationException.class);

        transport.clearReceivedRequests();

        assertThat(transport.receivedRequests()).isEmpty();
        assertThat(received).hasSize(2);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
