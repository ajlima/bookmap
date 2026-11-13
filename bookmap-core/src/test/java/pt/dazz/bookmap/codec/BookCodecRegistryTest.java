package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

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

    /** A subclass of the book for the tests. */
    public static class CreditInvoice extends Invoice {
    }

    /** A codec for a hierarchy: it handles the base class and one subclass. */
    static class HierarchyCodec extends InvoiceCodec {
        @Override public List<Class<? extends Invoice>> types() {
            return List.of(Invoice.class, CreditInvoice.class);
        }
    }

    /** A codec that handles only the subclass. */
    static class CreditCodec extends InvoiceCodec {
        @Override public List<Class<? extends Invoice>> types() {
            return List.of(CreditInvoice.class);
        }
    }

    @Test
    void indexesAllTypesOfACodec() {
        HierarchyCodec codec = new HierarchyCodec();
        BookCodecRegistry registry = BookCodecRegistry.of(codec);
        assertThat(registry.codecFor(CreditInvoice.class)).isSameAs(codec);
        assertThat(registry.codecFor(Invoice.class)).isSameAs(codec);
    }

    /** A codec with an abstract base class: types() gives only the subclass. */
    static class AbstractBaseCodec extends InvoiceCodec {
        @Override public List<Class<? extends Invoice>> types() {
            return List.of(CreditInvoice.class);
        }
    }

    @Test
    void findsTheCodecByTypeAlsoWhenTypesLeavesItOut() {
        AbstractBaseCodec codec = new AbstractBaseCodec();
        BookCodecRegistry registry = BookCodecRegistry.of(codec);
        assertThat(registry.codecFor(Invoice.class)).isSameAs(codec);
        assertThat(registry.codecFor(CreditInvoice.class)).isSameAs(codec);
    }

    @Test
    void twoCodecsForOneSubtypeFail() {
        assertThatThrownBy(() -> BookCodecRegistry.of(new HierarchyCodec(), new CreditCodec()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("More than one codec for " + CreditInvoice.class.getName());
    }

    @Test
    void defaultReadResponseDecodesIntoTheTarget() {
        Invoice target = new Invoice();
        assertThat(new InvoiceCodec().readResponse(new byte[0], target, CodecContext.defaults()))
            .isSameAs(target);
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
