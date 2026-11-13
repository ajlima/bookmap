package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

/** Tests the empty-slot markers SPACES and key of a fixed collection. */
class EmptyItemMarkerTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);
    private static final CodecContext EBCDIC = new CodecContext(Charset.forName("IBM1047"));

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /** lines (fixed, maxOccurs 3, with the given attributes), trailer (2). */
    private static JavaFileObject listLines(String itemType, String attributes) {
        return source("ListLines",
            "@BookOperation public class ListLines {",
            field("@BookField(order = 5, pic = \"9(1)\")", "int", "lineCount"),
            field("@BookCollection(order = 10, maxOccurs = 3, fixed = true" + attributes + ")",
                "List<" + itemType + ">", "lines"),
            field("@BookField(order = 20, pic = \"X(2)\")", "String", "trailer"),
            "}");
    }

    /** productCode (4), quantity (3): 7 bytes. */
    private static JavaFileObject orderLine() {
        return source("OrderLine", "public class OrderLine {",
            field("@BookField(order = 1, pic = \"X(4)\")", "String", "productCode"),
            field("@BookField(order = 2, pic = \"9(3)\")", "int", "quantity"),
            "}");
    }

    /** note (3): text only. */
    private static JavaFileObject noteLine() {
        return source("NoteLine", "public class NoteLine {",
            field("@BookField(order = 1, pic = \"X(3)\")", "String", "note"), "}");
    }

    /** note (1), productId (COMP-3, 5 digits, 3 bytes), units (COMP, 2 bytes): 6 bytes. */
    private static JavaFileObject numberLine() {
        return source("NumberLine", "public class NumberLine {",
            field("@BookField(order = 1, pic = \"X\")", "String", "note"),
            field("@BookField(order = 2, pic = \"S9(5) COMP-3\")", "Long", "productId"),
            field("@BookField(order = 3, pic = \"9(4) COMP\")", "int", "units"),
            "}");
    }

    private BookCodec<Object> codec(JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test.ListLines");
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

    private static Object item(BookCodec<Object> codec, String className, Object... properties) {
        try {
            Object item = codec.getClass().getClassLoader().loadClass(className)
                .getConstructor().newInstance();
            for (int i = 0; i < properties.length; i += 2) {
                CompiledCodec.set(item, (String) properties[i], properties[i + 1]);
            }
            return item;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object lines(BookCodec<Object> codec, List<Object> lines) {
        Object value = codec.newInstance();
        CompiledCodec.set(value, "lines", lines);
        CompiledCodec.set(value, "trailer", "OK");
        return value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> decodedLines(BookCodec<Object> codec, byte[] response,
                                             CodecContext ctx) {
        Object target = codec.newInstance();
        codec.decodeResponse(response, target, ctx);
        assertThat(CompiledCodec.get(target, "trailer")).isEqualTo("OK");
        return (List<Object>) CompiledCodec.get(target, "lines");
    }

    private static Object orderLine(BookCodec<Object> codec, String product, int quantity) {
        return item(codec, "test.OrderLine", "productCode", product, "quantity", quantity);
    }

    // ---- SPACES ----

    @Test
    void spacesModeWritesSpacesInTheUnusedSlotsAndReadsThemBack() {
        BookCodec<Object> codec = codec(listLines("OrderLine", ", emptyItem = EmptyItem.SPACES"),
            orderLine());
        byte[] response = codec.encodeResponse(
            lines(codec, List.of(orderLine(codec, "P001", 5))), LATIN);
        assertThat(response).isEqualTo(bytes("0P001005              OK"));
        assertThat(decodedLines(codec, response, LATIN))
            .extracting(l -> CompiledCodec.get(l, "productCode")).containsExactly("P001");
    }

    @Test
    void spacesModeUsesTheSpaceOfTheCharsetAndAcceptsLowValues() {
        BookCodec<Object> codec = codec(listLines("OrderLine", ", emptyItem = EmptyItem.SPACES"),
            orderLine());
        byte[] response = codec.encodeResponse(lines(codec, List.of()), EBCDIC);
        assertThat(response).isEqualTo(concat(hex("F0"), hex("40".repeat(21)), hex("D6D2")));
        byte[] mixed = concat(hex("F0"), hex("D7F0F0F1F0F0F5"), hex("4000404040004000"),
            new byte[6], hex("D6D2"));
        assertThat(decodedLines(codec, mixed, EBCDIC)).hasSize(1);
    }

    @Test
    void spacesModeRejectsAnItemOfOnlySpaces() {
        BookCodec<Object> codec = codec(listLines("NoteLine", ", emptyItem = EmptyItem.SPACES"),
            noteLine());
        Object value = lines(codec, List.of(item(codec, "test.NoteLine", "note", "")));
        assertThatThrownBy(() -> codec.encodeResponse(value, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListLines.lines (order 10): item 0 is all spaces, so a read cannot"
                + " find it");
    }

    @Test
    void spacesModeRejectsAGap() {
        BookCodec<Object> codec = codec(listLines("NoteLine", ", emptyItem = EmptyItem.SPACES"),
            noteLine());
        assertThatThrownBy(() -> decodedLines(codec, bytes("0   AB    OK"), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListLines.lines (order 10): slot 0 is empty, but slot 1 after it has"
                + " data");
    }

    // ---- key ----

    @Test
    void textKeyIgnoresTheOtherBytesOfTheSlot() {
        BookCodec<Object> codec = codec(listLines("OrderLine", ", emptyKey = \"productCode\""),
            orderLine());
        assertThat(decodedLines(codec, bytes("0P001005    999       OK"), LATIN))
            .extracting(l -> CompiledCodec.get(l, "quantity")).containsExactly(5);
        byte[] response = codec.encodeResponse(
            lines(codec, List.of(orderLine(codec, "P001", 5))), LATIN);
        assertThat(response).isEqualTo(concat(bytes("0P001005"), new byte[14], bytes("OK")));
    }

    @Test
    void textKeyRejectsAnItemWithAnEmptyKey() {
        BookCodec<Object> codec = codec(listLines("OrderLine", ", emptyKey = \"productCode\""),
            orderLine());
        Object value = lines(codec, List.of(orderLine(codec, "P001", 5),
            orderLine(codec, null, 7)));
        assertThatThrownBy(() -> codec.encodeResponse(value, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("ListLines.lines (order 10): item 1 has an empty key productCode, so a"
                + " read cannot find it");
    }

    @Test
    void displayKeyWithSpacesWritesZeroAndTreatsZeroAsEmpty() {
        BookCodec<Object> codec = codec(listLines("OrderLine",
            ", emptyKey = \"quantity\", emptyItem = EmptyItem.SPACES"), orderLine());
        byte[] response = codec.encodeResponse(
            lines(codec, List.of(orderLine(codec, "P001", 5))), LATIN);
        assertThat(response).isEqualTo(bytes("0P001005    000    000OK"));
        assertThat(decodedLines(codec, bytes("0P001005OLD0000    000OK"), LATIN)).hasSize(1);
        Object zero = lines(codec, List.of(orderLine(codec, "P001", 0)));
        assertThatThrownBy(() -> codec.encodeResponse(zero, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageContaining("item 0 has an empty key quantity");
    }

    @Test
    void packedAndBinaryKeysWriteZeroAndReadIt() {
        BookCodec<Object> packed = codec(listLines("NumberLine",
            ", emptyKey = \"productId\", emptyItem = EmptyItem.SPACES"), numberLine());
        byte[] response = packed.encodeResponse(lines(packed,
            List.of(item(packed, "test.NumberLine", "productId", 12L, "units", 3))), EBCDIC);
        String emptySlot = "40" + "00000C" + "4040";
        assertThat(response).isEqualTo(concat(hex("F0"), hex("4000012C0003"),
            hex(emptySlot + emptySlot), hex("D6D2")));
        assertThat(decodedLines(packed, response, EBCDIC)).hasSize(1);

        BookCodec<Object> binary = codec(listLines("NumberLine",
            ", emptyKey = \"units\", emptyItem = EmptyItem.SPACES"), numberLine());
        byte[] binaryResponse = binary.encodeResponse(lines(binary,
            List.of(item(binary, "test.NumberLine", "productId", 12L, "units", 3))), EBCDIC);
        String binarySlot = "40" + "404040" + "0000";
        assertThat(binaryResponse).isEqualTo(concat(hex("F0"), hex("4000012C0003"),
            hex(binarySlot + binarySlot), hex("D6D2")));
        // Spaces in a COMP key are a number that is not zero: the slot has data.
        assertThat(decodedLines(binary, concat(hex("F0"), hex("4000012C0003"),
            hex("40404040" + "4040"), hex(binarySlot), hex("D6D2")), EBCDIC)).hasSize(2);
    }

    @Test
    void keyOfASubtypeItemIsAFieldOfTheBaseClass() {
        BookCodec<Object> codec = codec(listLines("Entry", ", emptyKey = \"reference\""),
            source("Entry",
                "@BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"C\", type = CardEntry.class)})",
                "public abstract class Entry {",
                field("@BookField(order = 1, pic = \"X\")", "String", "kind"),
                field("@BookField(order = 2, pic = \"X(2)\")", "String", "reference"),
                "}"),
            source("CardEntry", "public class CardEntry extends Entry {",
                field("@BookField(order = 3, pic = \"9(2)\")", "int", "amount"), "}"));
        assertThat(decodedLines(codec, bytes("0CR142?  99?  99OK"), LATIN))
            .extracting(e -> CompiledCodec.get(e, "amount")).containsExactly(42);
    }

    // ---- count mode ----

    @Test
    void countModeUsesTheFillOfEmptyItem() {
        BookCodec<Object> codec = codec(listLines("OrderLine",
            ", dependsOn = \"lineCount\", emptyItem = EmptyItem.SPACES"), orderLine());
        byte[] response = codec.encodeResponse(
            lines(codec, List.of(orderLine(codec, "P001", 5))), LATIN);
        assertThat(response).isEqualTo(bytes("1P001005              OK"));
        assertThat(decodedLines(codec, response, LATIN)).hasSize(1);
    }

    // ---- compile errors ----

    private void assertError(String attributes, String itemType, JavaFileObject item,
                             String message) {
        Compilation compilation = compiler.compile(listLines(itemType, attributes), item);
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("ListLines.lines (order 10): " + message);
    }

    @Test
    void markersNeedAFixedCollection() {
        Compilation compilation = compiler.compile(source("ListLines",
            "@BookOperation public class ListLines {",
            field("@BookField(order = 5, pic = \"9(1)\")", "int", "lineCount"),
            field("@BookCollection(order = 10, maxOccurs = 3, emptyItem = EmptyItem.SPACES)",
                "List<OrderLine>", "lines"),
            field("@BookCollection(order = 20, maxOccurs = 3, dependsOn = \"lineCount2\","
                + " emptyKey = \"productCode\")", "List<OrderLine>", "others"),
            field("@BookField(order = 15, pic = \"9(1)\")", "int", "lineCount2"),
            "}"), orderLine());
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(
            "ListLines.lines (order 10): emptyItem and emptyKey need fixed = true");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
            "ListLines.others (order 20): emptyItem and emptyKey need fixed = true");
    }

    @Test
    void emptyKeyIsNotPermittedWithDependsOn() {
        assertError(", dependsOn = \"lineCount\", emptyKey = \"productCode\"", "OrderLine",
            orderLine(), "emptyKey is not permitted with dependsOn: the count field gives the"
                + " number of items");
    }

    @Test
    void emptyKeyMustBeAFieldOfTheItemClass() {
        assertError(", emptyKey = \"price\"", "OrderLine", orderLine(),
            "emptyKey field 'price' is not a field of item class test.OrderLine");
    }

    @Test
    void emptyKeyMustNotBeAFieldOfASubtype() {
        Compilation compilation = compiler.compile(listLines("Entry", ", emptyKey = \"amount\""),
            source("Entry",
                "@BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"C\", type = CardEntry.class)})",
                "public abstract class Entry {",
                field("@BookField(order = 1, pic = \"X\")", "String", "kind"),
                "}"),
            source("CardEntry", "public class CardEntry extends Entry {",
                field("@BookField(order = 2, pic = \"9(2)\")", "int", "amount"), "}"));
        CompilationSubject.assertThat(compilation).hadErrorContaining("ListLines.lines (order"
            + " 10): emptyKey field 'amount' is not a field of item class test.Entry");
    }

    @Test
    void emptyKeyMustBeTextOrNumber() {
        assertError(", emptyKey = \"shipDate\"", "DatedLine",
            source("DatedLine", "public class DatedLine {",
                field("@BookField(order = 1)", "LocalDate", "shipDate"), "}"),
            "emptyKey field 'shipDate' must be String or a number without a converter");
    }

    @Test
    void emptyKeyOfAnInvalidItemClassGivesOnlyTheItemError() {
        Compilation compilation = compiler.compile(listLines("EmptyLine",
            ", emptyKey = \"code\""), source("EmptyLine", "public class EmptyLine {", "}"));
        CompilationSubject.assertThat(compilation).hadErrorContaining(
            "item class test.EmptyLine must have at least one @BookField");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void lowValuesModeKeepsTheFormerCode() {
        Compilation compilation = compiler.compile(listLines("OrderLine", ""), orderLine());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        CompilationSubject.assertThat(compilation).generatedSourceFile("test.ListLines_BookCodec")
            .contentsAsUtf8String().doesNotContain("EmptySlot");
    }

    @Test
    void numberKeyCanBeBigDecimal() {
        BookCodec<Object> codec = codec(listLines("PriceLine", ", emptyKey = \"price\""),
            source("PriceLine", "public class PriceLine {",
                field("@BookField(order = 1, pic = \"9V9\")", "BigDecimal", "price"), "}"));
        assertThat(decodedLines(codec, bytes("0120000OK"), LATIN))
            .extracting(l -> CompiledCodec.get(l, "price"))
            .containsExactly(new BigDecimal("1.2"));
    }
}
