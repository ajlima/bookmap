package pt.dazz.bookmap.mq;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

/**
 * A {@link Transport} that calls a program through IBM MQ request and reply queues.
 *
 * <p>The transport puts the request bytes on the request queue of the operation. Then it waits
 * for the reply with the correct correlation ID on the reply queue, and returns the reply bytes.
 * It keeps a pool of sessions. The transport is thread-safe.
 *
 * <p>The transport translates each failure to a BookMap exception:
 * <ul>
 *   <li>No route for the operation: {@link OperationNotFoundException}.</li>
 *   <li>No connection, or no free session in time: {@link ConnectionException}. The request was
 *       not sent.</li>
 *   <li>No reply before the timeout: {@link TransportTimeoutException}.</li>
 *   <li>Another failure: {@link TransportException}. The request can be on the queue, or
 *       complete on the server.</li>
 * </ul>
 */
public final class MqTransport implements Transport, AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_SESSION_WAIT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_SESSIONS = 10;
    private static final int CORRELATION_ID_BYTES = 24;
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private final MqConnector connector;
    private final Map<String, MqRoute> routes;
    private final String defaultReplyQueue;
    private final MqCorrelation correlation;
    private final Duration timeout;
    private final boolean persistent;
    private final Duration sessionWaitTimeout;
    private final Semaphore permits;
    private final Deque<MqSession> freeSessions = new ArrayDeque<>();
    private final Object lock = new Object();
    private final SecureRandom random = new SecureRandom();
    private boolean closed;

    private MqTransport(Builder builder) {
        this.connector = builder.connector;
        this.routes = Map.copyOf(builder.routes);
        this.defaultReplyQueue = builder.defaultReplyQueue;
        this.correlation = builder.correlation;
        this.timeout = builder.timeout;
        this.persistent = builder.persistent;
        this.sessionWaitTimeout = builder.sessionWaitTimeout;
        this.permits = new Semaphore(builder.maxSessions);
    }

    /**
     * Creates a builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Sends the request to the request queue of the operation and waits for the reply.
     *
     * @param request the request
     * @return the reply bytes
     * @throws OperationNotFoundException if no route exists for the operation
     * @throws ConnectionException if the transport cannot connect, or no session is free in time
     * @throws TransportTimeoutException if no reply comes before the timeout
     * @throws TransportException for another failure, or after {@link #close()}
     */
    @Override
    public byte[] exchange(BookRequest request) {
        Objects.requireNonNull(request, "request");
        String operation = request.operation();
        MqRoute route = routes.get(operation);
        if (route == null) {
            throw new OperationNotFoundException(operation + ": no MQ route for the operation");
        }
        String prefix = operation + " (" + route.requestQueue() + "): ";
        String replyQueue = route.replyQueue() != null ? route.replyQueue() : defaultReplyQueue;
        MqSession session = acquire(prefix);
        boolean reusable = false;
        try {
            String correlationId = correlation == MqCorrelation.CORRELATION_ID
                ? newCorrelationId() : null;
            String messageId;
            try {
                messageId = session.send(new MqRequest(route.requestQueue(), replyQueue,
                    request.payload(), correlationId, timeout, persistent));
            } catch (MqException e) {
                throw new TransportException(prefix + "send failed (" + e.failure() + ")", e);
            }
            String replyCorrelationId = correlationId != null ? correlationId : messageId;
            if (replyCorrelationId == null || replyCorrelationId.isBlank()) {
                // Without a correlation ID, the transport could get the reply of another request.
                throw new TransportException(prefix + "the MQ session gave no message ID");
            }
            byte[] reply;
            try {
                reply = session.receive(replyQueue, replyCorrelationId, timeout);
            } catch (MqException e) {
                String text = e.failure() == MqFailure.NOT_BYTES
                    ? "the reply is not a bytes message" : "receive failed";
                throw new TransportException(prefix + text + " (" + e.failure() + ")", e);
            }
            if (reply == null) {
                // A late reply stays on the reply queue. On a temporary queue, it stays for the
                // life of the session, so the transport closes the session to delete the queue.
                reusable = replyQueue != null;
                throw new TransportTimeoutException(prefix + "no reply after " + timeout);
            }
            reusable = true;
            return reply;
        } finally {
            release(session, reusable);
        }
    }

    /**
     * Closes the free sessions. A session in use closes when its exchange ends. After this
     * call, {@link #exchange(BookRequest)} throws {@link TransportException}. A second call does
     * nothing.
     */
    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            MqSession session;
            while ((session = freeSessions.pollFirst()) != null) {
                closeQuietly(session);
            }
        }
    }

    private MqSession acquire(String prefix) {
        try {
            // The conversion saturates: a very long wait does not overflow.
            long waitNanos = TimeUnit.NANOSECONDS.convert(sessionWaitTimeout);
            if (!permits.tryAcquire(waitNanos, TimeUnit.NANOSECONDS)) {
                throw new ConnectionException(
                    prefix + "no free MQ session after " + sessionWaitTimeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException(prefix + "interrupted while waiting for an MQ session", e);
        }
        boolean acquired = false;
        try {
            MqSession session;
            synchronized (lock) {
                if (closed) {
                    throw new TransportException(prefix + "the transport is closed");
                }
                session = freeSessions.pollFirst();
            }
            if (session == null) {
                session = open(prefix);
            }
            acquired = true;
            return session;
        } finally {
            if (!acquired) {
                permits.release();
            }
        }
    }

    private MqSession open(String prefix) {
        try {
            return connector.open();
        } catch (MqException e) {
            String message = prefix + "connect failed (" + e.failure() + ")";
            if (e.failure() == MqFailure.NOT_AUTHORIZED) {
                throw new TransportException(message, e);
            }
            throw new ConnectionException(message, e);
        }
    }

    private void release(MqSession session, boolean reusable) {
        try {
            synchronized (lock) {
                if (reusable && !closed) {
                    freeSessions.addFirst(session);
                    return;
                }
            }
            closeQuietly(session);
        } finally {
            permits.release();
        }
    }

    private static void closeQuietly(MqSession session) {
        try {
            session.close();
        } catch (MqException | RuntimeException e) {
            // The session is not used again. A failure to close it changes nothing.
        }
    }

    private String newCorrelationId() {
        byte[] bytes = new byte[CORRELATION_ID_BYTES];
        random.nextBytes(bytes);
        return "ID:" + HEX.formatHex(bytes);
    }

    /**
     * Builds an {@link MqTransport}.
     */
    public static final class Builder {

        private MqConnector connector;
        private final Map<String, MqRoute> routes = new HashMap<>();
        private String defaultReplyQueue;
        private MqCorrelation correlation = MqCorrelation.MESSAGE_ID;
        private Duration timeout = DEFAULT_TIMEOUT;
        private boolean persistent;
        private int maxSessions = DEFAULT_MAX_SESSIONS;
        private Duration sessionWaitTimeout = DEFAULT_SESSION_WAIT;

        private Builder() {
        }

        /**
         * Sets the connector. This value is mandatory.
         *
         * @param connector the connector
         * @return this builder
         */
        public Builder connector(MqConnector connector) {
            this.connector = Objects.requireNonNull(connector, "connector");
            return this;
        }

        /**
         * Adds a route with the default reply queue. A route replaces an earlier route for the
         * same operation.
         *
         * @param operation the operation name
         * @param requestQueue the request queue
         * @return this builder
         */
        public Builder route(String operation, String requestQueue) {
            routes.put(Objects.requireNonNull(operation, "operation"),
                new MqRoute(requestQueue, null));
            return this;
        }

        /**
         * Adds a route. A route replaces an earlier route for the same operation.
         *
         * @param operation the operation name
         * @param requestQueue the request queue
         * @param replyQueue the reply queue
         * @return this builder
         */
        public Builder route(String operation, String requestQueue, String replyQueue) {
            routes.put(Objects.requireNonNull(operation, "operation"),
                new MqRoute(requestQueue, Objects.requireNonNull(replyQueue, "replyQueue")));
            return this;
        }

        /**
         * Adds all routes of a map. The key is the operation name.
         *
         * @param routes the routes
         * @return this builder
         */
        public Builder routes(Map<String, MqRoute> routes) {
            routes.forEach((operation, route) -> this.routes.put(
                Objects.requireNonNull(operation, "operation"),
                Objects.requireNonNull(route, "route")));
            return this;
        }

        /**
         * Sets the reply queue for a route without a reply queue. The default value is
         * {@code null}: the transport uses a temporary queue.
         *
         * @param replyQueue the reply queue, or {@code null}
         * @return this builder
         * @throws IllegalArgumentException if the name is blank
         */
        public Builder defaultReplyQueue(String replyQueue) {
            if (replyQueue != null && replyQueue.isBlank()) {
                throw new IllegalArgumentException("The default reply queue is blank");
            }
            this.defaultReplyQueue = replyQueue;
            return this;
        }

        /**
         * Sets the correlation mode. The default value is {@link MqCorrelation#MESSAGE_ID}.
         *
         * @param correlation the correlation mode
         * @return this builder
         */
        public Builder correlation(MqCorrelation correlation) {
            this.correlation = Objects.requireNonNull(correlation, "correlation");
            return this;
        }

        /**
         * Sets the maximum wait for the reply. It is also the expiry of the request message.
         * The default value is 30 seconds.
         *
         * @param timeout the timeout, more than zero
         * @return this builder
         * @throws IllegalArgumentException if the value is zero or negative
         */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("The timeout must be more than zero: " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Sends persistent request messages. The default value is {@code false}.
         *
         * @param persistent {@code true} for persistent messages
         * @return this builder
         */
        public Builder persistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        /**
         * Sets the maximum number of open sessions. The default value is 10.
         *
         * @param maxSessions the maximum, at least 1
         * @return this builder
         * @throws IllegalArgumentException if the value is less than 1
         */
        public Builder maxSessions(int maxSessions) {
            if (maxSessions < 1) {
                throw new IllegalArgumentException("maxSessions must be at least 1: " + maxSessions);
            }
            this.maxSessions = maxSessions;
            return this;
        }

        /**
         * Sets the maximum wait for a free session when all sessions are in use. The default
         * value is 30 seconds.
         *
         * @param sessionWaitTimeout the wait, zero or more
         * @return this builder
         * @throws IllegalArgumentException if the value is negative
         */
        public Builder sessionWaitTimeout(Duration sessionWaitTimeout) {
            Objects.requireNonNull(sessionWaitTimeout, "sessionWaitTimeout");
            if (sessionWaitTimeout.isNegative()) {
                throw new IllegalArgumentException(
                    "The session wait timeout is negative: " + sessionWaitTimeout);
            }
            this.sessionWaitTimeout = sessionWaitTimeout;
            return this;
        }

        /**
         * Creates the transport. The transport opens no session here.
         *
         * @return the transport
         * @throws IllegalStateException if the connector or the routes are missing, or if
         *     persistent messages go with a temporary reply queue
         */
        public MqTransport build() {
            if (connector == null) {
                throw new IllegalStateException("The connector is not set");
            }
            if (routes.isEmpty()) {
                throw new IllegalStateException("No route is set");
            }
            if (persistent && defaultReplyQueue == null) {
                routes.forEach((operation, route) -> {
                    if (route.replyQueue() == null) {
                        throw new IllegalStateException(operation + ": a temporary reply queue"
                            + " does not accept persistent messages. Set a reply queue.");
                    }
                });
            }
            return new MqTransport(this);
        }
    }
}
