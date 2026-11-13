package pt.dazz.bookmap.spring.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.http.HttpTransport;
import pt.dazz.bookmap.spi.BookRequest;
import pt.dazz.bookmap.spi.Transport;
import pt.dazz.bookmap.spring.BookmapAutoConfiguration;

class HttpTransportAutoConfigurationTest {

    private static final String P = "bookmap.transport.http.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HttpTransportAutoConfiguration.class,
            BookmapAutoConfiguration.class, SslAutoConfiguration.class));

    private RecordingServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = RecordingServer.http();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private String route() {
        return P + "routes[GetInvoice].uri=" + server.url("http", "/invoices");
    }

    private ApplicationContextRunner http() {
        return runner.withPropertyValues("bookmap.transport.type=http");
    }

    private static byte[] exchange(Transport transport) {
        return transport.exchange(new BookRequest("GetInvoice", new byte[] {1, 2}, 4));
    }

    @Test
    void noTypeNoTransport() {
        runner.withPropertyValues(route())
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(HttpTransport.class));
    }

    @Test
    void createsTransportAndClient() {
        http().withPropertyValues(route())
            .run(context -> assertThat(context).hasSingleBean(HttpTransport.class)
                .hasSingleBean(BookmapClient.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP", "Http"})
    void typeIsNotCaseSensitive(String type) {
        runner.withPropertyValues("bookmap.transport.type=" + type, route())
            .run(context -> assertThat(context).hasSingleBean(HttpTransport.class));
    }

    @Test
    void applicationTransportWins() {
        http().withPropertyValues(route(), P + "ssl.bundle=unknown")
            .withBean(Transport.class, () -> request -> new byte[0])
            .run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(HttpTransport.class)
                .doesNotHaveBean(HttpTransportAutoConfiguration.SslBundleHttpClient.class));
    }

    @Test
    void transportModuleAbsentFailsWithMessage() {
        runner.withClassLoader(new FilteredClassLoader(HttpTransport.class))
            .withPropertyValues("bookmap.transport.type=http", route())
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.type=http, but no Transport bean exists. Add "
                    + "bookmap-transport-http to the class path."));
    }

    @Test
    void propertiesReachTheRequest() {
        http().withPropertyValues(
                P + "base-uri=" + server.url("http", "/orders/"),
                P + "routes[GetInvoice].uri=invoices",
                P + "routes[GetInvoice].method=PUT",
                P + "routes[GetInvoice].headers[X-Route]=r1",
                P + "headers[X-Client-Id]=order-service",
                P + "username=ORDUSER",
                P + "password=secret")
            .run(context -> {
                assertThat(exchange(context.getBean(HttpTransport.class))).containsExactly(7, 7, 7, 7);
                assertThat(server.method).isEqualTo("PUT");
                assertThat(server.path).isEqualTo("/orders/invoices");
                assertThat(server.body).containsExactly(1, 2);
                assertThat(server.headers).containsEntry("x-route", "r1")
                    .containsEntry("x-client-id", "order-service")
                    .containsEntry("authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("ORDUSER:secret".getBytes(StandardCharsets.UTF_8)));
            });
    }

    @Test
    void defaultsReachTheRequest() {
        http().withPropertyValues(route())
            .run(context -> {
                exchange(context.getBean(HttpTransport.class));
                assertThat(server.method).isEqualTo("POST");
                assertThat(server.path).isEqualTo("/invoices");
                assertThat(server.headers).doesNotContainKey("authorization");
            });
    }

    @Test
    void abendHeaderIsApplied() {
        server.status = 500;
        server.replyHeaders = java.util.Map.of("X-Abend-Code", "ASRA");
        http().withPropertyValues(route(), P + "abend-header=X-Abend-Code")
            .run(context -> assertThatThrownBy(() -> exchange(context.getBean(HttpTransport.class)))
                .isInstanceOfSatisfying(RemoteAbendException.class,
                    e -> assertThat(e.abendCode()).isEqualTo("ASRA")));
    }

    @Test
    void timeoutIsApplied() {
        server.delayMillis = 2000;
        http().withPropertyValues(route(), P + "timeout=200ms")
            .run(context -> assertThatThrownBy(() -> exchange(context.getBean(HttpTransport.class)))
                .isInstanceOf(TransportTimeoutException.class));
    }

    @Test
    void connectTimeoutIsAccepted() {
        http().withPropertyValues(route(), P + "connect-timeout=1s")
            .run(context -> assertThat(exchange(context.getBean(HttpTransport.class))).hasSize(4));
    }

    @Test
    void connectTimeoutAndCustomizerClientFail() {
        http().withPropertyValues(route(), P + "connect-timeout=1s")
            .withBean(HttpTransportBuilderCustomizer.class,
                () -> builder -> builder.httpClient(HttpClient.newHttpClient()))
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageStartingWith("Set the connect timeout on the HTTP client"));
    }

    @Test
    void usernameWithoutPasswordFails() {
        http().withPropertyValues(route(), P + "username=ORDUSER")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.http.password is not set, but "
                    + "bookmap.transport.http.username is set."));
    }

    @Test
    void passwordWithoutUsernameFails() {
        http().withPropertyValues(route(), P + "password=secret")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.http.password is set, but "
                    + "bookmap.transport.http.username is not set.")
                .message().doesNotContain("secret"));
    }

    @Test
    void routeWithoutUriFails() {
        http().withPropertyValues(P + "routes[GetInvoice].method=PUT")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessage("bookmap.transport.http.routes[GetInvoice].uri is not set."));
    }

    @Test
    void noRouteFails() {
        http().run(context -> assertThat(context).hasFailed().getFailure().rootCause()
            .hasMessage("Set at least one route"));
    }

    @Test
    void relativeUriWithoutBaseFails() {
        http().withPropertyValues(P + "routes[GetInvoice].uri=invoices")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customizersRunInOrderAfterProperties() {
        Customizers.base = server.url("http", "");
        http().withPropertyValues(route())
            .withUserConfiguration(Customizers.class)
            .run(context -> {
                exchange(context.getBean(HttpTransport.class));
                assertThat(server.path).isEqualTo("/second");
            });
    }

    /** Two customizers. The second one in the order sets the last route. */
    static class Customizers {
        static volatile String base;

        @Bean
        @Order(2)
        HttpTransportBuilderCustomizer second() {
            return builder -> builder.route("GetInvoice", base + "/second");
        }

        @Bean
        @Order(1)
        HttpTransportBuilderCustomizer first() {
            return builder -> builder.route("GetInvoice", base + "/first");
        }
    }

    @Test
    void closesTransportWithContext() {
        http().withPropertyValues(route())
            .run(context -> {
                HttpTransport transport = context.getBean(HttpTransport.class);
                context.close();
                assertThatThrownBy(() -> exchange(transport)).isInstanceOf(TransportException.class);
            });
    }

    private static SSLContext serverContext() {
        return SslBundle.of(new PemSslStoreBundle(
                PemSslStoreDetails.forCertificate("classpath:ssl/server.crt")
                    .withPrivateKey("classpath:ssl/server.key"), null))
            .createSslContext();
    }

    private static final String BUNDLE =
        "spring.ssl.bundle.pem.mainframe.truststore.certificate=classpath:ssl/server.crt";

    @Test
    void sslBundleTrustsTheServer() throws Exception {
        try (RecordingServer https = RecordingServer.https(serverContext())) {
            http().withPropertyValues(P + "routes[GetInvoice].uri=" + https.url("https", "/invoices"),
                    BUNDLE, P + "ssl.bundle=mainframe")
                .run(context -> {
                    assertThat(exchange(context.getBean(HttpTransport.class))).hasSize(4);
                    HttpClient client = context.getBean(
                        HttpTransportAutoConfiguration.SslBundleHttpClient.class).client();
                    assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
                    assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
                    assertThat(client.connectTimeout()).contains(Duration.ofSeconds(10));
                });
        }
    }

    @Test
    void withoutSslBundleTheServerIsNotTrusted() throws Exception {
        try (RecordingServer https = RecordingServer.https(serverContext())) {
            http().withPropertyValues(P + "routes[GetInvoice].uri=" + https.url("https", "/invoices"))
                .run(context -> assertThatThrownBy(() -> exchange(context.getBean(HttpTransport.class)))
                    .isInstanceOf(ConnectionException.class));
        }
    }

    @Test
    void sslBundleOptionsAndConnectTimeoutAreApplied() {
        http().withPropertyValues(route(), BUNDLE, P + "ssl.bundle=mainframe",
                P + "connect-timeout=2s",
                "spring.ssl.bundle.pem.mainframe.options.enabled-protocols=TLSv1.3",
                "spring.ssl.bundle.pem.mainframe.options.ciphers=TLS_AES_128_GCM_SHA256")
            .run(context -> {
                HttpClient client = context.getBean(
                    HttpTransportAutoConfiguration.SslBundleHttpClient.class).client();
                assertThat(client.connectTimeout()).contains(Duration.ofSeconds(2));
                assertThat(client.sslParameters().getProtocols()).containsExactly("TLSv1.3");
                assertThat(client.sslParameters().getCipherSuites())
                    .containsExactly("TLS_AES_128_GCM_SHA256");
            });
    }

    @Test
    void unknownSslBundleFails() {
        http().withPropertyValues(route(), P + "ssl.bundle=unknown")
            .run(context -> assertThat(context).hasFailed().getFailure()
                .hasRootCauseInstanceOf(NoSuchSslBundleException.class));
    }

    @Test
    void contextCloseShutsDownTheSslBundleClient() {
        http().withPropertyValues(route(), BUNDLE, P + "ssl.bundle=mainframe")
            .run(context -> {
                HttpClient client = context.getBean(
                    HttpTransportAutoConfiguration.SslBundleHttpClient.class).client();
                context.close();
                assertThat(client.awaitTermination(Duration.ofSeconds(5))).isTrue();
                assertThat(client.isTerminated()).isTrue();
            });
    }

    @Test
    void customizerClientReplacesTheSslBundleClient() {
        HttpClient own = HttpClient.newHttpClient();
        http().withPropertyValues(route(), BUNDLE, P + "ssl.bundle=mainframe")
            .withBean(HttpTransportBuilderCustomizer.class, () -> builder -> builder.httpClient(own))
            .run(context -> assertThat(exchange(context.getBean(HttpTransport.class))).hasSize(4));
        own.shutdownNow();
    }
}
