package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

class FixedCollectionGenerationTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    /** The order total +123.45 (COMP-3, 9 digits, 5 bytes). */
    private static final byte[] TOTAL = hex("000012345C");
    private static final byte[] EMPTY_LINE = new byte[7];

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /**
     * orderId (INPUT, 10), lineCount (OUTPUT, 2), lines (OUTPUT, fixed, maxOccurs 3, 7 bytes
     * each), orderTotal (OUTPUT, COMP-3, 5 bytes).
     */
    private static JavaFileObject getOrder(String name, String itemType, String dependsOn) {
        return source(name,
            "@BookOperation public class " + name + " {",
            field("@BookField(order = 10, pic = \"X(10)\", direction = Direction.INPUT)", "String",
                "orderId"),
            field("@BookField(order = 20, pic = \"9(2)\", direction = Direction.OUTPUT)", "int",
                "lineCount"),
            field("@BookCollection(order = 30, maxOccurs = 3, fixed = true" + dependsOn + ")",
                "List<" + itemType + ">", "lines"),
            field("@BookField(order = 40, pic = \"S9(7)V99 COMP-3\", direction = Direction.OUTPUT)",
                "BigDecimal", "orderTotal"),
            "}");
    }

    /** productCode (4), quantity (3): 7 bytes. */
    private static JavaFileObject orderLine() {
        return source("OrderLine", "public class OrderLine {",
            field("@BookField(order = 1, pic = \"X(4)\")", "String", "productCode"),
            field("@BookField(order = 2, pic = \"9(3)\")", "int", "quantity"),
            "}");
    }

    /** quantity (COMP, 2 bytes): the value 0 encodes as LOW-VALUES. */
    private static JavaFileObject binaryLine() {
        return source("BinaryLine", "public class BinaryLine {",
            field("@BookField(order = 1, pic = \"9(4) COMP\")", "int", "quantity"),
            "}");
    }

    /** reference (5), entries (fixed, maxOccurs 3, subtype items of 6 bytes). */
    private static JavaFileObject statement() {
        return source("Statement",
            "@BookOperation public class Statement {",
            field("@BookField(order = 10, pic = \"X(5)\")", "String", "reference"),
            field("@BookCollection(order = 20, maxOccurs = 3, fixed = true)", "List<Entry>",
                "entries"),
            "}");
    }

    private static JavaFileObject[] entries() {
        return new JavaFileObject[] {
            source("Entry",
                "@BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"C\", type = CardEntry.class),",
                "  @BookSubTypes.Type(value = \"W\", type = WireEntry.class)})",
                "public abstract class Entry {",
                field("@BookField(order = 1, pic = \"X\")", "String", "kind"),
                "}"),
            source("CardEntry", "public class CardEntry extends Entry {",
                field("@BookField(order = 2, pic = \"9(3)\")", "int", "amount"), "}"),
            source("WireEntry", "public class WireEntry extends Entry {",
                field("@BookField(order = 2, pic = \"X(5)\")", "String", "iban"), "}")
        };
    }

    private BookCodec<Object> codec(String name, JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test." + name);
    }

    private BookCodec<Object> lowValuesCodec() {
        return codec("GetOrder", getOrder("GetOrder", "OrderLine", ""), orderLine());
    }

    private BookCodec<Object> countCodec() {
        return codec("GetOrder", getOrder("GetOrder", "OrderLine", ", dependsOn = \"lineCount\""),
            orderLine());
    }

    // ---- helpers ----

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
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

    private static Object newObject(BookCodec<Object> codec, String className) {
        try {
            return codec.getClass().getClassLoader().loadClass(className)
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object line(BookCodec<Object> codec, String product, int quantity) {
        Object line = newObject(codec, "test.OrderLine");
        CompiledCodec.set(line, "productCode", product);
        CompiledCodec.set(line, "quantity", quantity);
        return line;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object bean, String property) {
        return (List<Object>) CompiledCodec.get(bean, property);
    }

    private static Object order(BookCodec<Object> codec, int lineCount, List<Object> lines) {
        Object order = codec.newInstance();
        CompiledCodec.set(order, "lineCount", lineCount);
        CompiledCodec.set(order, "lines", lines);
        CompiledCodec.set(order, "orderTotal", new BigDecimal("123.45"));
        return order;
    }

    // ---- LOW-VALUES mode ----

    @Test
    void lowValuesModeWritesTheFullArea() {
        BookCodec<Object> codec = lowValuesCodec();
        byte[] response = codec.encodeResponse(
            order(codec, 1, new ArrayList<>(List.of(line(codec, "P001", 5)))), LATIN);
        assertThat(response).isEqualTo(
            concat(bytes("01"), bytes("P001005"), new byte[14], TOTAL));
    }

    @Test
    void lowValuesModeReadsUsedSlotsAndTheFieldAfter() {
        BookCodec<Object> codec = lowValuesCodec();
        Object target = codec.newInstance();
        codec.decodeResponse(concat(bytes("02"), bytes("P001005"), bytes("P002010"), EMPTY_LINE,
            TOTAL), target, LATIN);
        assertThat(list(target, "lines")).extracting(l -> CompiledCodec.get(l, "productCode"))
            .containsExactly("P001", "P002");
        assertThat(CompiledCodec.get(target, "orderTotal")).isEqualTo(new BigDecimal("123.45"));
    }

    @Test
    void lowValuesModeNullListWritesZeroesAndReadsBackEmpty() {
        BookCodec<Object> codec = lowValuesCodec();
        byte[] response = codec.encodeResponse(order(codec, 0, null), LATIN);
        assertThat(response).isEqualTo(concat(bytes("00"), new byte[21], TOTAL));
        Object target = codec.newInstance();
        codec.decodeResponse(response, target, LATIN);
        assertThat(list(target, "lines")).isEmpty();
    }

    @Test
    void lowValuesModeRequestAreaIsWrittenAndRead() {
        BookCodec<Object> codec = codec("SendOrder", source("SendOrder",
                "@BookOperation public class SendOrder {",
                field("@BookCollection(order = 10, maxOccurs = 2, fixed = true,"
                    + " direction = Direction.INPUT)", "List<OrderLine>", "lines"),
                field("@BookField(order = 20, pic = \"X(3)\", direction = Direction.INPUT)",
                    "String", "currency"),
                "}"),
            orderLine());
        Object request = codec.newInstance();
        CompiledCodec.set(request, "lines", List.of(line(codec, "P001", 5)));
        CompiledCodec.set(request, "currency", "EUR");
        byte[] bytes = codec.encodeRequest(request, LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P001005"), EMPTY_LINE, bytes("EUR")));
        Object decoded = codec.decodeRequest(bytes, LATIN);
        assertThat(list(decoded, "lines")).hasSize(1);
        assertThat(CompiledCodec.get(decoded, "currency")).isEqualTo("EUR");
    }

    @Test
    void lowValuesModeRejectsAnAllZeroItem() {
        BookCodec<Object> codec = codec("GetOrder", getOrder("GetOrder", "BinaryLine", ""),
            binaryLine());
        Object item = newObject(codec, "test.BinaryLine");
        Object order = order(codec, 1, List.of(item));
        assertThatThrownBy(() -> codec.encodeResponse(order, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetOrder.lines (order 30): item 0 is all LOW-VALUES, so a read cannot"
                + " find it");
    }

    @Test
    void lowValuesModeRejectsANullItem() {
        BookCodec<Object> codec = lowValuesCodec();
        List<Object> lines = new ArrayList<>();
        lines.add(null);
        Object order = order(codec, 1, lines);
        assertThatThrownBy(() -> codec.encodeResponse(order, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("the list contains a null item");
    }

    @Test
    void lowValuesModeRejectsTooManyItems() {
        BookCodec<Object> codec = lowValuesCodec();
        Object order = order(codec, 4, List.of(line(codec, "P001", 1), line(codec, "P002", 1),
            line(codec, "P003", 1), line(codec, "P004", 1)));
        assertThatThrownBy(() -> codec.encodeResponse(order, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("more than maxOccurs 3");
    }

    @Test
    void lowValuesModeRejectsAGap() {
        BookCodec<Object> codec = lowValuesCodec();
        byte[] response = concat(bytes("01"), EMPTY_LINE, bytes("P002010"), EMPTY_LINE, TOTAL);
        Object target = codec.newInstance();
        assertThatThrownBy(() -> codec.decodeResponse(response, target, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("GetOrder.lines (order 30): slot 0 is empty, but slot 1 after it has"
                + " data");
    }

    @Test
    void lowValuesModeRejectsAShortResponse() {
        BookCodec<Object> codec = lowValuesCodec();
        byte[] response = concat(bytes("01"), bytes("P001005"), new byte[3]);
        Object target = codec.newInstance();
        assertThatThrownBy(() -> codec.decodeResponse(response, target, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("too short for the fixed area");
    }

    // ---- count mode ----

    @Test
    void countModeSetsTheCountAndWritesTheFullArea() {
        BookCodec<Object> codec = countCodec();
        byte[] response = codec.encodeResponse(
            order(codec, 0, List.of(line(codec, "P001", 5), line(codec, "P002", 10))), LATIN);
        assertThat(response).isEqualTo(
            concat(bytes("02"), bytes("P001005"), bytes("P002010"), EMPTY_LINE, TOTAL));
    }

    @Test
    void countModeReadsCountItemsAndSkipsTheRest() {
        BookCodec<Object> codec = countCodec();
        Object target = codec.newInstance();
        codec.decodeResponse(concat(bytes("01"), bytes("P001005"), bytes("P002010"), EMPTY_LINE,
            TOTAL), target, LATIN);
        assertThat(list(target, "lines")).extracting(l -> CompiledCodec.get(l, "productCode"))
            .containsExactly("P001");
        assertThat(CompiledCodec.get(target, "orderTotal")).isEqualTo(new BigDecimal("123.45"));
    }

    @Test
    void countModeAcceptsAnAllZeroItem() {
        BookCodec<Object> codec = codec("GetOrder",
            getOrder("GetOrder", "BinaryLine", ", dependsOn = \"lineCount\""), binaryLine());
        byte[] response = codec.encodeResponse(
            order(codec, 0, List.of(newObject(codec, "test.BinaryLine"))), LATIN);
        assertThat(response).isEqualTo(concat(bytes("01"), new byte[6], TOTAL));
        Object target = codec.newInstance();
        codec.decodeResponse(response, target, LATIN);
        assertThat(list(target, "lines")).hasSize(1);
    }

    @Test
    void countModeRejectsACountAboveMax() {
        BookCodec<Object> codec = countCodec();
        byte[] response = concat(bytes("04"), new byte[21], TOTAL);
        Object target = codec.newInstance();
        assertThatThrownBy(() -> codec.decodeResponse(response, target, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("is not between 0 and maxOccurs 3");
    }

    // ---- subtype items ----

    @Test
    void fixedItemsWithSubtypesRoundTrip() {
        List<JavaFileObject> files = new ArrayList<>(List.of(statement()));
        files.addAll(List.of(entries()));
        BookCodec<Object> codec = codec("Statement", files.toArray(JavaFileObject[]::new));
        Object card = newObject(codec, "test.CardEntry");
        CompiledCodec.set(card, "amount", 42);
        Object wire = newObject(codec, "test.WireEntry");
        CompiledCodec.set(wire, "iban", "GB001");
        Object statement = codec.newInstance();
        CompiledCodec.set(statement, "reference", "ST001");
        CompiledCodec.set(statement, "entries", List.of(card, wire));

        byte[] bytes = codec.encodeResponse(statement, LATIN);
        assertThat(bytes).isEqualTo(
            concat(bytes("ST001"), bytes("C042  "), bytes("WGB001"), new byte[6]));

        Object target = codec.newInstance();
        codec.decodeResponse(bytes, target, LATIN);
        assertThat(list(target, "entries")).extracting(e -> e.getClass().getSimpleName())
            .containsExactly("CardEntry", "WireEntry");
    }
}
