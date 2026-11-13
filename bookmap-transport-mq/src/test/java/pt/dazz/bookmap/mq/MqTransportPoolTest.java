package pt.dazz.bookmap.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;

class MqTransportPoolTest {

    private static final BookRequest REQUEST = new BookRequest("GetInvoice", new byte[] {1}, 10);

    private final FakeConnector fake = new FakeConnector();

    private MqTransport.Builder builder() {
        return MqTransport.builder().connector(fake)
            .route("GetInvoice", "INVOICE.REQUEST", "INVOICE.REPLY");
    }

    /** Blocks each receive call until {@code release} opens. {@code entered} counts the calls. */
    private void blockReceive(CountDownLatch entered, CountDownLatch release) {
        fake.responder = (queue, correlationId) -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new byte[] {2};
        };
    }

    @Test
    void sessionIsReused() {
        try (MqTransport transport = builder().build()) {
            transport.exchange(REQUEST);
            transport.exchange(REQUEST);
        }
        assertThat(fake.opened).hasValue(1);
        assertThat(fake.closed).hasValue(1);
    }

    @Test
    void sessionReusedAfterTimeout() {
        fake.responder = (queue, correlationId) -> null;
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isInstanceOf(TransportTimeoutException.class);
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isInstanceOf(TransportTimeoutException.class);
        }
        assertThat(fake.opened).hasValue(1);
    }

    @Test
    void timeoutOnTemporaryQueueDiscardsSession() {
        fake.responder = (queue, correlationId) -> null;
        try (MqTransport transport = MqTransport.builder().connector(fake)
                .route("GetInvoice", "INVOICE.REQUEST").build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isInstanceOf(TransportTimeoutException.class);
            assertThat(fake.closed).hasValue(1);
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isInstanceOf(TransportTimeoutException.class);
        }
        assertThat(fake.opened).hasValue(2);
        assertThat(fake.closed).hasValue(2);
    }

    @Test
    void closeContinuesAfterRuntimeException() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        blockReceive(entered, release);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        MqTransport transport = builder().maxSessions(2).build();
        try {
            Future<byte[]> first = executor.submit(() -> transport.exchange(REQUEST));
            Future<byte[]> second = executor.submit(() -> transport.exchange(REQUEST));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        fake.closeRuntimeFailure = new IllegalStateException("provider failure");

        transport.close();

        assertThat(fake.closed).hasValue(2);
    }

    @Test
    void hugeSessionWaitTimeoutWorks() {
        try (MqTransport transport = builder()
                .sessionWaitTimeout(Duration.ofSeconds(Long.MAX_VALUE)).build()) {
            assertThat(transport.exchange(REQUEST)).containsExactly(0xF1);
        }
    }

    @Test
    void failedSessionIsClosedAndReplaced() {
        fake.sendFailure = FakeConnector.failure(MqFailure.OTHER);
        fake.closeFailure = FakeConnector.failure(MqFailure.OTHER);
        try (MqTransport transport = builder().build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(TransportException.class);
            assertThat(fake.closed).hasValue(1);
            fake.sendFailure = null;
            assertThat(transport.exchange(REQUEST)).containsExactly(0xF1);
        }
        assertThat(fake.opened).hasValue(2);
    }

    @Test
    void waitTimeoutGivesConnectionException() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockReceive(entered, release);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (MqTransport transport = builder().maxSessions(1)
                .sessionWaitTimeout(Duration.ofMillis(50)).build()) {
            Future<byte[]> first = executor.submit(() -> transport.exchange(REQUEST));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isInstanceOf(ConnectionException.class)
                .hasMessage("GetInvoice (INVOICE.REQUEST): no free MQ session after PT0.05S");

            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly(2);
        } finally {
            executor.shutdownNow();
        }
        assertThat(fake.opened).hasValue(1);
    }

    @Test
    void interruptGivesTransportExceptionAndKeepsFlag() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockReceive(entered, release);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (MqTransport transport = builder().maxSessions(1).build()) {
            Future<byte[]> first = executor.submit(() -> transport.exchange(REQUEST));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> transport.exchange(REQUEST))
                    .isExactlyInstanceOf(TransportException.class)
                    .hasMessage("GetInvoice (INVOICE.REQUEST): interrupted while waiting"
                        + " for an MQ session")
                    .hasCauseInstanceOf(InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void permitReleasedAfterOpenFailure() {
        fake.openFailure.set(FakeConnector.failure(MqFailure.OTHER));
        try (MqTransport transport = builder().maxSessions(1)
                .sessionWaitTimeout(Duration.ZERO).build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isInstanceOf(ConnectionException.class);
            assertThat(transport.exchange(REQUEST)).containsExactly(0xF1);
        }
    }

    @Test
    void closeClosesFreeSessions() {
        fake.closeFailure = FakeConnector.failure(MqFailure.OTHER);
        MqTransport transport = builder().build();
        transport.exchange(REQUEST);
        assertThat(fake.closed).hasValue(0);

        transport.close();

        assertThat(fake.closed).hasValue(1);
    }

    @Test
    void exchangeAfterCloseThrows() {
        MqTransport transport = builder().maxSessions(1).build();
        transport.close();
        assertThatThrownBy(() -> transport.exchange(REQUEST))
            .isExactlyInstanceOf(TransportException.class)
            .hasMessage("GetInvoice (INVOICE.REQUEST): the transport is closed");
        assertThatThrownBy(() -> transport.exchange(REQUEST))
            .isExactlyInstanceOf(TransportException.class);
        assertThat(fake.opened).hasValue(0);
    }

    @Test
    void secondCloseDoesNothing() {
        MqTransport transport = builder().build();
        transport.exchange(REQUEST);
        transport.close();
        transport.close();
        assertThat(fake.closed).hasValue(1);
    }

    @Test
    void sessionInUseAtCloseIsClosedAfterExchange() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockReceive(entered, release);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MqTransport transport = builder().build();
            Future<byte[]> first = executor.submit(() -> transport.exchange(REQUEST));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            transport.close();
            assertThat(fake.closed).hasValue(0);
            release.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly(2);
            assertThat(fake.closed).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentExchangesUseAtMostMaxSessions() throws Exception {
        CountDownLatch entered = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        blockReceive(entered, release);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try (MqTransport transport = builder().maxSessions(3).build()) {
            List<Future<byte[]>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> transport.exchange(REQUEST)));
            }
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (Future<byte[]> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).containsExactly(2);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(fake.opened.get()).isLessThanOrEqualTo(3);
        assertThat(fake.maxInUse.get()).isLessThanOrEqualTo(3);
    }
}
