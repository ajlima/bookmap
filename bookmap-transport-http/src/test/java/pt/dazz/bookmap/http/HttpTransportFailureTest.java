package pt.dazz.bookmap.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;

class HttpTransportFailureTest {

    private static final BookRequest REQUEST = new BookRequest("GetInvoice", new byte[] {1}, 10);
    private static final String TARGET = "GetInvoice (POST https://billing.example.com/invoices)";

    private final AtomicInteger sends = new AtomicInteger();

    private HttpTransport.Builder builder(CompletableFuture<HttpResponse<byte[]>> future) {
        return HttpTransport.builder()
            .route("GetInvoice", "https://billing.example.com/invoices")
            .sender(request -> {
                sends.incrementAndGet();
                return future;
            });
    }

    static Stream<Arguments> failures() {
        return Stream.of(
            Arguments.of(new HttpConnectTimeoutException("x"), ConnectionException.class,
                TARGET + ": connection failed"),
            Arguments.of(new ConnectException("x"), ConnectionException.class,
                TARGET + ": connection failed"),
            Arguments.of(new SSLHandshakeException("x"), ConnectionException.class,
                TARGET + ": TLS handshake failed"),
            Arguments.of(new HttpTimeoutException("x"), TransportTimeoutException.class,
                TARGET + ": no response in 30000 ms"),
            Arguments.of(new IOException("x"), TransportException.class,
                TARGET + ": I/O failure"),
            Arguments.of(new IllegalStateException("x"), TransportException.class,
                TARGET + ": request failed"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void translatesTheFailureAndKeepsTheCause(Throwable failure,
                                              Class<? extends TransportException> type,
                                              String message) {
        try (HttpTransport transport = builder(CompletableFuture.failedFuture(failure)).build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(type)
                .hasMessage(message)
                .hasCause(failure);
        }
    }

    @Test
    void cancelsTheRequestWhenTheTimeEnds() {
        CompletableFuture<HttpResponse<byte[]>> future = new CompletableFuture<>();
        try (HttpTransport transport = builder(future).timeout(Duration.ofMillis(50)).build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(TransportTimeoutException.class)
                .hasMessage(TARGET + ": no response in 50 ms");
            assertThat(future).isCancelled();
        }
    }

    @Test
    void interruptCancelsTheRequestAndKeepsTheFlag() {
        CompletableFuture<HttpResponse<byte[]>> future = new CompletableFuture<>();
        try (HttpTransport transport = builder(future).build()) {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessage(TARGET + ": interrupted");
            assertThat(Thread.interrupted()).isTrue();
            assertThat(future).isCancelled();
        }
    }

    static Stream<Supplier<Map<String, String>>> badSuppliers() {
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("X-Token", null);
        return Stream.of(
            () -> {
                throw new IllegalStateException("no token");
            },
            () -> null,
            () -> Map.of("Host", "h"),
            () -> nullValue);
    }

    @ParameterizedTest
    @MethodSource("badSuppliers")
    void headerSupplierFailureSendsNoRequest(Supplier<Map<String, String>> supplier) {
        try (HttpTransport transport = builder(new CompletableFuture<>())
                .headerSupplier(supplier).build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessageStartingWith(TARGET + ": header supplier");
            assertThat(sends).hasValue(0);
        }
    }

    @Test
    void exchangeAfterCloseFails() {
        HttpTransport transport = builder(new CompletableFuture<>()).build();
        transport.close();

        assertThatThrownBy(() -> transport.exchange(REQUEST))
            .isExactlyInstanceOf(TransportException.class)
            .hasMessage("GetInvoice: the transport is closed");
        assertThat(sends).hasValue(0);
        assertThatCode(transport::close).doesNotThrowAnyException();
    }

    @Test
    void incorrectSuppliedHeaderNamesOnlyTheHeader() {
        try (HttpTransport transport = builder(new CompletableFuture<>())
                .headerSupplier(() -> Map.of("X-Token", "secret\u0000")).build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessage(TARGET + ": header supplier gave an incorrect header: X-Token")
                .hasNoCause();
        }
    }

    @Test
    void suppliedHeaderNameIsCleanedAndLimitedInTheMessage() {
        String name = "X\r\nInjected" + "A".repeat(100);
        try (HttpTransport transport = builder(new CompletableFuture<>())
                .headerSupplier(() -> Map.of(name, "v")).build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(TransportException.class)
                .hasMessageNotContaining("\r")
                .hasMessageNotContaining("\n")
                .hasMessageEndingWith(": X  Injected" + "A".repeat(53));
        }
    }

    @Test
    void suppliedMapIsReadOnlyOnce() {
        AtomicInteger reads = new AtomicInteger();
        Map<String, String> changing = new java.util.AbstractMap<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public java.util.Set<Map.Entry<String, String>> entrySet() {
                return reads.getAndIncrement() == 0
                    ? Map.of("X-Token", "t").entrySet()
                    : Map.of("Host", "h").entrySet();
            }
        };
        CompletableFuture<HttpResponse<byte[]>> future = new CompletableFuture<>();
        try (HttpTransport transport = builder(future).timeout(Duration.ofMillis(20))
                .headerSupplier(() -> changing).build()) {
            assertThatThrownBy(() -> transport.exchange(REQUEST))
                .isExactlyInstanceOf(TransportTimeoutException.class);
            assertThat(reads).hasValue(1);
        }
    }
}
