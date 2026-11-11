package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.lombok;
import static pt.dazz.bookmap.processor.TestCompiler.source;
import static pt.dazz.bookmap.processor.TestCompiler.sourceIn;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

class CollectionGenerationTest {

    enum ProcessorOrder { LOMBOK_FIRST, BOOKMAP_FIRST }

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    /** Invoice INV-0001 with the amount 1234.50 (COMP-3, 5 bytes). */
    private static final byte[] FIRST_INVOICE = concat(bytes("INV-0001"), hex("000123450C"));
    /** Invoice INV-0002 with the amount -9.99 (COMP-3, 5 bytes). */
    private static final byte[] SECOND_INVOICE = concat(bytes("INV-0002"), hex("000000999D"));

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /**
     * accountId (INPUT, 10), invoiceCount (OUTPUT, 3), invoices (OUTPUT, maxOccurs 5),
     * trailer (OUTPUT, 4). The item class is in another package.
     */
    private static JavaFileObject listInvoices() {
        return source("ListInvoices",
            "import test.billing.Invoice;",
            "@BookOperation public class ListInvoices {",
            field("@BookField(order = 10, length = 10, direction = Direction.INPUT)", "String",
                "accountId"),
            field("@BookField(order = 20, length = 3, direction = Direction.OUTPUT)", "int",
                "invoiceCount"),
            field("@BookCollection(order = 30, direction = Direction.OUTPUT,"
                + " dependsOn = \"invoiceCount\", maxOccurs = 5)", "List<Invoice>", "invoices"),
            field("@BookField(order = 40, length = 4, direction = Direction.OUTPUT)", "String",
                "trailer"),
            "}");
    }

    /** invoiceNumber (8), amount (COMP-3, 9 digits, scale 2, signed): 13 bytes. */
    private static JavaFileObject invoice() {
        return sourceIn("test.billing", "Invoice",
            "public class Invoice {",
            field("@BookField(order = 10, length = 8)", "String", "invoiceNumber"),
            field("@BookField(order = 20, length = 9, scale = 2, signed = true,"
                + " usage = Usage.COMP3)", "BigDecimal", "amount"),
            "}");
    }

    private static JavaFileObject flagConverter() {
        return source("FlagConverter",
            "public class FlagConverter implements FieldConverter<Boolean> {",
            "public Boolean decode(byte[] b, FieldContext c) {",
            "  String s = new String(b, c.charset());",
            "  return s.equals(\"Y\") ? Boolean.TRUE : s.equals(\"N\") ? Boolean.FALSE : null;",
            "}",
            "public byte[] encode(Boolean v, FieldContext c) {",
            "  return (v == null ? \" \" : v ? \"Y\" : \"N\").getBytes(c.charset());",
            "}",
            "}");
    }

    /**
     * shipmentId (INPUT, 8), lineCount (INPUT, Integer, 3), lines (INPUT, maxOccurs 3),
     * priority (INPUT_OUTPUT, converter).
     */
    private static JavaFileObject updateShipment() {
        return source("UpdateShipment",
            "@BookOperation public class UpdateShipment {",
            field("@BookField(order = 1, length = 8, direction = Direction.INPUT)", "String",
                "shipmentId"),
            field("@BookField(order = 2, length = 3, direction = Direction.INPUT)", "Integer",
                "lineCount"),
            field("@BookCollection(order = 3, direction = Direction.INPUT, maxOccurs = 3)",
                "List<ShipmentLine>", "lines"),
            field("@BookField(order = 4, length = 1, converter = FlagConverter.class)", "Boolean",
                "priority"),
            "}");
    }

    /** sku (6), quantity (3), shipDate (yyyyMMdd), fragile (converter): 18 bytes. */
    private static JavaFileObject shipmentLine() {
        return source("ShipmentLine",
            "public class ShipmentLine {",
            field("@BookField(order = 1, length = 6)", "String", "sku"),
            field("@BookField(order = 2, length = 3)", "int", "quantity"),
            field("@BookField(order = 3)", "LocalDate", "shipDate"),
            field("@BookField(order = 4, length = 1, converter = FlagConverter.class)", "Boolean",
                "fragile"),
            "}");
    }

