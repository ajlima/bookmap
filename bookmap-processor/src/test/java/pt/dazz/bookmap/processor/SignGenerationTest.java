package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.spi.BookCodec;

/** Tests the sign formats of zoned decimal fields: SIGN LEADING and SIGN SEPARATE. */
class SignGenerationTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private final TestCompiler compiler = TestCompiler.create();

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static void assertError(Compilation compilation, String descriptor, String phrase) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(descriptor + ": " + phrase);
    }

    private static JavaFileObject order(String... members) {
        return source("Order", "@BookOperation public class Order {",
            String.join("\n", members), "}");
    }

    private BookCodec<Object> codec(String className, JavaFileObject... files) {
        Compilation compilation = compiler.compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, className);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Test
    void eachSignFormatWritesItsBytesAndReadsThemBack() {
        BookCodec<Object> codec = codec("test.Invoice", source("Invoice",
            "@BookOperation public class Invoice {",
            field("@BookField(order = 1, pic = \"S9(3)\")", "int", "trailing"),
            field("@BookField(order = 2, pic = \"S9(3) SIGN LEADING\")", "int", "leading"),
            field("@BookField(order = 3, pic = \"S9(3)V9 SIGN IS TRAILING SEPARATE CHARACTER\")",
                "BigDecimal", "trailingSeparate"),
            field("@BookField(order = 4, length = 3, signed = true,"
                + " sign = Sign.LEADING_SEPARATE)", "Long", "leadingSeparate"),
            field("@BookField(order = 5, length = 3, signed = true, sign = Sign.TRAILING)",
                "Integer", "explicitTrailing"),
            "}"));
        Object value = codec.newInstance();
        CompiledCodec.set(value, "trailing", -12);
        CompiledCodec.set(value, "leading", -12);
        CompiledCodec.set(value, "trailingSeparate", new BigDecimal("-1.5"));
        CompiledCodec.set(value, "leadingSeparate", 7L);
        CompiledCodec.set(value, "explicitTrailing", 3);

        byte[] request = codec.encodeRequest(value, LATIN);

        assertThat(text(request)).isEqualTo("01K}120015-+00700C");
        assertThat(codec.maxResponseLength()).isEqualTo(3 + 3 + 5 + 4 + 3);
        Object back = codec.decodeRequest(request, LATIN);
        assertThat(CompiledCodec.get(back, "trailing")).isEqualTo(-12);
        assertThat(CompiledCodec.get(back, "leading")).isEqualTo(-12);
        assertThat(CompiledCodec.get(back, "trailingSeparate")).isEqualTo(new BigDecimal("-1.5"));
        assertThat(CompiledCodec.get(back, "leadingSeparate")).isEqualTo(7L);
        assertThat(CompiledCodec.get(back, "explicitTrailing")).isEqualTo(3);
    }

    @Test
    void numericEnumAndEmptyKeyUseTheSign() {
        BookCodec<Object> codec = codec("test.ListLines",
            source("Priority", "public enum Priority {",
                "  @CobolValue(\"-1\") LOW, @CobolValue(\"1\") HIGH }"),
            source("ListLines", "@BookOperation public class ListLines {",
                field("@BookField(order = 1, pic = \"S9 SIGN LEADING SEPARATE\")", "Priority",
                    "priority"),
                field("@BookCollection(order = 10, maxOccurs = 2, fixed = true,"
                    + " emptyKey = \"quantity\")", "List<OrderLine>", "lines"),
                "}"),
            source("OrderLine", "public class OrderLine {",
                field("@BookField(order = 1, pic = \"X(2)\")", "String", "code"),
                field("@BookField(order = 2, pic = \"S9(2) SIGN TRAILING SEPARATE\")", "int",
                    "quantity"),
                "}"));
        Object value = codec.newInstance();
        Object line = newLine(codec, "AB", -4);
        CompiledCodec.set(value, "priority", enumConstant(codec, "LOW"));
        CompiledCodec.set(value, "lines", List.of(line));

        byte[] response = codec.encodeResponse(value, LATIN);

        assertThat(text(response)).isEqualTo("-1AB04-" + "\0\0" + "00+");
        Object back = codec.newInstance();
        codec.decodeResponse(response, back, LATIN);
        assertThat(CompiledCodec.get(back, "priority")).hasToString("LOW");
        assertThat((List<?>) CompiledCodec.get(back, "lines")).hasSize(1);
    }

    private static Object newLine(BookCodec<Object> codec, String code, int quantity) {
        try {
            Object line = codec.getClass().getClassLoader().loadClass("test.OrderLine")
                .getConstructor().newInstance();
            CompiledCodec.set(line, "code", code);
            CompiledCodec.set(line, "quantity", quantity);
            return line;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(BookCodec<Object> codec, String name) {
        try {
            Class type = codec.getClass().getClassLoader().loadClass("test.Priority");
            return Enum.valueOf(type, name);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void picMustNotBeCombinedWithSign() {
        assertError(compiler.compile(order(
                field("@BookField(order = 1, pic = \"S9(3)\", sign = Sign.LEADING)", "int",
                    "a"))),
            "Order.a (order 1)", "pic must not be combined with sign");
    }

    @Test
    void picSignClauseErrorsAreCompileErrors() {
        Compilation compilation = compiler.compile(order(
            field("@BookField(order = 1, pic = \"9(3) SIGN LEADING\")", "int", "a"),
            field("@BookField(order = 2, pic = \"S9(3) COMP-3 SIGN LEADING\")", "int", "b")));
        assertError(compilation, "Order.a (order 1)", "Invalid PIC string \"9(3) SIGN LEADING\":"
            + " a SIGN clause needs a signed numeric picture");
        assertError(compilation, "Order.b (order 2)", "Invalid PIC string"
            + " \"S9(3) COMP-3 SIGN LEADING\": a SIGN clause is permitted only with usage DISPLAY");
    }

    @Test
    void explicitSignNeedsASignedDisplayNumber() {
        Compilation compilation = compiler.compile(order(
            field("@BookField(order = 1, length = 3, sign = Sign.LEADING)", "int", "a"),
            field("@BookField(order = 2, length = 3, signed = true, usage = Usage.COMP3,"
                + " sign = Sign.LEADING)", "int", "b"),
            field("@BookField(order = 3, length = 3, sign = Sign.TRAILING)", "String", "c"),
            field("@BookField(order = 4, sign = Sign.LEADING)", "LocalDate", "d"),
            field("@BookField(order = 5, length = 3, sign = Sign.TRAILING)", "long", "e")));
        assertError(compilation, "Order.a (order 1)", "sign needs a signed field");
        assertError(compilation, "Order.b (order 2)", "sign is permitted only with usage DISPLAY");
        assertError(compilation, "Order.c (order 3)", "sign is not permitted for String");
        assertError(compilation, "Order.d (order 4)", "sign is not permitted for LocalDate");
        assertError(compilation, "Order.e (order 5)", "sign needs a signed field");
        CompilationSubject.assertThat(compilation).hadErrorCount(5);
    }

    @Test
    void converterFieldCannotHaveASignFormat() {
        Compilation compilation = compiler.compile(
            source("CodeConverter",
                "public class CodeConverter implements FieldConverter<Integer> {",
                "public Integer decode(byte[] b, FieldContext c) { return 0; }",
                "public byte[] encode(Integer v, FieldContext c) { return new byte[4]; }",
                "}"),
            order(field("@BookField(order = 1, pic = \"S9(3) LEADING SEPARATE\","
                + " converter = CodeConverter.class)", "Integer", "a"),
                field("@BookField(order = 2, pic = \"S9(3) TRAILING\","
                    + " converter = CodeConverter.class)", "Integer", "b")));
        assertError(compilation, "Order.a (order 1)",
            "sign LEADING_SEPARATE is not permitted with a converter");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }
}
