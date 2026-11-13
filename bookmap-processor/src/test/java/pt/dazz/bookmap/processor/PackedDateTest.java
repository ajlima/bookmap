package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.spi.BookCodec;

/** Tests the date and time fields with the usage COMP3 (packed dates). */
class PackedDateTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private final TestCompiler compiler = TestCompiler.create();

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static JavaFileObject order(String... members) {
        return source("Order", "@BookOperation public class Order {",
            String.join("\n", members), "}");
    }

    private static void assertError(Compilation compilation, String descriptor, String phrase) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(descriptor + ": " + phrase);
    }

    private BookCodec<Object> codec(String className, JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, className);
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    @Test
    void eachTypeWritesPackedDigitsAndReadsThemBack() {
        BookCodec<Object> codec = codec("test.Order", order(
            field("@BookField(order = 1, pic = \"S9(8) COMP-3\")", "LocalDate", "orderDate"),
            field("@BookField(order = 2, pic = \"9(7) COMP-3\", pattern = \"yyyyDDD\")",
                "LocalDate", "shipDate"),
            field("@BookField(order = 3, pic = \"S9(7) COMP-3\", pattern = \"'0'HHmmss\")",
                "LocalTime", "shipTime"),
            field("@BookField(order = 4, usage = Usage.COMP3, signed = true,"
                + " pattern = \"yyyyMMddHHmmss\")", "LocalDateTime", "lastUpdate"),
            field("@BookField(order = 5, usage = Usage.COMP3, pattern = \"HHmm\")", "LocalTime",
                "cutOff"),
            field("@BookField(order = 6, pic = \"9(8) COMP-3\")", "LocalDate", "noDate")));
        Object value = codec.newInstance();
        CompiledCodec.set(value, "orderDate", LocalDate.of(2026, 11, 13));
        CompiledCodec.set(value, "shipDate", LocalDate.of(2026, 11, 13));
        CompiledCodec.set(value, "shipTime", LocalTime.of(13, 5, 9));
        CompiledCodec.set(value, "lastUpdate", LocalDateTime.of(2026, 11, 13, 23, 59, 58));
        CompiledCodec.set(value, "cutOff", LocalTime.of(0, 15));

        byte[] request = codec.encodeRequest(value, LATIN);

        assertThat(hex(request)).isEqualTo("020261113C" + "2026317F" + "0130509C"
            + "020261113235958C" + "00015F" + "000000000F");
        assertThat(codec.maxResponseLength()).isEqualTo(5 + 4 + 4 + 8 + 3 + 5);
        Object back = codec.decodeRequest(request, LATIN);
        assertThat(CompiledCodec.get(back, "orderDate")).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(CompiledCodec.get(back, "shipDate")).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(CompiledCodec.get(back, "shipTime")).isEqualTo(LocalTime.of(13, 5, 9));
        assertThat(CompiledCodec.get(back, "lastUpdate"))
            .isEqualTo(LocalDateTime.of(2026, 11, 13, 23, 59, 58));
        assertThat(CompiledCodec.get(back, "cutOff")).isEqualTo(LocalTime.of(0, 15));
        assertThat(CompiledCodec.get(back, "noDate")).isNull();
    }

    @Test
    void packedDateInACollectionItem() {
        BookCodec<Object> codec = codec("test.Shipment",
            source("Shipment", "@BookOperation public class Shipment {",
                field("@BookCollection(order = 1, maxOccurs = 2, fixed = true)",
                    "List<Stop>", "stops"),
                "}"),
            source("Stop", "public class Stop {",
                field("@BookField(order = 1, pic = \"S9(8) COMP-3\")", "LocalDate", "arrival"),
                "}"));
        Object shipment = codec.newInstance();
        Object stop;
        try {
            stop = codec.getClass().getClassLoader().loadClass("test.Stop")
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        CompiledCodec.set(stop, "arrival", LocalDate.of(2026, 1, 2));
        CompiledCodec.set(shipment, "stops", List.of(stop));

        byte[] response = codec.encodeResponse(shipment, LATIN);

        assertThat(hex(response)).isEqualTo("020260102C" + "0000000000");
        Object back = codec.newInstance();
        codec.decodeResponse(response, back, LATIN);
        assertThat((List<?>) CompiledCodec.get(back, "stops"))
            .extracting(item -> CompiledCodec.get(item, "arrival"))
            .containsExactly(LocalDate.of(2026, 1, 2));
    }

    @Test
    void patternMustGiveDigitsOnly() {
        Compilation compilation = compiler.compile(order(
            field("@BookField(order = 1, pic = \"S9(10) COMP-3\", pattern = \"yyyy-MM-dd\")",
                "LocalDate", "shipped"),
            field("@BookField(order = 2, usage = Usage.COMP3, pattern = \"HH:mm\")",
                "LocalTime", "cutOff"),
            field("@BookField(order = 3, pic = \"X(10)\", pattern = \"yyyy-MM-dd\")",
                "LocalDate", "textDate")));
        assertError(compilation, "Order.shipped (order 1)", "pattern 'yyyy-MM-dd' gives"
            + " characters that are not digits, but a COMP3 field holds only digits");
        assertError(compilation, "Order.cutOff (order 2)", "pattern 'HH:mm' gives"
            + " characters that are not digits, but a COMP3 field holds only digits");
        CompilationSubject.assertThat(compilation).hadErrorCount(2);
    }

    @Test
    void lengthMustAgreeWithThePattern() {
        assertError(compiler.compile(order(
                field("@BookField(order = 1, pic = \"S9(7) COMP-3\")", "LocalDate", "shipped"))),
            "Order.shipped (order 1)",
            "length 7 does not agree with pattern 'yyyyMMdd' (8 characters)");
    }

    @Test
    void signAndScaleRules() {
        Compilation compilation = compiler.compile(order(
            field("@BookField(order = 1, pic = \"S9(6)V99 COMP-3\")", "LocalDate", "decimal"),
            field("@BookField(order = 2, pic = \"S9(8)\")", "LocalDate", "zoned"),
            field("@BookField(order = 3, signed = true)", "LocalDate", "signedText"),
            field("@BookField(order = 4, usage = Usage.COMP3, scale = 0)", "LocalDate",
                "scaled"),
            field("@BookField(order = 5, usage = Usage.COMP3, signed = true,"
                + " sign = Sign.LEADING)", "LocalDate", "leading"),
            field("@BookField(order = 6, pic = \"9(8) COMP\")", "LocalDate", "binary")));
        assertError(compilation, "Order.decimal (order 1)",
            "a decimal pic is not permitted for LocalDate");
        assertError(compilation, "Order.zoned (order 2)",
            "a signed pic is permitted only with COMP-3 for LocalDate");
        assertError(compilation, "Order.signedText (order 3)",
            "signed is not permitted for LocalDate");
        assertError(compilation, "Order.scaled (order 4)", "scale is not permitted for LocalDate");
        assertError(compilation, "Order.leading (order 5)", "sign is not permitted for LocalDate");
        assertError(compilation, "Order.binary (order 6)", "COMP is not permitted for LocalDate");
        CompilationSubject.assertThat(compilation).hadErrorCount(6);
    }

    @Test
    void defaultUsageDoesNotApplyToDates() {
        BookCodec<Object> codec = codec("test.Order", source("Order",
            "@BookOperation @BookDefaults(usage = Usage.COMP3) public class Order {",
            field("@BookField(order = 1)", "LocalDate", "orderDate"),
            "}"));
        Object value = codec.newInstance();
        CompiledCodec.set(value, "orderDate", LocalDate.of(2026, 11, 13));
        assertThat(new String(codec.encodeRequest(value, LATIN), StandardCharsets.ISO_8859_1))
            .isEqualTo("20261113");
    }
}
