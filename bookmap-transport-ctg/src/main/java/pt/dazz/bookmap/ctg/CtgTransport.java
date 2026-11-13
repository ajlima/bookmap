package pt.dazz.bookmap.ctg;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import pt.dazz.bookmap.exception.BookmapException;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

/**
 * Transport that calls a CICS program through the CICS Transaction Gateway (ECI). The request
 * bytes go to the program in a COMMAREA. The transport returns the COMMAREA that the program
 * gives back.
 *
 * <p>Each operation name has a route to a program and a transaction. The transport keeps a
 * bounded pool of gateway connections. It is thread-safe. Close it to close the connections.
 *
 * <p>The transport translates each ECI failure to a BookMap exception:
 * <ul>
 *   <li>No route, or the abend {@code AEI0} (CICS does not know the program):
 *       {@link OperationNotFoundException}.</li>
 *   <li>Another transaction abend: {@link RemoteAbendException}.</li>
 *   <li>An ECI timeout: {@link TransportTimeoutException}.</li>
 *   <li>No connection to the gateway or to CICS: {@link ConnectionException}.</li>
 *   <li>Any other failure: {@link TransportException}.</li>
 * </ul>
 */
public final class CtgTransport implements Transport, AutoCloseable {

    /** The maximum COMMAREA length in bytes. CTG can send this length with all protocols. */
    public static final int MAX_COMMAREA_LENGTH = 32_500;

    /** The abend code of a program that CICS does not know ({@code PGMIDERR}). */
    private static final String PROGRAM_NOT_FOUND_ABEND = "AEI0";

    private final Map<String, CtgRoute> routes;
    private final String server;
    private final String defaultTransaction;
    private final String userid;
    private final String password;
    private final int timeoutSeconds;
    private final ConnectionPool pool;

    private CtgTransport(Builder builder) {
        this.routes = Map.copyOf(builder.routes);
        this.server = builder.server;
        this.defaultTransaction = builder.defaultTransaction;
        this.userid = builder.userid;
        this.password = builder.password;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.pool = new ConnectionPool(builder.connector, builder.maxConnections,
            builder.connectionWaitTimeout);
    }

    /**
     * Creates a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public byte[] exchange(BookRequest request) {
        String operation = request.operation();
        CtgRoute route = routes.get(operation);
        if (route == null) {
            throw new OperationNotFoundException("No CTG route for the operation " + operation);
        }
        int commareaLength = Math.max(request.payload().length, request.maxResponseLength());
        if (commareaLength > MAX_COMMAREA_LENGTH) {
            throw new TransportException(prefix(operation, route) + ": the COMMAREA length "
                + commareaLength + " is more than the maximum of " + MAX_COMMAREA_LENGTH + " bytes");
        }
        String transaction = route.transaction() == null ? defaultTransaction : route.transaction();
        EciCall call = new EciCall(server, userid, password, route.program(), transaction,
            request.payload(), commareaLength, timeoutSeconds);
        EciConnection connection = pool.acquire();
        EciReply reply;
        boolean replied = false;
        try {
            reply = connection.call(call);
            replied = true;
        } catch (IOException | RuntimeException e) {
            throw new TransportException(prefix(operation, route)
                + ": the ECI call failed. The program can be complete in CICS.", e);
        } finally {
            // Do not use a connection again after a failure.
            if (replied) {
                pool.release(connection);
            } else {
                pool.discard(connection);
            }
        }
        if (reply.returnCode() != EciReturnCodes.NO_ERROR) {
            throw translate(operation, route, reply);
        }
        return reply.commarea();
    }

    private static String prefix(String operation, CtgRoute route) {
        return operation + " (" + route.program() + ")";
    }

    private static BookmapException translate(String operation, CtgRoute route, EciReply reply) {
        int code = reply.returnCode();
        String message = prefix(operation, route) + ": ECI return code " + code
            + " (" + EciReturnCodes.name(code) + ")";
        return switch (code) {
            case EciReturnCodes.ERR_TRANSACTION_ABEND -> {
                String abend = reply.abendCode();
                String abendMessage = message + ", abend " + abend;
                yield PROGRAM_NOT_FOUND_ABEND.equals(abend)
                    ? new OperationNotFoundException(abendMessage)
                    : new RemoteAbendException(abend, abendMessage);
            }
            case EciReturnCodes.ERR_REQUEST_TIMEOUT, EciReturnCodes.ERR_RESPONSE_TIMEOUT ->
                new TransportTimeoutException(message);
            case EciReturnCodes.ERR_NO_CICS, EciReturnCodes.ERR_CICS_DIED,
                 EciReturnCodes.ERR_RESOURCE_SHORTAGE, EciReturnCodes.ERR_NO_SESSIONS,
                 EciReturnCodes.ERR_UNKNOWN_SERVER, EciReturnCodes.ERR_NO_SYSTEMS,
                 EciReturnCodes.ERR_MAX_SYSTEMS, EciReturnCodes.ERR_MAX_SESSIONS ->
                new ConnectionException(message);
            default -> new TransportException(message);
        };
    }

    /**
     * Closes the free connections. A connection in use is closed when its exchange ends. An
     * exchange after this call throws {@link TransportException}. A second call does nothing.
     */
    @Override
    public void close() {
        pool.close();
    }

