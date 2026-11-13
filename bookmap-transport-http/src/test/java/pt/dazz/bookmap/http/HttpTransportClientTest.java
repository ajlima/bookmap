package pt.dazz.bookmap.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;

class HttpTransportClientTest {

    private TestServer server;
    private HttpTransport transport;
    private BookmapClient client;

    @BeforeEach
    void start() throws IOException {
        server = new TestServer();
        transport = HttpTransport.builder()
            .baseUri(server.baseUri())
            .route("GetInvoice", "invoices")
            .build();
        client = BookmapClient.builder().transport(transport).build();
    }

    @AfterEach
    void stop() {
        transport.close();
        server.close();
    }

    private static GetInvoice invoice(String id) {
        GetInvoice call = new GetInvoice();
        call.setInvoiceId(id);
        return call;
    }

    @Test
    void fullCallThroughClient() {
        server.respond(200, "application/octet-stream",
            new byte[] {0x00, 0x01, 0x23, 0x45, 0x0C, 0x00, 0x03}, Map.of());

        GetInvoice result = client.execute(invoice("INV-000042"));

        assertThat(result.getAmount()).isEqualTo(new BigDecimal("1234.50"));
        assertThat(result.getLineCount()).isEqualTo(3);
        assertThat(server.lastBody).isEqualTo("INV-000042".getBytes(Charset.forName("IBM037")));
        assertThat(server.lastPath).isEqualTo("/invoices");
    }

    @Test
    void responseEndingWithZeroBytesAndPaddingDecodes() {
        server.respond(200, "application/octet-stream",
            new byte[] {0x00, 0x00, 0x00, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00}, Map.of());

        GetInvoice result = client.execute(invoice("INV-000001"));

        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.ZERO).hasScaleOf(2);
        assertThat(result.getLineCount()).isZero();
    }
}