    /** baseCurrency (3), rateCount (long, 2), rates (INPUT_OUTPUT, maxOccurs 4). */
    private static JavaFileObject exchangeRates() {
        return source("ExchangeRates",
            "@BookOperation public class ExchangeRates {",
            field("@BookField(order = 1, length = 3)", "String", "baseCurrency"),
            field("@BookField(order = 2, length = 2)", "long", "rateCount"),
            field("@BookCollection(order = 3, direction = Direction.INPUT_OUTPUT, maxOccurs = 4)",
                "List<Rate>", "rates"),
            "}");
    }

    /** currency (3), rate (7 digits, scale 4): 10 bytes. */
    private static JavaFileObject rate() {
        return source("Rate",
            "public class Rate {",
            field("@BookField(order = 1, length = 3)", "String", "currency"),
            field("@BookField(order = 2, length = 7, scale = 4)", "BigDecimal", "rate"),
            "}");
    }

    /** refundCount (OUTPUT, Long, signed, 3), refunds (OUTPUT, maxOccurs 4). */
    private static JavaFileObject listRefunds() {
        return source("ListRefunds",
            "import test.billing.Invoice;",
            "@BookOperation public class ListRefunds {",
            field("@BookField(order = 1, length = 3, signed = true, direction = Direction.OUTPUT)",
                "Long", "refundCount"),
            field("@BookCollection(order = 2, maxOccurs = 4)", "List<Invoice>", "refunds"),
            "}");
    }

    private Compilation compile(JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return compilation;
    }

    private BookCodec<Object> listInvoicesCodec() {
        return CompiledCodec.load(compile(listInvoices(), invoice()), "test.ListInvoices");
    }

    private BookCodec<Object> updateShipmentCodec() {
        return CompiledCodec.load(compile(updateShipment(), shipmentLine(), flagConverter()),
            "test.UpdateShipment");
    }

    private BookCodec<Object> exchangeRatesCodec() {
        return CompiledCodec.load(compile(exchangeRates(), rate()), "test.ExchangeRates");
    }

