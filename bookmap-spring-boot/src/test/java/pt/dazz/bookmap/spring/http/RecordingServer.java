package pt.dazz.bookmap.spring.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;

/** A JDK HTTP server on a free local port. It records the last request and sends a reply. */
final class RecordingServer implements AutoCloseable {

    volatile String method;
    volatile String path;
    volatile byte[] body;
    final Map<String, String> headers = new ConcurrentHashMap<>();
    volatile int status = 200;
    volatile Map<String, String> replyHeaders = Map.of();
    volatile long delayMillis;

    private final HttpServer server;

    private RecordingServer(HttpServer server) {
        this.server = server;
        server.createContext("/", this::handle);
        server.start();
    }

    static RecordingServer http() throws IOException {
        return new RecordingServer(HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
    }

    static RecordingServer https(SSLContext context) throws IOException {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        return new RecordingServer(server);
    }

    String url(String scheme, String path) {
        return scheme + "://localhost:" + server.getAddress().getPort() + path;
    }

    private void handle(HttpExchange exchange) throws IOException {
        method = exchange.getRequestMethod();
        path = exchange.getRequestURI().getPath();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
        body = exchange.getRequestBody().readAllBytes();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        replyHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] reply = {7, 7, 7, 7};
        exchange.sendResponseHeaders(status, reply.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(reply);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
