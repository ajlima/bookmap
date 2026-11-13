package pt.dazz.bookmap.spring.ctg;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the CTG transport, with the prefix {@code bookmap.transport.ctg}.
 *
 * <p>The Spring Boot configuration processor copies the text of each {@code @param} tag into the
 * configuration metadata. Do not use inline Javadoc tags in the {@code @param} tags.
 *
 * @param url The URL of the Gateway daemon, for example tcp://ctg.example.com or
 *     ssl://ctg.example.com. The value local: selects local mode. Mandatory for the connector of the
 *     IBM library.
 * @param port The port of the Gateway daemon.
 * @param socketConnectTimeout The time limit to connect the socket to the Gateway daemon.
 * @param protocolProperties The protocol properties of the connection, for example the key ring for
 *     TLS. Write each key in brackets, for example protocol-properties[ssl.keyring].
 * @param server The name of the CICS server in the CTG configuration. Without a value, CTG uses its
 *     default server.
 * @param defaultTransaction The transaction for a route without a transaction, with 1 to 4
 *     characters. Without a value, CICS uses the mirror transaction.
 * @param userid The user ID for CICS. Without a value, the transport sends no credentials.
 * @param password The password for CICS. Set it with an environment variable or a secret store, not
 *     in a file in the source repository. A password needs a userid.
 * @param timeout The ECI timeout, in whole seconds. The value 0s means no timeout.
 * @param maxConnections The maximum number of open gateway connections. The value must be 1 or more.
 * @param connectionWaitTimeout The maximum wait for a free connection when all connections are in
 *     use.
 * @param routes The route of each operation. The key is the operation name, for example
 *     routes[GetInvoice].program. An environment variable cannot give a route. At least one route.
 */
@ConfigurationProperties("bookmap.transport.ctg")
public record CtgTransportProperties(
        String url,
        @DefaultValue("2006") int port,
        @DefaultValue("10s") Duration socketConnectTimeout,
        Map<String, String> protocolProperties,
        String server,
        String defaultTransaction,
        String userid,
        String password,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue("10") int maxConnections,
        @DefaultValue("30s") Duration connectionWaitTimeout,
        Map<String, Route> routes) {

    /**
     * Replaces a missing map with an empty map.
     */
    public CtgTransportProperties {
        protocolProperties = protocolProperties == null ? Map.of() : Map.copyOf(protocolProperties);
        routes = routes == null ? Map.of() : Map.copyOf(routes);
    }

    /**
     * The CICS program and the transaction of one operation.
     *
     * @param program The name of the CICS program, with 1 to 8 characters. Mandatory.
     * @param transaction The transaction, with 1 to 4 characters. Without a value, the transport uses
     *     the default transaction.
     */
    public record Route(String program, String transaction) {
    }

    /**
     * Shows the settings. The password is hidden.
     *
     * @return the settings, with {@code password=******} when a password is set
     */
    @Override
    public String toString() {
        return "CtgTransportProperties[url=" + url + ", port=" + port
            + ", socketConnectTimeout=" + socketConnectTimeout
            + ", protocolProperties=" + protocolProperties + ", server=" + server
            + ", defaultTransaction=" + defaultTransaction + ", userid=" + userid
            + ", password=" + (password == null ? "null" : "******")
            + ", timeout=" + timeout + ", maxConnections=" + maxConnections
            + ", connectionWaitTimeout=" + connectionWaitTimeout + ", routes=" + routes + "]";
    }
}
