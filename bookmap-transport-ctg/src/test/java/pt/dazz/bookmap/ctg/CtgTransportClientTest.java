package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.BookmapClient;
import pt.dazz.bookmap.codec.BookCodecRegistry;
import pt.dazz.bookmap.spi.BookCodec;

class CtgTransportClientTest {

    private final FakeConnector connector = new FakeConnector();
    private final BookmapClient client = BookmapClient.builder()
        .transport(CtgTransport.builder().connector(connector).route("GetInvoice", "PGMINV01").build())
        .build();

    private static GetInvoice invoice(String id) {
        GetInvoice call = new GetInvoice();
        call.setInvoiceId(id);
        return call;
    }

    /** CICS gives back the full COMMAREA. The response layout is at offset 0. */
    private void reply(byte... response) {
        connector.respond(call -> {
            byte[] commarea = new byte[call.commareaLength()];
            System.arraycopy(response, 0, commarea, 0, response.length);
            return new EciReply(0, null, commarea);
        });
    }

    @Test
    void responseEndingWithZeroBytesDecodes() {
        reply((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0x00);
        GetInvoice result = client.execute(invoice("INV-000001"));
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.ZERO).hasScaleOf(2);
        assertThat(result.getLineCount()).isZero();
        BookCodec<GetInvoice> codec = BookCodecRegistry.loadDefault().codecFor(GetInvoice.class);
        assertThat(connector.calls().getFirst().commareaLength())
            .isEqualTo(Math.max(10, codec.maxResponseLength()));
    }

    @Test
    void fullCallThroughClient() {
        reply((byte) 0x00, (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x0C, (byte) 0x00, (byte) 0x03);
        GetInvoice result = client.execute(invoice("INV-000042"));
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("1234.50"));
        assertThat(result.getLineCount()).isEqualTo(3);
        assertThat(connector.calls().getFirst().program()).isEqualTo("PGMINV01");
    }
}
