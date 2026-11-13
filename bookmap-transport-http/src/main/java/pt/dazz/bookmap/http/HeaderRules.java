package pt.dazz.bookmap.http;

import java.net.http.HttpRequest;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Checks the HTTP header names, the header values, and the methods of the transport.
 */
final class HeaderRules {

    /** An HTTP token (RFC 9110, section 5.6.2). */
    static final Pattern TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private HeaderRules() {
    }

    /**
     * Checks a header.
     *
     * @param name the header name
     * @param value the header value
     * @throws NullPointerException if the name or the value is null
     * @throws IllegalArgumentException if the name is not a token, if the value contains a line
     *     break, or if the JDK client does not accept the header (a restricted header)
     */
    static void check(String name, String value) {
        Objects.requireNonNull(name, "header name");
        Objects.requireNonNull(value, "header value");
        if (!TOKEN.matcher(name).matches()) {
            throw new IllegalArgumentException("Incorrect header name: " + name);
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("The value of the header " + name
                + " contains a line break");
        }
        // The JDK client knows its restricted headers, for example Host and Content-Length.
        // Its message can contain the value, for example a token. Only the name goes out.
        try {
            HttpRequest.newBuilder().header(name, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The JDK HTTP client does not accept the header "
                + name + ": it is restricted, or its value is not correct");
        }
    }

    /**
     * Checks an HTTP method.
     *
     * @param method the method
     * @throws NullPointerException if the method is null
     * @throws IllegalArgumentException if the method is not a token, or is {@code CONNECT}
     */
    static void checkMethod(String method) {
        Objects.requireNonNull(method, "method");
        if (!TOKEN.matcher(method).matches()) {
            throw new IllegalArgumentException("Incorrect HTTP method: " + method);
        }
        if ("CONNECT".equals(method)) {
            throw new IllegalArgumentException("The JDK HTTP client does not support CONNECT");
        }
    }
}
