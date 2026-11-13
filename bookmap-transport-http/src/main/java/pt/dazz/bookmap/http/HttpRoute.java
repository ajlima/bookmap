package pt.dazz.bookmap.http;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The destination of one operation: a URI, an HTTP method, and headers.
 *
 * <p>The URI is an absolute {@code http} or {@code https} URI, or a relative reference. The
 * builder of {@link HttpTransport} resolves a relative reference against its base URI.
 *
 * <p>The default method is {@code POST}. The transport never sends a request two times. But the
 * JDK client can send a {@code GET} or {@code HEAD} request again after the server closed a
 * pooled connection. Use {@code POST} or {@code PUT} for a program that is not idempotent.
 *
 * @param uri the URI
 * @param method the HTTP method. {@code null} means {@code POST}.
 * @param headers the headers of the route. {@code null} means no headers.
 */
public record HttpRoute(URI uri, String method, Map<String, String> headers) {

    /** The default HTTP method. */
    static final String DEFAULT_METHOD = "POST";

    /**
     * Creates a route and checks the values.
     *
     * @throws NullPointerException if the URI is null, or if a header name or value is null
     * @throws IllegalArgumentException if the method is not an HTTP token, or if a header is
     *     not correct
     */
    public HttpRoute {
        Objects.requireNonNull(uri, "uri");
        if (method == null) {
            method = DEFAULT_METHOD;
        }
        HeaderRules.checkMethod(method);
        Map<String, String> copy = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> {
                HeaderRules.check(name, value);
                copy.put(name, value);
            });
        }
        headers = Collections.unmodifiableMap(copy);
    }

    /**
     * Creates a route with the method {@code POST} and no headers.
     *
     * @param uri the URI, absolute or relative
     * @return the route
     * @throws IllegalArgumentException if the URI is not correct
     */
    public static HttpRoute of(String uri) {
        return of(uri, DEFAULT_METHOD);
    }

    /**
     * Creates a route with a method and no headers.
     *
     * @param uri the URI, absolute or relative
     * @param method the HTTP method
     * @return the route
     * @throws IllegalArgumentException if the URI or the method is not correct
     */
    public static HttpRoute of(String uri, String method) {
        return new HttpRoute(URI.create(uri), method, null);
    }
}
