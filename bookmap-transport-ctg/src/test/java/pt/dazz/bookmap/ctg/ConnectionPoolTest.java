package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.TransportException;

class ConnectionPoolTest {

    private final FakeConnector connector = new FakeConnector();

    private ConnectionPool pool(int max, Duration wait) {
        return new ConnectionPool(connector, max, wait);
    }

    @Test
    void opensConnectionOnFirstAcquire() {
        ConnectionPool pool = pool(2, Duration.ofSeconds(1));
        assertThat(pool.acquire()).isNotNull();
        assertThat(connector.opened()).isEqualTo(1);
    }

    @Test
    void reusesReleasedConnection() {
        ConnectionPool pool = pool(2, Duration.ofSeconds(1));
        EciConnection first = pool.acquire();
        pool.release(first);
        assertThat(pool.acquire()).isSameAs(first);
        assertThat(connector.opened()).isEqualTo(1);
    }

    @Test
    void discardedConnectionIsNotReused() {
        ConnectionPool pool = pool(1, Duration.ofSeconds(1));
        EciConnection first = pool.acquire();
        pool.discard(first);
        assertThat(connector.connections().get(0).closed()).isTrue();
        assertThat(pool.acquire()).isNotSameAs(first);
        assertThat(connector.opened()).isEqualTo(2);
    }

    @Test
    void discardIgnoresCloseFailure() {
        connector.failClose(new IOException("broken"));
        ConnectionPool pool = pool(1, Duration.ofMillis(50));
        pool.discard(pool.acquire());
        assertThat(pool.acquire()).isNotNull();
    }

    @Test
    void waitsForFreeConnection() throws Exception {
        ConnectionPool pool = pool(1, Duration.ofSeconds(2));
        EciConnection held = pool.acquire();
        CompletableFuture<EciConnection> waiting = CompletableFuture.supplyAsync(pool::acquire);
        Thread.sleep(50);
        assertThat(waiting).isNotDone();
        pool.release(held);
        assertThat(waiting.get(1, TimeUnit.SECONDS)).isSameAs(held);
    }

    @Test
    void failsAfterWaitTimeout() {
        ConnectionPool pool = pool(1, Duration.ofMillis(50));
        pool.acquire();
        assertThatThrownBy(pool::acquire).isExactlyInstanceOf(ConnectionException.class)
            .hasMessageContaining("50 ms");
    }

    @Test
    void openFailureIsConnectionException() {
        IOException failure = new IOException("refused");
        connector.failOpen(failure);
        ConnectionPool pool = pool(1, Duration.ofMillis(50));
        assertThatThrownBy(pool::acquire).isExactlyInstanceOf(ConnectionException.class)
            .hasCause(failure);
        connector.failOpen(null);
        assertThat(pool.acquire()).isNotNull();
    }

    @Test
    void interruptWhileWaitingIsTransportException() throws Exception {
        ConnectionPool pool = pool(1, Duration.ofSeconds(10));
        pool.acquire();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                pool.acquire();
            } catch (RuntimeException e) {
                thrown.set(e);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
            done.countDown();
        });
        Thread.sleep(50);
        waiter.interrupt();
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(thrown.get()).isExactlyInstanceOf(TransportException.class)
            .hasCauseInstanceOf(InterruptedException.class);
        assertThat(interrupted).isTrue();
    }

    @Test
    void acquireAfterCloseFails() {
        ConnectionPool pool = pool(1, Duration.ofSeconds(1));
        pool.close();
        assertThatThrownBy(pool::acquire).isExactlyInstanceOf(TransportException.class)
            .hasMessage("The CTG transport is closed");
    }

    @Test
    void closeClosesFreeConnections() {
        ConnectionPool pool = pool(2, Duration.ofSeconds(1));
        EciConnection first = pool.acquire();
        EciConnection second = pool.acquire();
        pool.release(first);
        pool.release(second);
        pool.close();
        assertThat(connector.connections()).allMatch(FakeConnector.FakeConnection::closed);
    }

    @Test
    void closeIgnoresFailures() {
        connector.failClose(new IOException("broken"));
        ConnectionPool pool = pool(1, Duration.ofSeconds(1));
        pool.release(pool.acquire());
        pool.close();
        assertThat(connector.connections().get(0).closed()).isTrue();
    }

    @Test
    void secondCloseDoesNothing() {
        ConnectionPool pool = pool(1, Duration.ofSeconds(1));
        pool.release(pool.acquire());
        pool.close();
        pool.close();
        assertThat(connector.connections()).hasSize(1);
    }

    @Test
    void releaseAfterCloseClosesConnection() {
        ConnectionPool pool = pool(1, Duration.ofSeconds(1));
        EciConnection inUse = pool.acquire();
        pool.close();
        assertThat(connector.connections().get(0).closed()).isFalse();
        pool.release(inUse);
        assertThat(connector.connections().get(0).closed()).isTrue();
    }
}
