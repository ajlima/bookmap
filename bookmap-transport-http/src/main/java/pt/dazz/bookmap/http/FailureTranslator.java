package pt.dazz.bookmap.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import javax.net.ssl.SSLHandshakeException;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;

/**
 * Translates an HTTP response and a JDK failure to a BookMap exception. A message never contains
 * a request header, the user information of the URI, or the query of the URI.
 */
final class FailureTranslator {

    private static final int MAX_TEXT = 200;
    private static final int MAX_HEX_BYTES = 32;
    private static final int MAX_ABEND_CODE = 32;

    private FailureTranslator() {
    }

    /**
     * Gives the start of each message: {@code operation (METHOD scheme://host[:port]/path)}.
     *
     * @param operation the operation name
     * @param method the HTTP method
     * @param uri the URI of the route
     * @return the text
     */
    static String target(String operation, String method, URI uri) {
        return operation + " (" + method + " " + safe(uri) + ")";
    }

    /**
     * Gives a URI without the user information, the query, and the fragment, for a message.
     *
     * @param uri the URI, absolute or relative
     * @return the text
     */
    static String safe(URI uri) {
        StringBuilder text = new StringBuilder();
        if (uri.getScheme() != null) {
            text.append(uri.getScheme()).append(':');
        }
        if (uri.getHost() != null) {
            text.append("//").append(uri.getHost());
            if (uri.getPort() != -1) {
                text.append(':').append(uri.getPort());
            }
        }
        if (uri.getRawPath() != null) {
            text.append(uri.getRawPath());
        }
        return text.toString();
    }

    /**
     * Translates a failure of the JDK client.
     *
     * @param target the start of the message
     * @param failure the failure
     * @param timeout the timeout of the transport
     * @return the exception, with the failure as the cause
     */
    static TransportException translate(String target, Throwable failure, Duration timeout) {
        if (failure instanceof HttpConnectTimeoutException || failure instanceof ConnectException) {
            return new ConnectionException(target + ": connection failed", failure);
        }
        if (failure instanceof SSLHandshakeException) {
            return new ConnectionException(target + ": TLS handshake failed", failure);
        }
        if (failure instanceof HttpTimeoutException) {
            return timeout(target, timeout, failure);
        }
        if (failure instanceof IOException) {
            return new TransportException(target + ": I/O failure", failure);
        }
        return new TransportException(target + ": request failed", failure);
    }

    /**
     * Creates the exception for a response that did not arrive in time.
     *
     * @param target the start of the message
     * @param timeout the timeout of the transport
     * @param cause the cause
     * @return the exception
     */
    static TransportTimeoutException timeout(String target, Duration timeout, Throwable cause) {
        return new TransportTimeoutException(
            target + ": no response in " + timeout.toMillis() + " ms", cause);
    }

    /**
     * Checks a response. The abend header comes before the status.
     *
     * @param target the start of the message
     * @param response the response
     * @param abendHeader the name of the abend header, or {@code null}
     * @return the body, for a status {@code 2xx}
     * @throws TransportException or a subclass, for a failure
     */
    static byte[] checkResponse(String target, HttpResponse<byte[]> response, String abendHeader) {
        if (abendHeader != null) {
            String abendCode = response.headers().firstValue(abendHeader).orElse("")
                .replaceAll("\\p{Cntrl}", " ").strip();
            if (abendCode.length() > MAX_ABEND_CODE) {
                abendCode = abendCode.substring(0, MAX_ABEND_CODE);
            }
            if (!abendCode.isEmpty()) {
                throw new RemoteAbendException(abendCode, target + ": abend " + abendCode);
            }
        }
        int status = response.statusCode();
        if (status / 100 == 2) {
            return response.body();
        }
        String excerpt = excerpt(response.headers(), response.body());
        String message = target + ": HTTP status " + status
            + (excerpt.isEmpty() ? "" : ": " + excerpt);
        throw switch (status) {
            case 404 -> new OperationNotFoundException(message);
            case 408, 504 -> new TransportTimeoutException(message);
            case 502, 503 -> new ConnectionException(message);
            default -> new TransportException(message);
        };
    }

    /**
     * Gives an excerpt of a response body. For a text, JSON, or XML content type, the excerpt is
     * the start of the body as UTF-8 text, with control characters as spaces. Otherwise, it is
     * the start of the body in hexadecimal.
     *
     * @param headers the response headers
     * @param body the response body
     * @return the excerpt, or an empty string for an empty body
     */
    static String excerpt(HttpHeaders headers, byte[] body) {
        if (body.length == 0) {
            return "";
        }
        String type = headers.firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (type.contains("text") || type.contains("json") || type.contains("xml")) {
            String text = new String(body, StandardCharsets.UTF_8);
            if (text.length() > MAX_TEXT) {
                text = text.substring(0, MAX_TEXT);
            }
            return text.replaceAll("\\p{Cntrl}", " ");
        }
        int length = Math.min(body.length, MAX_HEX_BYTES);
        return "hex " + HexFormat.of().withUpperCase().formatHex(body, 0, length);
    }
}
