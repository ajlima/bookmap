package pt.dazz.bookmap.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;

class HttpTransportTest {

    private static final byte[] REQUEST = {(byte) 0xC1, 0x00, 0x0C};

    private TestServer server;
    private HttpTransport transport;

    @BeforeEach
    void startServer() throws IOException {
        server = new TestServer();
    }

    @AfterEach
    void stopServer() {
        if (transport != null) {
            transport.close();
        }
        server.close();
    }

    private HttpTransport.Builder builder() {
        return HttpTransport.builder().baseUri(server.baseUri()).route("GetInvoice", "invoices");
    }

    private byte[] exchange(HttpTransport.Builder builder) {
        transport = builder.build();
        return transport.exchange(new BookRequest("GetInvoice", REQUEST, 100));
    }

    private byte[] exchange() {
        return exchange(builder());
    }

    @Test
    void sendsTheBytesAndReturnsTheBodyUnchanged() {
        server.respond(200, "application/octet-stream", new byte[] {0x00, 0x00}, Map.of());

        byte[] response = exchange();

        assertThat(response).containsExactly(0x00, 0x00);
        assertThat(server.lastBody).containsExactly(REQUEST);
        assertThat(server.lastMethod).isEqualTo("POST");
        assertThat(server.lastPath).isEqualTo("/invoices");
    }

    @Test
    void sendsTheDefaultHeaders() {
        exchange();

        assertThat(server.lastHeaders.get("Content-Type")).containsExactly("application/octet-stream");
        assertThat(server.lastHeaders.get("Accept")).containsExactly("application/octet-stream");
    }

    @Test
    void mergesTheHeadersInOrder() {
        HttpRoute route = new HttpRoute(URI.create("invoices"), "PUT",
            Map.of("content-type", "application/x-book", "X-Route", "route", "X-Both", "route"));
        exchange(HttpTransport.builder()
            .baseUri(server.baseUri())
            .header("X-Transport", "transport")
            .header("X-Both", "transport")
            .header("X-Supplied", "transport")
            .route("GetInvoice", route)
            .headerSupplier(() -> Map.of("x-supplied", "supplier")));

        assertThat(server.lastMethod).isEqualTo("PUT");
        assertThat(server.lastHeaders.get("Content-Type")).containsExactly("application/x-book");
        assertThat(server.lastHeaders.get("X-Transport")).containsExactly("transport");
        assertThat(server.lastHeaders.get("X-Route")).containsExactly("route");
        assertThat(server.lastHeaders.get("X-Both")).containsExactly("route");
        assertThat(server.lastHeaders.get("X-Supplied")).containsExactly("supplier");
    }

    @Test
    void sendsBasicAuthenticationInUtf8() {
        exchange(builder().basicAuth("ORDUSER", "pässword"));

        String expected = "Basic " + Base64.getEncoder()
            .encodeToString("ORDUSER:pässword".getBytes(StandardCharsets.UTF_8));
        assertThat(server.lastHeaders.get("Authorization")).containsExactly(expected);
    }

    @Test
    void callsTheHeaderSupplierForEachExchange() {
        AtomicInteger calls = new AtomicInteger();
        transport = builder()
            .headerSupplier(() -> Map.of("Authorization", "Bearer t" + calls.incrementAndGet()))
            .build();

        transport.exchange(new BookRequest("GetInvoice", REQUEST, 100));
        transport.exchange(new BookRequest("GetInvoice", REQUEST, 100));

        assertThat(calls).hasValue(2);
        assertThat(server.lastHeaders.get("Authorization")).containsExactly("Bearer t2");
    }

    @Test
    void unknownOperationSendsNoRequest() {
        transport = builder().build();

        assertThatThrownBy(() -> transport.exchange(new BookRequest("GetOrder", REQUEST, 100)))
            .isExactlyInstanceOf(OperationNotFoundException.class)
            .hasMessageContaining("GetOrder");
        assertThat(server.requestCount()).isZero();
    }