    // ---- helpers ----

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] hex(String digits) {
        return HexFormat.of().parseHex(digits);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    /** Makes an instance of a compiled test class with the class loader of the codec. */
    private static Object newObject(BookCodec<Object> codec, String className) {
        try {
            return codec.getClass().getClassLoader().loadClass(className)
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoice(BookCodec<Object> codec, String number, String amount) {
        Object invoice = newObject(codec, "test.billing.Invoice");
        CompiledCodec.set(invoice, "invoiceNumber", number);
        CompiledCodec.set(invoice, "amount", new BigDecimal(amount));
        return invoice;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object bean, String property) {
        return (List<Object>) CompiledCodec.get(bean, property);
    }

    private static List<Object> mutableList(Object... items) {
        return new ArrayList<>(List.of(items));
    }

    private static String sourceOf(Compilation compilation, String name) {
        try {
            return compilation.generatedSourceFile(name).orElseThrow()
                .getCharContent(true).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- brief cases ----

    @Test
    void decodesVariableItemsAndFieldAfterCollection() {
        BookCodec<Object> codec = listInvoicesCodec();
        Object target = codec.newInstance();
        byte[] response = concat(bytes("002"), FIRST_INVOICE, SECOND_INVOICE, bytes("END "));
        assertThat(response).hasSize(3 + 26 + 4);

        codec.decodeResponse(response, target, LATIN);

        assertThat(CompiledCodec.get(target, "invoiceCount")).isEqualTo(2);
        List<Object> invoices = list(target, "invoices");
        assertThat(invoices).extracting(i -> CompiledCodec.get(i, "invoiceNumber"))
            .containsExactly("INV-0001", "INV-0002");
        assertThat(invoices).extracting(i -> CompiledCodec.get(i, "amount"))
            .containsExactly(new BigDecimal("1234.50"), new BigDecimal("-9.99"));
        assertThat(CompiledCodec.get(target, "trailer")).isEqualTo("END");
    }

    @Test
    void decodeAppendsToExistingList() {
        BookCodec<Object> codec = listInvoicesCodec();
        Object target = codec.newInstance();
        List<Object> existing = mutableList(invoice(codec, "INV-0000", "1.00"));
        CompiledCodec.set(target, "invoices", existing);

        codec.decodeResponse(concat(bytes("002"), FIRST_INVOICE, SECOND_INVOICE, bytes("END ")),
            target, LATIN);

        assertThat(list(target, "invoices")).isNotSameAs(existing)
            .extracting(i -> CompiledCodec.get(i, "invoiceNumber"))
            .containsExactly("INV-0000", "INV-0001", "INV-0002");
        assertThat(existing).hasSize(1);
        assertThat(CompiledCodec.get(target, "invoiceCount")).isEqualTo(2);
    }

    @Test
    void decodeAppendsToAnUnmodifiableList() {
        BookCodec<Object> codec = listInvoicesCodec();
        Object target = codec.newInstance();
        CompiledCodec.set(target, "invoices", List.of(invoice(codec, "INV-0000", "1.00")));

        codec.decodeResponse(concat(bytes("001"), FIRST_INVOICE, bytes("END ")), target, LATIN);

        assertThat(list(target, "invoices"))
            .extracting(i -> CompiledCodec.get(i, "invoiceNumber"))
            .containsExactly("INV-0000", "INV-0001");
    }

    /** A getter that returns a copy of the list, as some defensive classes do. */
    private static JavaFileObject listCopiedInvoices() {
        return source("ListCopiedInvoices",
            "import test.billing.Invoice;",
            "@BookOperation public class ListCopiedInvoices {",
            "@BookField(order = 1, length = 1) private int invoiceCount;",
            "@BookCollection(order = 2, maxOccurs = 2) private List<Invoice> invoices;",
            accessors("int", "invoiceCount"),
            "public List<Invoice> getInvoices() {",
            "  return invoices == null ? null : new java.util.ArrayList<>(invoices);",
            "}",
            "public void setInvoices(List<Invoice> v) { this.invoices = v; }",
            "}");
    }

    @Test
    void decodeKeepsTheItemsWhenTheGetterReturnsACopy() {
        BookCodec<Object> codec = CompiledCodec.load(compile(listCopiedInvoices(), invoice()),
            "test.ListCopiedInvoices");
        Object target = codec.newInstance();
        codec.resetOutputCollections(target);

        codec.decodeResponse(concat(bytes("1"), FIRST_INVOICE), target, LATIN);
        codec.decodeResponse(concat(bytes("1"), SECOND_INVOICE), target, LATIN);

        assertThat(list(target, "invoices"))
            .extracting(i -> CompiledCodec.get(i, "invoiceNumber"))
            .containsExactly("INV-0001", "INV-0002");
    }

    @Test
    void resetClearsOutputCollections() {
        BookCodec<Object> codec = listInvoicesCodec();
        Object target = codec.newInstance();
        CompiledCodec.set(target, "invoices", mutableList(invoice(codec, "INV-0000", "1.00")));

        codec.resetOutputCollections(target);

        assertThat(list(target, "invoices")).isNotNull().isEmpty();
        Object fresh = codec.newInstance();
        codec.resetOutputCollections(fresh);
        assertThat(list(fresh, "invoices")).isNotNull().isEmpty();
    }

    @Test
    void maxResponseLength() {
        assertThat(listInvoicesCodec().maxResponseLength()).isEqualTo(3 + 5 * 13 + 4);
    }

    @Test
    void countAboveMaxOccursFails() {
        BookCodec<Object> codec = listInvoicesCodec();
        byte[] response = concat(bytes("006"), new byte[6 * 13], bytes("END "));

        assertThatThrownBy(() -> codec.decodeResponse(response, codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListInvoices.invoices (order 30): count 6 is not between 0 and"
                + " maxOccurs 5");
    }

    @Test
    void blankCountMeansZeroItems() {
        BookCodec<Object> codec = listInvoicesCodec();
        Object target = codec.newInstance();

        codec.decodeResponse(bytes("   END "), target, LATIN);

        assertThat(CompiledCodec.get(target, "invoiceCount")).isEqualTo(0);
        assertThat(list(target, "invoices")).isEmpty();
        assertThat(CompiledCodec.get(target, "trailer")).isEqualTo("END");
    }

    private Object shipment(BookCodec<Object> codec, int lines) {
        Object shipment = codec.newInstance();
        CompiledCodec.set(shipment, "shipmentId", "SHP-0042");
        CompiledCodec.set(shipment, "priority", Boolean.TRUE);
        List<Object> items = new ArrayList<>();
        for (int i = 1; i <= lines; i++) {
            Object line = newObject(codec, "test.ShipmentLine");
            CompiledCodec.set(line, "sku", "SKU-0" + i);
            CompiledCodec.set(line, "quantity", 5 * i);
            CompiledCodec.set(line, "shipDate", LocalDate.of(2026, 11, 10 + i));
            CompiledCodec.set(line, "fragile", i % 2 == 1);
            items.add(line);
        }
        CompiledCodec.set(shipment, "lines", items);
        return shipment;
    }

    private static final String SHIPMENT_REQUEST = "SHP-0042" + "002"
        + "SKU-01" + "005" + "20261111" + "Y"
        + "SKU-02" + "010" + "20261112" + "N"
        + "Y";

    @Test
    void inputCollectionSetsCountFromListSize() {
        BookCodec<Object> codec = updateShipmentCodec();
        Object shipment = shipment(codec, 2);

        byte[] request = codec.encodeRequest(shipment, LATIN);

        assertThat(text(request)).isEqualTo(SHIPMENT_REQUEST);
        assertThat(CompiledCodec.get(shipment, "lineCount")).isEqualTo(2);
    }

    @Test
    void inputCollectionAboveMaxOccursFails() {
        BookCodec<Object> codec = updateShipmentCodec();
        Object shipment = shipment(codec, 4);

        assertThatThrownBy(() -> codec.encodeRequest(shipment, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("UpdateShipment.lines (order 3): the list has 4 items, more than"
                + " maxOccurs 3");
    }

    @Test
    void decodeRequestCreatesNewList() {
        BookCodec<Object> codec = updateShipmentCodec();

        Object decoded = codec.decodeRequest(bytes(SHIPMENT_REQUEST), LATIN);

        assertThat(CompiledCodec.get(decoded, "shipmentId")).isEqualTo("SHP-0042");
        assertThat(CompiledCodec.get(decoded, "lineCount")).isEqualTo(2);
        List<Object> lines = list(decoded, "lines");
        assertThat(lines).isInstanceOf(ArrayList.class);
        assertThat(lines).extracting(l -> CompiledCodec.get(l, "sku"),
                l -> CompiledCodec.get(l, "quantity"), l -> CompiledCodec.get(l, "shipDate"),
                l -> CompiledCodec.get(l, "fragile"))
            .containsExactly(
                tuple("SKU-01", 5, LocalDate.of(2026, 11, 11),
                    true),
                tuple("SKU-02", 10, LocalDate.of(2026, 11, 12),
                    false));
        assertThat(CompiledCodec.get(decoded, "priority")).isEqualTo(true);
    }

    @Test
    void negativeCountFails() {
        BookCodec<Object> codec = CompiledCodec.load(compile(listRefunds(), invoice()),
            "test.ListRefunds");

        assertThatThrownBy(() -> codec.decodeResponse(bytes("00L"), codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListRefunds.refunds (order 2): count -3 is not between 0 and"
                + " maxOccurs 4");
    }

    @Test
    void encodeResponseSetsOutputCount() {
        BookCodec<Object> codec = listInvoicesCodec();
        Object value = codec.newInstance();
        CompiledCodec.set(value, "invoices", mutableList(invoice(codec, "INV-0001", "1234.50"),
            invoice(codec, "INV-0002", "-9.99")));
        CompiledCodec.set(value, "trailer", "END");

        byte[] response = codec.encodeResponse(value, LATIN);

        assertThat(response).isEqualTo(concat(bytes("002"), FIRST_INVOICE, SECOND_INVOICE,
            bytes("END ")));
        assertThat(CompiledCodec.get(value, "invoiceCount")).isEqualTo(2);
    }

    private Object rate(BookCodec<Object> codec, String currency, String rate) {
        Object item = newObject(codec, "test.Rate");
        CompiledCodec.set(item, "currency", currency);
        CompiledCodec.set(item, "rate", new BigDecimal(rate));
        return item;
    }

    @Test
    void inputOutputCollectionIsReplacedNotReset() {
        BookCodec<Object> codec = exchangeRatesCodec();
        Object rates = codec.newInstance();
        CompiledCodec.set(rates, "baseCurrency", "EUR");
        List<Object> sent = mutableList(rate(codec, "USD", "1.0850"), rate(codec, "GBP",
            "0.8600"));
        CompiledCodec.set(rates, "rates", sent);

        codec.resetOutputCollections(rates);
        assertThat(list(rates, "rates")).isSameAs(sent).hasSize(2);

        assertThat(text(codec.encodeRequest(rates, LATIN)))
            .isEqualTo("EUR02USD0010850GBP0008600");
        assertThat(CompiledCodec.get(rates, "rateCount")).isEqualTo(2L);

        codec.decodeResponse(bytes("EUR03USD0010900GBP0008700JPY1620000"), rates, LATIN);

        assertThat(list(rates, "rates")).isNotSameAs(sent)
            .extracting(r -> CompiledCodec.get(r, "currency"))
            .containsExactly("USD", "GBP", "JPY");
        assertThat(list(rates, "rates")).extracting(r -> CompiledCodec.get(r, "rate"))
            .containsExactly(new BigDecimal("1.0900"), new BigDecimal("0.8700"),
                new BigDecimal("162.0000"));
        assertThat(CompiledCodec.get(rates, "rateCount")).isEqualTo(3L);
        assertThat(sent).hasSize(2);
        assertThat(codec.maxResponseLength()).isEqualTo(3 + 2 + 4 * 10);
    }

    // ---- extra cases ----

    @Test
    void inputOutputCollectionIsInBothMessages() {
        BookCodec<Object> codec = exchangeRatesCodec();
        Object rates = codec.newInstance();
        CompiledCodec.set(rates, "baseCurrency", "EUR");
        CompiledCodec.set(rates, "rates", mutableList(rate(codec, "CHF", "0.9400")));

        byte[] response = codec.encodeResponse(rates, LATIN);

        assertThat(text(response)).isEqualTo("EUR01CHF0009400");
        Object decoded = codec.decodeRequest(response, LATIN);
        assertThat(list(decoded, "rates")).extracting(r -> CompiledCodec.get(r, "currency"))
            .containsExactly("CHF");
    }

    @Test
    void nullListWritesCountZero() {
        BookCodec<Object> codec = exchangeRatesCodec();
        Object rates = codec.newInstance();
        CompiledCodec.set(rates, "rateCount", 9L);

        assertThat(text(codec.encodeRequest(rates, LATIN))).isEqualTo("   00");
        assertThat(CompiledCodec.get(rates, "rateCount")).isEqualTo(0L);
        assertThat(list(rates, "rates")).isNull();
    }

    @Test
    void boxedLongCountRoundTrip() {
        BookCodec<Object> codec = CompiledCodec.load(compile(listRefunds(), invoice()),
            "test.ListRefunds");
        Object refunds = codec.newInstance();
        CompiledCodec.set(refunds, "refunds", mutableList(invoice(codec, "INV-0001",
            "1234.50")));

        byte[] response = codec.encodeResponse(refunds, LATIN);

        assertThat(CompiledCodec.get(refunds, "refundCount")).isEqualTo(1L);
        assertThat(response).hasSize(3 + 13).endsWith(FIRST_INVOICE);
        Object decoded = codec.newInstance();
        codec.decodeResponse(response, decoded, LATIN);
        assertThat(CompiledCodec.get(decoded, "refundCount")).isEqualTo(1L);
        assertThat(list(decoded, "refunds")).hasSize(1);
        Object blank = codec.newInstance();
        codec.decodeResponse(bytes("   "), blank, LATIN);
        assertThat(CompiledCodec.get(blank, "refundCount")).isNull();
        assertThat(list(blank, "refunds")).isEmpty();
        assertThat(text(codec.encodeResponse(codec.newInstance(), LATIN))).isEqualTo("00{");
    }

    @Test
    void itemClassInAnotherPackageIsImported() {
        Compilation compilation = compile(listInvoices(), invoice());

        assertThat(sourceOf(compilation, "test.ListInvoices_BookCodec"))
            .contains("import test.billing.Invoice;")
            .contains("private void writeItem_Invoice(BookWriter writer, Invoice value,")
            .contains("private Invoice readItem_Invoice(BookReader reader, CodecContext ctx)")
            .contains("\"Invoice.amount (order 20)\"");
    }

    @Test
    void oneConverterInstanceForEachConverterClass() {
        Compilation compilation = compile(updateShipment(), shipmentLine(), flagConverter());

        String source = sourceOf(compilation, "test.UpdateShipment_BookCodec");
        assertThat(source.split("new FlagConverter\\(\\)", -1)).hasSize(2);
    }

    /** Two collections in one class, with the same item class and with names of locals. */
    private static JavaFileObject catalog() {
        return source("Catalog",
            "@BookOperation public class Catalog {",
            field("@BookField(order = 1, length = 1)", "int", "first"),
            field("@BookCollection(order = 2, maxOccurs = 2)", "List<Tag>", "value"),
            field("@BookField(order = 3, length = 1)", "int", "second"),
            field("@BookCollection(order = 4, maxOccurs = 2)", "List<Tag>", "data"),
            field("@BookField(order = 5, length = 1)", "String", "item"),
            "}");
    }

    private static JavaFileObject tag() {
        return source("Tag",
            "public class Tag {",
            field("@BookField(order = 1, length = 2)", "String", "code"),
            "}");
    }

    private Object tag(BookCodec<Object> codec, String code) {
        Object tag = newObject(codec, "test.Tag");
        CompiledCodec.set(tag, "code", code);
        return tag;
    }

    @Test
    void twoCollectionsWithTheSameItemClass() {
        Compilation compilation = compile(catalog(), tag());
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Catalog");
        Object catalog = codec.newInstance();
        CompiledCodec.set(catalog, "value", mutableList(tag(codec, "A1"), tag(codec, "B2")));
        CompiledCodec.set(catalog, "data", mutableList(tag(codec, "C3")));
        CompiledCodec.set(catalog, "item", "Z");

        byte[] response = codec.encodeResponse(catalog, LATIN);

        assertThat(text(response)).isEqualTo("2A1B21C3Z");
        Object decoded = codec.newInstance();
        codec.decodeResponse(response, decoded, LATIN);
        assertThat(list(decoded, "value")).extracting(t -> CompiledCodec.get(t, "code"))
            .containsExactly("A1", "B2");
        assertThat(list(decoded, "data")).extracting(t -> CompiledCodec.get(t, "code"))
            .containsExactly("C3");
        assertThat(CompiledCodec.get(decoded, "item")).isEqualTo("Z");
        assertThat(codec.maxResponseLength()).isEqualTo(1 + 4 + 1 + 4 + 1);
        assertThat(sourceOf(compilation, "test.Catalog_BookCodec").split("void writeItem_Tag",
            -1)).hasSize(2);
    }

    @Test
    void nullItemFails() {
        BookCodec<Object> codec = CompiledCodec.load(compile(catalog(), tag()), "test.Catalog");
        Object catalog = codec.newInstance();
        List<Object> tags = new ArrayList<>();
        tags.add(null);
        CompiledCodec.set(catalog, "data", tags);

        assertThatThrownBy(() -> codec.encodeResponse(catalog, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("Catalog.data (order 4): the list contains a null item");
    }

    private static JavaFileObject parcels() {
        return source("Parcels",
            "@lombok.Data @BookOperation public class Parcels {",
            "@BookField(order = 1, length = 1) private int parcelCount;",
            "@BookCollection(order = 2, maxOccurs = 3) private List<Parcel> parcels;",
            "}");
    }

    private static JavaFileObject parcel() {
        return source("Parcel",
            "@lombok.Getter @lombok.Setter public class Parcel {",
            "@BookField(order = 1, length = 4) private String code;",
            "@BookField(order = 2, length = 3) private Integer weight;",
            "}");
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void lombokItemClass(ProcessorOrder order) {
        List<Processor> processors = order == ProcessorOrder.LOMBOK_FIRST
            ? List.of(lombok(), compiler.processor())
            : List.of(compiler.processor(), lombok());
        Compilation compilation = compiler.compileWithProcessors(processors, parcels(),
            parcel());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Parcels");
        Object parcels = codec.newInstance();

        codec.decodeResponse(bytes("2BOX1012TUBE003"), parcels, LATIN);

        assertThat(list(parcels, "parcels"))
            .extracting(p -> CompiledCodec.get(p, "code"), p -> CompiledCodec.get(p, "weight"))
            .containsExactly(tuple("BOX1", 12),
                tuple("TUBE", 3));
        assertThat(text(codec.encodeResponse(parcels, LATIN))).isEqualTo("2BOX1012TUBE003");
    }

    @Test
    void outputCollectionAboveMaxOccursFailsInEncodeResponse() {
        BookCodec<Object> codec = listInvoicesCodec();
        Object value = codec.newInstance();
        List<Object> invoices = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            invoices.add(invoice(codec, "INV-000" + i, "1.00"));
        }
        CompiledCodec.set(value, "invoices", invoices);

        assertThatThrownBy(() -> codec.encodeResponse(value, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListInvoices.invoices (order 30): the list has 6 items, more than"
                + " maxOccurs 5");
    }

    @Test
    void nestedItemClass() {
        Compilation compilation = compile(source("Warehouse",
            "@BookOperation public class Warehouse {",
            field("@BookField(order = 1, length = 1)", "int", "binCount"),
            field("@BookCollection(order = 2, maxOccurs = 2)", "List<Warehouse.Bin>", "bins"),
            "public static class Bin {",
            field("@BookField(order = 1, length = 3)", "String", "code"),
            "}",
            "}"));
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Warehouse");
        Object warehouse = codec.newInstance();

        codec.decodeResponse(bytes("2A01B02"), warehouse, LATIN);

        assertThat(list(warehouse, "bins")).extracting(b -> CompiledCodec.get(b, "code"))
            .containsExactly("A01", "B02");
        assertThat(sourceOf(compilation, "test.Warehouse_BookCodec"))
            .contains("private void writeItem_Warehouse_Bin(BookWriter writer, Warehouse.Bin value,")
            .contains("private Warehouse.Bin readItem_Warehouse_Bin(BookReader reader,");
    }
}
