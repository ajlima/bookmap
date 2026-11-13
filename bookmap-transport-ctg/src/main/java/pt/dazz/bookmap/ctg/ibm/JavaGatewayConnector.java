package pt.dazz.bookmap.ctg.ibm;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import pt.dazz.bookmap.ctg.EciCall;
import pt.dazz.bookmap.ctg.EciConnection;
import pt.dazz.bookmap.ctg.EciConnector;
import pt.dazz.bookmap.ctg.EciReply;

/**
 * Connector for the IBM CICS Transaction Gateway client library ({@code ctgclient.jar}). Each
 * connection is one {@code com.ibm.ctg.client.JavaGateway}.
 *
 * <p>BookMap does not contain the IBM library. Add {@code ctgclient.jar} to the runtime class
 * path of the application. If it is missing, {@link #open()} throws {@link NoClassDefFoundError}.
 */
public final class JavaGatewayConnector implements EciConnector {

    private final String url;
    private final int port;
    private final int socketConnectTimeoutMillis;
    private final Properties protocolProperties;

    private JavaGatewayConnector(Builder builder) {
        this.url = builder.url;
        this.port = builder.port;
        this.socketConnectTimeoutMillis = (int) builder.socketConnectTimeout.toMillis();
        this.protocolProperties = copy(builder.protocolProperties);
    }

    /**
     * Creates a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private static Properties copy(Properties source) {
        // Copy the keys of the defaults too. putAll copies only the keys of the object itself.
        Properties copy = new Properties();
        source.stringPropertyNames().forEach(name -> copy.setProperty(name, source.getProperty(name)));
        return copy;
    }

    /**
     * Opens a connection to the Gateway daemon, or to the local client in local mode.
     *
     * @return a new connection
     * @throws IOException if the connection cannot be opened
     */
    @Override
    public EciConnection open() throws IOException {
        return new JavaGatewayConnection(new JavaGateway(url, port, socketConnectTimeoutMillis,
            copy(protocolProperties)));
    }

    /** One connection: a {@code JavaGateway}. */
    private static final class JavaGatewayConnection implements EciConnection {

        private final JavaGateway gateway;

        private JavaGatewayConnection(JavaGateway gateway) {
            this.gateway = gateway;
        }

        @Override
        public EciReply call(EciCall call) throws IOException {
            byte[] commarea = new byte[call.commareaLength()];
            System.arraycopy(call.commarea(), 0, commarea, 0, call.commarea().length);
            ECIRequest request = new ECIRequest(ECIRequest.ECI_SYNC, call.server(), call.userid(),
                call.password(), call.program(), call.transaction(), commarea,
                call.commareaLength(), ECIRequest.ECI_NO_EXTEND, 0);
            request.setECITimeout((short) call.timeoutSeconds());
            // Send only the request bytes. CICS gets a COMMAREA of the full length.
            request.setCommareaOutboundLength(call.commarea().length);
            // Get the full COMMAREA back. Without this, CTG removes the trailing X'00' bytes.
            request.setCommareaInboundLength(call.commareaLength());
            gateway.flow(request);
            return new EciReply(request.getRc(), request.Abend_Code, request.Commarea);
        }

        @Override
        public void close() throws IOException {
            gateway.close();
        }
    }

    /** Builds a {@link JavaGatewayConnector}. */
    public static final class Builder {

        private String url;
        private int port = 2006;
        private Duration socketConnectTimeout = Duration.ofSeconds(10);
        private Properties protocolProperties = new Properties();

        private Builder() {
        }

        /**
         * Sets the URL of the gateway, for example {@code tcp://ctg.example.com} or
         * {@code ssl://ctg.example.com}. Use {@code local:} for local mode. This value is
         * mandatory.
         *
         * @param url the URL
         * @return this builder
         */
        public Builder url(String url) {
            this.url = Objects.requireNonNull(url, "url");
            return this;
        }

        /**
         * Sets the port of the Gateway daemon. The default is 2006. Local mode ignores the port.
         *
         * @param port the port
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the time limit to connect the socket. The default is 10 seconds.
         *
         * @param timeout the time limit, from 0 to {@code Integer.MAX_VALUE} milliseconds
         * @return this builder
         * @throws IllegalArgumentException if the value is out of range
         */
        public Builder socketConnectTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "socketConnectTimeout");
            if (timeout.isNegative() || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException("socketConnectTimeout must be from 0 to "
                    + Integer.MAX_VALUE + " ms: " + timeout);
            }
            this.socketConnectTimeout = timeout;
            return this;
        }

        /**
         * Sets the protocol properties, for example the key ring for TLS. The builder keeps a
         * copy.
         *
         * @param properties the protocol properties
         * @return this builder
         */
        public Builder protocolProperties(Properties properties) {
            this.protocolProperties = copy(Objects.requireNonNull(properties, "protocolProperties"));
            return this;
        }

        /**
         * Builds the connector. It opens no connection.
         *
         * @return a new connector
         * @throws IllegalStateException if the URL is not set
         */
        public JavaGatewayConnector build() {
            if (url == null) {
                throw new IllegalStateException("Set the url of the gateway");
            }
            return new JavaGatewayConnector(this);
        }
    }
}
