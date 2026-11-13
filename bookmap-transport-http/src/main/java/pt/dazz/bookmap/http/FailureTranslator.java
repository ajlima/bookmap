package pt.dazz.bookmap.http;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
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
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return operation + " (" + method + " " + uri.getScheme() + "://" + uri.getHost() + port
            + uri.getRawPath() + ")";
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
            String abendCode = response.headers().firstValue(abendHeader).orElse("").strip();
            if (!abendCode.isEmpty()) {
                throw new RemoteAbendException(abendCode, target + ": abend " + abendCode);
            }
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
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