    /** Builds a {@link CtgTransport}. */
    public static final class Builder {

        private EciConnector connector;
        private final Map<String, CtgRoute> routes = new HashMap<>();
        private String server;
        private String defaultTransaction;
        private String userid;
        private String password;
        private int timeoutSeconds = 30;
        private int maxConnections = 10;
        private Duration connectionWaitTimeout = Duration.ofSeconds(30);

        private Builder() {
        }

        /**
         * Sets the connector. This value is mandatory.
         *
         * @param connector the connector, for example a {@code JavaGatewayConnector}
         * @return this builder
         */
        public Builder connector(EciConnector connector) {
            this.connector = Objects.requireNonNull(connector, "connector");
            return this;
        }

        /**
         * Adds a route that uses the default transaction. A route for the same operation is
         * replaced.
         *
         * @param operation the operation name
         * @param program the name of the CICS program, 1 to 8 characters
         * @return this builder
         */
        public Builder route(String operation, String program) {
            return route(operation, program, null);
        }

        /**
         * Adds a route. A route for the same operation is replaced.
         *
         * @param operation the operation name
         * @param program the name of the CICS program, 1 to 8 characters
         * @param transaction the name of the CICS transaction, 1 to 4 characters, or
         *     {@code null} for the default transaction
         * @return this builder
         */
        public Builder route(String operation, String program, String transaction) {
            routes.put(Objects.requireNonNull(operation, "operation"),
                new CtgRoute(program, transaction));
            return this;
        }

        /**
         * Adds all routes of a map. A route for the same operation is replaced.
         *
         * @param routes the routes, by operation name
         * @return this builder
         */
        public Builder routes(Map<String, CtgRoute> routes) {
            Objects.requireNonNull(routes, "routes").forEach((operation, route) ->
                this.routes.put(Objects.requireNonNull(operation, "operation"),
                    Objects.requireNonNull(route, "route")));
            return this;
        }

        /**
         * Sets the name of the CICS server in the CTG configuration.
         *
         * @param server the server name, or {@code null} for the default server of CTG
         * @return this builder
         */
        public Builder server(String server) {
            this.server = server;
            return this;
        }

        /**
         * Sets the transaction for a route without a transaction.
         *
         * @param transaction the transaction name, 1 to 4 characters, or {@code null} for the
         *     mirror transaction of CICS
         * @return this builder
         */
        public Builder defaultTransaction(String transaction) {
            if (transaction != null) {
                CtgRoute.checkName("transaction", transaction, 4);
            }
            this.defaultTransaction = transaction;
            return this;
        }

        /**
         * Sets the CICS user ID and password.
         *
         * @param userid the user ID, or {@code null} for no credentials
         * @param password the password, or {@code null}
         * @return this builder
         */
        public Builder credentials(String userid, String password) {
            this.userid = userid;
            this.password = password;
            return this;
        }

        /**
         * Sets the ECI timeout. The default is 30 seconds.
         *
         * @param timeout the timeout in whole seconds, from 0 to 32767. {@link Duration#ZERO}
         *     means no ECI timeout.
         * @return this builder
         * @throws IllegalArgumentException if the value is negative, has a fraction of a second,
         *     or is more than 32767 seconds
         */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.toNanosPart() != 0
                    || timeout.getSeconds() > Short.MAX_VALUE) {
                throw new IllegalArgumentException("The ECI timeout must be whole seconds from 0 to "
                    + Short.MAX_VALUE + ": " + timeout);
            }
            this.timeoutSeconds = (int) timeout.getSeconds();
            return this;
        }

        /**
         * Sets the maximum number of open gateway connections. The default is 10.
         *
         * @param maxConnections the maximum, 1 or more
         * @return this builder
         */
        public Builder maxConnections(int maxConnections) {
            if (maxConnections < 1) {
                throw new IllegalArgumentException("maxConnections must be 1 or more: "
                    + maxConnections);
            }
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * Sets the maximum wait for a free connection when all connections are in use. The
         * default is 30 seconds.
         *
         * @param timeout the maximum wait
         * @return this builder
         */
        public Builder connectionWaitTimeout(Duration timeout) {
            this.connectionWaitTimeout = Objects.requireNonNull(timeout, "connectionWaitTimeout");
            return this;
        }

        /**
         * Builds the transport. It opens no connection.
         *
         * @return a new transport
         * @throws IllegalStateException if the connector or the routes are not set
         */
        public CtgTransport build() {
            if (connector == null) {
                throw new IllegalStateException("Set the connector");
            }
            if (routes.isEmpty()) {
                throw new IllegalStateException("Add at least one route");
            }
            return new CtgTransport(this);
        }
    }
}
