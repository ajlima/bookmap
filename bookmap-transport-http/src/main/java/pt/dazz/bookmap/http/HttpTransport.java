package pt.dazz.bookmap.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;

/**
 * A transport that sends the request bytes of a book as the body of an HTTP request, and
 * returns the body of the response. The body is binary: {@code application/octet-stream}.
 *
 * <p>Map each operation name to an {@link HttpRoute} in the builder. The transport uses the JDK
 * HTTP client ({@code java.net.http}). It is thread-safe. The transport never sends a request two
 * times.
 *
 * <pre>{@code
 * HttpTransport transport = HttpTransport.builder()
 *     .baseUri("https://gateway.example.com/orders/")
 *     .basicAuth("ORDUSER", password)
 *     .route("GetCustomerOrders", "customer-orders")
 *     .build();
 * }</pre>
 */
public final class HttpTransport implements Transport, AutoCloseable {

    private final Map<String, HttpRoute> routes;
    private final Map<String, String> headers;
    private final Supplier<Map<String, String>> headerSupplier;
    private final String abendHeader;
    private final Duration timeout;
    private final HttpClient client;
    private final boolean ownsClient;
    private final HttpSender sender;
    private final AtomicBoolean closed = new AtomicBoolean();

    private HttpTransport(Builder builder, Map<String, HttpRoute> routes) {
        this.routes = routes;
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(builder.headers);
        this.headers = Collections.unmodifiableMap(copy);
        this.headerSupplier = builder.headerSupplier;
        this.abendHeader = builder.abendHeader;
        this.timeout = builder.timeout;
        this.ownsClient = builder.httpClient == null;
        this.client = ownsClient
            ? HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(builder.connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
            : builder.httpClient;
        this.sender = builder.sender != null
            ? builder.sender
            : request -> client.sendAsync(request, BodyHandlers.ofByteArray());
    }

    /**
     * Creates a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Sends the request bytes to the URI of the route, and returns the response body. See the
     * documentation of the module for the failure translation.
     *
     * @param request the request
     * @return the response body
     */
    @Override
    public byte[] exchange(BookRequest request) {
        String operation = request.operation();
        HttpRoute route = routes.get(operation);
        if (route == null) {
            throw new OperationNotFoundException(operation + ": no HTTP route");
        }
        String target = FailureTranslator.target(operation, route.method(), route.uri());
        Map<String, String> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        merged.putAll(headers);
        merged.putAll(route.headers());
        if (headerSupplier != null) {
            merged.putAll(headerSupplier.get());
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(route.uri())
            .method(route.method(), BodyPublishers.ofByteArray(request.payload()));
        merged.forEach(builder::header);
        CompletableFuture<HttpResponse<byte[]>> future = sender.send(builder.build());
        HttpResponse<byte[]> response;
        try {
            response = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new TransportException(target + ": request failed", e);
        }
        return FailureTranslator.checkResponse(target, response, abendHeader);
    }

    /**
     * Closes the transport. An {@code exchange} after {@code close()} throws
     * {@code TransportException}. If the transport created the HTTP client, this method calls
     * {@link HttpClient#shutdown()}: the client accepts no new request, and a request that runs
     * continues until it completes or until its timeout. This method does not wait. It does not
     * shut down a client of the caller. A second call does nothing.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ownsClient) {
            client.shutdown();
        }
    }

    /** Gives the routes, with resolved URIs. For the tests. */
    Map<String, HttpRoute> routes() {
        return routes;
    }

    /** Tells if the transport created its HTTP client. For the tests. */
    boolean ownsClient() {
        return ownsClient;
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return uri.isAbsolute()
            && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
            && uri.getHost() != null;
    }

    /** Builds an {@link HttpTransport}. */
    public static final class Builder {

        private URI baseUri;
        private final Map<String, HttpRoute> routes = new LinkedHashMap<>();
        private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private Supplier<Map<String, String>> headerSupplier;
        private String abendHeader;
        private Duration timeout = Duration.ofSeconds(30);
        private Duration connectTimeout = Duration.ofSeconds(10);
        private boolean connectTimeoutSet;
        private HttpClient httpClient;
        private HttpSender sender;

        private Builder() {
            headers.put("Content-Type", "application/octet-stream");
            headers.put("Accept", "application/octet-stream");
        }

        /**
         * Sets the URI that resolves a relative route URI, with {@link URI#resolve(URI)}. End
         * the path with {@code /}: without it, a relative route replaces the last segment.
         *
         * @param baseUri an absolute {@code http} or {@code https} URI
         * @return this builder
         * @throws IllegalArgumentException if the URI is not correct
         */
        public Builder baseUri(String baseUri) {
            return baseUri(URI.create(Objects.requireNonNull(baseUri, "baseUri")));
        }

        /**
         * Sets the URI that resolves a relative route URI. See {@link #baseUri(String)}.
         *
         * @param baseUri an absolute {@code http} or {@code https} URI
         * @return this builder
         * @throws IllegalArgumentException if the URI is not an absolute {@code http} or
         *     {@code https} URI
         */
        public Builder baseUri(URI baseUri) {
            Objects.requireNonNull(baseUri, "baseUri");
            if (!isHttp(baseUri)) {
                throw new IllegalArgumentException(
                    "The base URI must be an absolute http or https URI: " + baseUri);
            }
            this.baseUri = baseUri;
            return this;
        }

        /**
         * Adds a route with the method {@code POST} and no route headers. A route for the same
         * operation replaces an earlier route.
         *
         * @param operation the operation name
         * @param uri the URI, absolute or relative to the base URI
         * @return this builder
         */
        public Builder route(String operation, String uri) {
            Objects.requireNonNull(uri, "uri");
            return route(operation, HttpRoute.of(uri));
        }

        /**
         * Adds a route. A route for the same operation replaces an earlier route.
         *
         * @param operation the operation name
         * @param route the route
         * @return this builder
         */
        public Builder route(String operation, HttpRoute route) {
            routes.put(Objects.requireNonNull(operation, "operation"),
                Objects.requireNonNull(route, "route"));
            return this;
        }

        /**
         * Adds all routes of a map.
         *
         * @param routes the routes, by operation name
         * @return this builder
         */
        public Builder routes(Map<String, HttpRoute> routes) {
            Objects.requireNonNull(routes, "routes").forEach(this::route);
            return this;
        }

        /**
         * Adds a header to each request. A header replaces an earlier header with the same name.
         * Header names are not case-sensitive.
         *
         * @param name the header name
         * @param value the header value
         * @return this builder
         * @throws IllegalArgumentException if the header is not correct or is restricted
         */
        public Builder header(String name, String value) {
            HeaderRules.check(name, value);
            headers.put(name, value);
            return this;
        }

        /**
         * Sets the header {@code Authorization: Basic ...}, with the user and the password in
         * UTF-8 (RFC 7617).
         *
         * @param user the user
         * @param password the password
         * @return this builder
         */
        public Builder basicAuth(String user, String password) {
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(password, "password");
            String credentials = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            return header("Authorization", "Basic " + credentials);
        }

        /**
         * Sets a supplier of headers. The transport calls it for each request. Its headers
         * replace the other headers with the same name. The supplier must be thread-safe.
         *
         * @param headerSupplier the supplier
         * @return this builder
         */
        public Builder headerSupplier(Supplier<Map<String, String>> headerSupplier) {
            this.headerSupplier = Objects.requireNonNull(headerSupplier, "headerSupplier");
            return this;
        }

        /**
         * Sets the name of a response header that contains an abend code. If a response
         * contains this header with a value that is not blank, the transport throws
         * {@code RemoteAbendException}, for any status. The default is no abend check.
         *
         * @param name the header name
         * @return this builder
         */
        public Builder abendHeader(String name) {
            this.abendHeader = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * Sets the time limit of one exchange, from the send until the last byte of the
         * response body. The default is 30 seconds.
         *
         * @param timeout a positive duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = positive(timeout, "timeout");
            return this;
        }

        /**
         * Sets the time limit to connect. Only for the client that the transport creates. The
         * default is 10 seconds.
         *
         * @param connectTimeout a positive duration
         * @return this builder
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = positive(connectTimeout, "connectTimeout");
            this.connectTimeoutSet = true;
            return this;
        }

        /**
         * Sets the HTTP client of the caller, for example with an {@code SSLContext} for mutual
         * TLS, a proxy, or HTTP/2. The transport does not change the client and does not shut
         * it down. Without this method, the transport creates an HTTP/1.1 client that does not
         * follow redirects.
         *
         * @param httpClient the client
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        /** Sets the sender. For the tests. */
        Builder sender(HttpSender sender) {
            this.sender = Objects.requireNonNull(sender, "sender");
            return this;
        }

        /**
         * Builds the transport. The transport opens no connection here.
         *
         * @return a new transport
         * @throws IllegalStateException if no route is set, if a route URI cannot be resolved
         *     to an absolute {@code http} or {@code https} URI, or if {@code httpClient} and
         *     {@code connectTimeout} are both set
         */
        public HttpTransport build() {
            if (routes.isEmpty()) {
                throw new IllegalStateException("Set at least one route");
            }
            if (httpClient != null && connectTimeoutSet) {
                throw new IllegalStateException("Set the connect timeout on the HTTP client of "
                    + "the caller, not on the builder");
            }
            Map<String, HttpRoute> resolved = new LinkedHashMap<>();
            routes.forEach((operation, route) ->
                resolved.put(operation, resolve(operation, route)));
            return new HttpTransport(this, Collections.unmodifiableMap(resolved));
        }

        private HttpRoute resolve(String operation, HttpRoute route) {
            URI uri = route.uri();
            if (!uri.isAbsolute()) {
                if (baseUri == null) {
                    throw new IllegalStateException(operation + ": the route URI " + uri
                        + " is relative, and no base URI is set");
                }
                uri = baseUri.resolve(uri);
            }
            if (!isHttp(uri)) {
                throw new IllegalStateException(operation + ": the route URI " + uri
                    + " is not an absolute http or https URI");
            }
            return new HttpRoute(uri, route.method(), route.headers());
        }

        private static Duration positive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive: " + value);
            }
            return value;
        }
    }
}
