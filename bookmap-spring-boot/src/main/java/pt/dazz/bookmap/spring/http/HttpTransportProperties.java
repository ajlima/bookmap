package pt.dazz.bookmap.spring.http;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the HTTP transport, with the prefix {@code bookmap.transport.http}.
 *
 * <p>The Spring Boot configuration processor copies the text of each {@code @param} tag into the
 * configuration metadata. Do not use inline Javadoc tags in the {@code @param} tags.
 *
 * @param baseUri The URI that resolves a relative route URI, for example
 *     https://gateway.example.com/orders/. It must be an absolute http or https URI.
 * @param headers The headers of each request, for example headers[X-Client-Id]=order-service.
 *     Do not put a secret here as a plain value. Use an environment variable or a secret store.
 * @param username The user for basic authentication. It needs a password.
 * @param password The password for basic authentication. Set it with an environment variable or a
 *     secret store, not in a file in the source repository. It needs a username.
 * @param abendHeader The name of a response header that contains an abend code, for example
 *     X-Abend-Code. Without a value, the transport does no abend check.
 * @param timeout The time limit of one exchange, from the send until the last byte of the response.
 * @param connectTimeout The time limit to connect. Without a value, the transport uses 10 seconds.
 *     Do not set it when a customizer sets an HttpClient.
 * @param ssl The TLS settings of the HTTP client.
 * @param routes The route of each operation. The key is the operation name, for example
 *     routes[GetInvoice].uri. An environment variable cannot give a route. At least one route.
 */
@ConfigurationProperties("bookmap.transport.http")
public record HttpTransportProperties(
        URI baseUri,
        Map<String, String> headers,
        String username,
        String password,
        String abendHeader,
        @DefaultValue("30s") Duration timeout,
        Duration connectTimeout,
        Ssl ssl,
        Map<String, Route> routes) {

    /**
     * Replaces a missing map or a missing TLS group with an empty value.
     */
    public HttpTransportProperties {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        ssl = ssl == null ? new Ssl(null) : ssl;
        routes = routes == null ? Map.of() : Map.copyOf(routes);
    }

    /**
     * The TLS settings.
     *
     * @param bundle The name of a Spring Boot SSL bundle (spring.ssl.bundle.*). The HTTP client
     *     uses its trust store, its key store for mutual TLS, its protocols, and its ciphers.
     */
    public record Ssl(String bundle) {
    }

    /**
     * The URI, the method, and the headers of one operation.
     *
     * @param uri The URI of the operation, absolute or relative to the base URI. Mandatory.
     * @param method The HTTP method. Without a value, the transport uses POST.
     * @param headers The headers of this route. They replace the headers of the transport with the
     *     same name.
     */
    public record Route(String uri, String method, Map<String, String> headers) {

        /**
         * Replaces a missing map with an empty map.
         */
        public Route {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        /**
         * Shows the route. The values of the headers are hidden.
         *
         * @return the route, with only the names of the headers
         */
        @Override
        public String toString() {
            return "Route[uri=" + uri + ", method=" + method + ", headers=" + new TreeSet<>(headers.keySet()) + "]";
        }
    }

    /**
     * Shows the settings. The password and the values of the headers are hidden: a header can
     * contain a token.
     *
     * @return the settings, with {@code password=******} when a password is set, and only the
     *     names of the headers
     */
    @Override
    public String toString() {
        return "HttpTransportProperties[baseUri=" + baseUri + ", headers=" + new TreeSet<>(headers.keySet())
            + ", username=" + username + ", password=" + (password == null ? "null" : "******")
            + ", abendHeader=" + abendHeader + ", timeout=" + timeout + ", connectTimeout=" + connectTimeout
            + ", ssl=" + ssl + ", routes=" + new TreeMap<>(routes) + "]";
    }
}