    @Test
    void status404IsOperationNotFound() {
        server.respond(404, null, new byte[0], Map.of());

        assertThatThrownBy(this::exchange)
            .isExactlyInstanceOf(OperationNotFoundException.class)
            .hasMessage("GetInvoice (POST " + server.baseUri() + "invoices): HTTP status 404");
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 504})
    void timeoutStatusIsTransportTimeout(int status) {
        server.respond(status, null, new byte[0], Map.of());

        assertThatThrownBy(this::exchange).isExactlyInstanceOf(TransportTimeoutException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503})
    void gatewayStatusIsConnectionFailure(int status) {
        server.respond(status, null, new byte[0], Map.of());

        assertThatThrownBy(this::exchange).isExactlyInstanceOf(ConnectionException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 302, 400})
    void otherStatusIsTransportException(int status) {
        server.respond(status, null, new byte[0], Map.of());

        assertThatThrownBy(this::exchange)
            .isExactlyInstanceOf(TransportException.class)
            .hasMessageEndingWith("HTTP status " + status);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    void successWithoutABodyGivesAnEmptyArray(int status) {
        server.respond(status, null, new byte[0], Map.of());

        assertThat(exchange()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 200})
    void abendHeaderGivesRemoteAbendForAnyStatus(int status) {
        server.respond(status, null, new byte[0], Map.of("X-Abend-Code", " ASRA "));

        assertThatThrownBy(() -> exchange(builder().abendHeader("X-Abend-Code")))
            .isInstanceOfSatisfying(RemoteAbendException.class, e -> {
                assertThat(e.abendCode()).isEqualTo("ASRA");
                assertThat(e).hasMessageEndingWith(": abend ASRA");
            });
    }

    @Test
    void blankAbendHeaderUsesTheStatus() {
        server.respond(500, null, new byte[0], Map.of("X-Abend-Code", " "));

        assertThatThrownBy(() -> exchange(builder().abendHeader("X-Abend-Code")))
            .isExactlyInstanceOf(TransportException.class);
    }

    @Test
    void abendHeaderIsIgnoredWhenItIsNotConfigured() {
        server.respond(200, null, new byte[] {1}, Map.of("X-Abend-Code", "ASRA"));

        assertThat(exchange()).containsExactly(1);
    }

    @Test
    void messageContainsATextExcerptOfAJsonBody() {
        server.respond(500, "application/json",
            "{\"error\":\"x\"}".getBytes(StandardCharsets.UTF_8), Map.of());

        assertThatThrownBy(this::exchange)
            .hasMessageEndingWith(": HTTP status 500: {\"error\":\"x\"}");
    }

    @Test
    void messageContainsAHexExcerptOfABinaryBody() {
        server.respond(500, "application/octet-stream", new byte[] {(byte) 0xC1, (byte) 0xC2},
            Map.of());

        assertThatThrownBy(this::exchange).hasMessageEndingWith(": HTTP status 500: hex C1C2");
    }

    @Test
    void messageHasNoUserInformationAndNoQuery() {
        server.respond(500, null, new byte[0], Map.of());
        String uri = "http://u:p@127.0.0.1:" + server.port() + "/x?token=abc";

        assertThatThrownBy(() -> exchange(HttpTransport.builder().route("GetInvoice", uri)))
            .hasMessage("GetInvoice (POST http://127.0.0.1:" + server.port()
                + "/x): HTTP status 500")
            .hasMessageNotContaining("token")
            .hasMessageNotContaining("u:p");
    }

    @Test
    void usesTheClientOfTheCallerAndDoesNotShutItDown() throws Exception {
        server.respond(200, null, new byte[] {7}, Map.of());
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpTransport callerTransport = HttpTransport.builder()
                .httpClient(client)
                .baseUri(server.baseUri())
                .route("GetInvoice", "invoices")
                .build();

            assertThat(callerTransport.exchange(new BookRequest("GetInvoice", REQUEST, 100)))
                .containsExactly(7);
            callerTransport.close();

            assertThat(client.isTerminated()).isFalse();
            assertThat(client.send(java.net.http.HttpRequest.newBuilder(URI.create(server.baseUri()))
                    .build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray()).statusCode())
                .isEqualTo(200);
        }
    }
}
