package pt.dazz.bookmap.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** An in-process HTTP server on a free local port. It records the last request. */
final class TestServer implements AutoCloseable {

    /** Answers one request. */
    @FunctionalInterface
    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile Handler handler = exchange -> respond(exchange, 200, null, new byte[0], Map.of());
    volatile String lastMethod;
    volatile String lastPath;
    volatile Headers lastHeaders;
    volatile byte[] lastBody;

    TestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requestCount.incrementAndGet();
                lastMethod = exchange.getRequestMethod();
                lastPath = exchange.getRequestURI().getPath();
                lastHeaders = exchange.getRequestHeaders();
                lastBody = exchange.getRequestBody().readAllBytes();
                handler.handle(exchange);
            }
        });
        server.setExecutor(executor);
        server.start();
    }

    /** Gives the base URI, with a trailing slash. */
    String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    int port() {
        return server.getAddress().getPort();
    }

    int requestCount() {
        return requestCount.get();
    }

    void handler(Handler handler) {
        this.handler = handler;
    }

    /** Answers with a status, an optional content type, a body, and extra headers. */
    void respond(int status, String contentType, byte[] body, Map<String, String> headers) {
        handler(exchange -> respond(exchange, status, contentType, body, headers));
    }

    static void respond(HttpExchange exchange, int status, String contentType, byte[] body,
                        Map<String, String> headers) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }
        headers.forEach(exchange.getResponseHeaders()::add);
        boolean noBody = body.length == 0 || status == 204 || status == 304;
        exchange.sendResponseHeaders(status, noBody ? -1 : body.length);
        if (!noBody) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
