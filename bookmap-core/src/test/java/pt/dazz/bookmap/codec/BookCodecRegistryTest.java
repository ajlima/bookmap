package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;
import org.junit.jupiter.api.Test;

public class BookCodecRegistryTest {

    /** A book for the tests. */
    public static class Invoice {
    }

    /** A codec that the ServiceLoader finds. */
    public static class InvoiceCodec implements BookCodec<Invoice> {
        @Override public Class<Invoice> type() { return Invoice.class; }
        @Override public String operationName() { return "INVOICE"; }
        @Override public String charsetName() { return ""; }
        @Override public int maxResponseLength() { return 10; }
        @Override public Invoice newInstance() { return new Invoice(); }
        @Override public byte[] encodeRequest(Invoice value, CodecContext ctx) { return new byte[0]; }
        @Override public void decodeResponse(byte[] data, Invoice target, CodecContext ctx) { }
        @Override public Invoice decodeRequest(byte[] data, CodecContext ctx) { return new Invoice(); }
        @Override public byte[] encodeResponse(Invoice value, CodecContext ctx) { return new byte[0]; }
        @Override public void resetOutputCollections(Invoice value) { }
    }

    @Test
    void ofIndexesByType() {
        InvoiceCodec codec = new InvoiceCodec();
        assertThat(BookCodecRegistry.of(codec).codecFor(Invoice.class)).isSameAs(codec);
    }

    @Test
    void duplicateCodecsFail() {
        assertThatThrownBy(() -> BookCodecRegistry.of(new InvoiceCodec(), new InvoiceCodec()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(Invoice.class.getName());
    }

    @Test
    void unknownTypeFails() {
        assertThatThrownBy(() -> BookCodecRegistry.of().codecFor(Invoice.class))
            .isInstanceOf(MappingException.class)
            .hasMessage("No codec for " + Invoice.class.getName()
                + ". Make sure that bookmap-processor is on the annotationProcessor path.");
    }

    @Test
    void loadDefaultFindsServiceLoaderEntries() {
        assertThat(BookCodecRegistry.loadDefault().codecFor(Invoice.class))
            .isInstanceOf(InvoiceCodec.class);
    }

    @Test
    void loadDefaultWithLoaderFindsServiceLoaderEntries() {
        ClassLoader loader = getClass().getClassLoader();
        assertThat(BookCodecRegistry.loadDefault(loader).codecFor(Invoice.class))
            .isInstanceOf(InvoiceCodec.class);
    }
}
