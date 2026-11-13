package pt.dazz.bookmap.mq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.codec.BookCodecRegistry;

class MqTransportClientTest {

    @Test
    void fullCall() {
        Charset ebcdic = Charset.forName("IBM037");
        FakeConnector fake = new FakeConnector();
        fake.responder = (queue, correlationId) -> "PAID".getBytes(ebcdic);
        try (MqTransport transport = MqTransport.builder()
                .connector(fake)
                .route("GetInvoice", "INVOICE.REQUEST", "INVOICE.REPLY")
                .build()) {
            BookmapClient client = BookmapClient.builder()
                .transport(transport)
                .codecRegistry(BookCodecRegistry.of(new InvoiceCodec()))
                .build();
            Invoice invoice = new Invoice();
            invoice.number = "INV-2026-0042";

            Invoice result = client.execute(invoice);

            assertThat(result.status).isEqualTo("PAID");
            assertThat(fake.sent).singleElement().satisfies(request -> {
                assertThat(request.requestQueue()).isEqualTo("INVOICE.REQUEST");
                assertThat(new String(request.payload(), ebcdic)).isEqualTo("INV-2026-0042");
            });
        }
    }
}
