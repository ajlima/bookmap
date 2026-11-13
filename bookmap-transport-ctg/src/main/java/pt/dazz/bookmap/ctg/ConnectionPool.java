package pt.dazz.bookmap.ctg;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.TransportException;

/**
 * A bounded pool of ECI connections. A semaphore limits the number of connections in use. A
 * queue holds the free connections. The pool is thread-safe.
 */
final class ConnectionPool implements AutoCloseable {

    private final EciConnector connector;
    private final Semaphore permits;
    private final Duration waitTimeout;
    private final Deque<EciConnection> free = new ArrayDeque<>();
    private boolean closed;

    ConnectionPool(EciConnector connector, int maxConnections, Duration waitTimeout) {
        this.connector = connector;
        this.permits = new Semaphore(maxConnections, true);
        this.waitTimeout = waitTimeout;
    }

    /**
     * Gives a free connection, or opens a new one. Waits for a maximum of the wait timeout when
     * all connections are in use.
     *
     * @return a connection. Give it back with {@link #release} or {@link #discard}.
     * @throws ConnectionException if no connection is free in time, or if a new connection
     *     cannot be opened
     * @throws TransportException if the pool is closed, or if the thread is interrupted
     */
    EciConnection acquire() {
        checkOpen();
        try {
            if (!permits.tryAcquire(waitTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new ConnectionException("No free CTG connection after "
                    + waitTimeout.toMillis() + " ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Interrupted while waiting for a CTG connection", e);
        }
        EciConnection connection = takeFree();
        if (connection != null) {
            return connection;
        }
        boolean opened = false;
        try {
            EciConnection opening = connector.open();
            opened = true;
            return opening;
        } catch (IOException e) {
            throw new ConnectionException("Cannot open a connection to the CTG gateway: "
                + e.getMessage(), e);
        } finally {
            // Also for an unchecked failure, for example a missing ctgclient.jar.
            if (!opened) {
                permits.release();
            }
        }
    }

    private synchronized void checkOpen() {
        if (closed) {
            throw new TransportException("The CTG transport is closed");
        }
    }

    private synchronized EciConnection takeFree() {
        return free.poll();
    }

    /**
     * Gives back a connection that is still correct. If the pool is closed, closes the
     * connection.
     *
     * @param connection the connection
     */
    void release(EciConnection connection) {
        synchronized (this) {
            if (!closed) {
                free.push(connection);
                permits.release();
                return;
            }
        }
        closeQuietly(connection);
        permits.release();
    }

    /**
     * Closes a connection that is not correct, for example after a network failure.
     *
     * @param connection the connection
     */
    void discard(EciConnection connection) {
        closeQuietly(connection);
        permits.release();
    }

    /** Closes all free connections. A connection in use is closed when it comes back. */
    @Override
    public void close() {
        List<EciConnection> toClose;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            toClose = new ArrayList<>(free);
            free.clear();
        }
        toClose.forEach(ConnectionPool::closeQuietly);
    }

    private static void closeQuietly(EciConnection connection) {
        try {
            connection.close();
        } catch (IOException e) {
            // The connection is not used again. A failure to close it changes nothing.
        }
    }
}
