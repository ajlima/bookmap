package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.spi.BookCodec;

/** Tests the fields with an edited picture, for example {@code Z,ZZ9.99CR}. */
class EditedPictureGenerationTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private final TestCompiler compiler = TestCompiler.create();

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static JavaFileObject invoice(String... members) {
        return source("Invoice", "@BookOperation public class Invoice {",
            String.join("\n", members), "}");
    }

    private static void assertError(Compilation compilation, String phrase) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(phrase);
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
    void eachNumberTypeWritesEditedTextAndReadsItBack() {
        BookCodec<Object> codec = codec("test.Invoice", invoice(
            field("@BookField(order = 1, pic = \"ZZ9\")", "int", "lineCount"),
            field("@BookField(order = 2, pic = \"PIC -9(3).\")", "Integer", "adjustment"),
            field("@BookField(order = 3, pic = \"Z(9)9\")", "long", "units"),
            field("@BookField(order = 4, pic = \"$$$,$$9\")", "Long", "fee"),
            field("@BookField(order = 5, pic = \"Z,ZZ9.99CR\")", "BigDecimal", "total"),
            field("@BookField(order = 6, pic = \"9(3)V99 BLANK WHEN ZERO\")", "BigDecimal",
                "discount"),
            field("@BookField(order = 7, pic = \"***9.99 DISPLAY\")", "BigDecimal", "check")));
        Object value = codec.newInstance();
        CompiledCodec.set(value, "lineCount", 7);
        CompiledCodec.set(value, "adjustment", -12);
        CompiledCodec.set(value, "units", 1234567890L);
        CompiledCodec.set(value, "fee", 1500L);
        CompiledCodec.set(value, "total", new BigDecimal("-1234.5"));
        CompiledCodec.set(value, "discount", BigDecimal.ZERO);
        CompiledCodec.set(value, "check", new BigDecimal("5"));

        byte[] request = codec.encodeRequest(value, LATIN);

        assertThat(text(request)).isEqualTo("  7" + "-012" + "1234567890" + " $1,500"
            + "1,234.50CR" + "     " + "***5.00");
        assertThat(codec.maxResponseLength()).isEqualTo(3 + 4 + 10 + 7 + 10 + 5 + 7);
        Object back = codec.decodeRequest(request, LATIN);
        assertThat(CompiledCodec.get(back, "lineCount")).isEqualTo(7);
        assertThat(CompiledCodec.get(back, "adjustment")).isEqualTo(-12);
        assertThat(CompiledCodec.get(back, "units")).isEqualTo(1234567890L);
        assertThat(CompiledCodec.get(back, "fee")).isEqualTo(1500L);
        assertThat(CompiledCodec.get(back, "total")).isEqualTo(new BigDecimal("-1234.50"));
        assertThat(CompiledCodec.get(back, "discount")).isNull();
        assertThat(CompiledCodec.get(back, "check")).isEqualTo(new BigDecimal("5.00"));

        byte[] response = codec.encodeResponse(value, LATIN);
        Object target = codec.newInstance();
        codec.decodeResponse(response, target, LATIN);
        assertThat(CompiledCodec.get(target, "total")).isEqualTo(new BigDecimal("-1234.50"));
    }

    @Test
    void aBlankFieldReadsAsZeroForAPrimitive() {
        BookCodec<Object> codec = codec("test.Invoice", invoice(
            field("@BookField(order = 1, pic = \"ZZZ\")", "int", "lineCount"),
            field("@BookField(order = 2, pic = \"ZZZ\")", "Integer", "boxed")));
        Object value = codec.newInstance();

        byte[] request = codec.encodeRequest(value, LATIN);

        assertThat(text(request)).isEqualTo("      ");
        Object back = codec.decodeRequest(request, LATIN);
        assertThat(CompiledCodec.get(back, "lineCount")).isEqualTo(0);
        assertThat(CompiledCodec.get(back, "boxed")).isNull();
    }

    @Test
    void bookDefaultsDoNotChangeTheUsageOfAnEditedField() {
        Compilation compilation = compiler.compile(source("Invoice",
            "@BookDefaults(usage = Usage.COMP3, signed = true) @BookOperation",
            "public class Invoice {",
            field("@BookField(order = 1, pic = \"ZZ9.99\")", "BigDecimal", "total"),
            field("@BookField(order = 2, length = 3)", "int", "count"),
            "}"));

        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::usage)
            .containsExactly("DISPLAY", "COMP3");
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::byteLength)
            .containsExactly(6, 2);
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::signed)
            .containsExactly(false, true);
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::edited)
            .containsExactly("ZZ9.99", "");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "String|ZZ9|a numeric pic is not permitted for String",
        "LocalDate|99/99/99|an edited pic is not permitted for LocalDate. Use X(n) with a pattern",
        "LocalTime|99B99B99|an edited pic is not permitted for LocalTime. Use X(n) with a pattern",
        "int|ZZ9.99|scale must be 0 for int",
        "int|Z(9)9|length 10 is more than 9 digits for int",
        "int|ZZ9 COMP-3|an edited picture must have usage DISPLAY",
        "int|-9(3) SIGN LEADING|a SIGN clause is not permitted with an edited picture",
        "BigDecimal|S9(3) BLANK WHEN ZERO|BLANK WHEN ZERO is not permitted with S or '*'",
        "BigDecimal|Z*9|Z, *, and a floating string are mutually exclusive",
    })
    void rejectsAnIncorrectEditedField(String type, String pic, String phrase) {
        assertError(compiler.compile(invoice(
            field("@BookField(order = 1, pic = \"" + pic + "\")", type, "amount"))), phrase);
    }

    @Test
    void rejectsAnEditedPicWithOtherAttributes() {
        assertError(compiler.compile(invoice(
                field("@BookField(order = 1, pic = \"ZZ9\", usage = Usage.COMP3)", "int",
                    "amount"))),
            "Invoice.amount (order 1): pic must not be combined with usage");
    }

    @Test
    void rejectsAnEditedEnum() {
        assertError(compiler.compile(
                source("Priority", "public enum Priority {",
                    "  @CobolValue(\"1\") LOW, @CobolValue(\"2\") HIGH }"),
                invoice(field("@BookField(order = 1, pic = \"Z9\")", "Priority", "priority"))),
            "Invoice.priority (order 1): an edited pic is not permitted for an enum");
    }

    @Test
    void rejectsAnEditedFieldWithAConverter() {
        assertError(compiler.compile(
                source("FlagConverter",
                    "public class FlagConverter implements FieldConverter<Boolean> {",
                    "public Boolean decode(byte[] b, FieldContext c) { return null; }",
                    "public byte[] encode(Boolean v, FieldContext c) { return new byte[3]; }",
                    "}"),
                invoice(field("@BookField(order = 1, pic = \"ZZ9\", converter ="
                    + " FlagConverter.class)", "Boolean", "flag"))),
            "Invoice.flag (order 1): an edited pic is not permitted with a converter");
    }

    @Test
    void rejectsAnEditedCountField() {
        assertError(compiler.compile(
                invoice(
                    field("@BookField(order = 1, pic = \"Z9\")", "int", "lineCount"),
                    field("@BookCollection(order = 2, maxOccurs = 5)", "List<Line>", "lines")),
                source("Line", "public class Line {",
                    field("@BookField(order = 1, pic = \"X(2)\")", "String", "code"), "}")),
            "Invoice.lines (order 2): dependsOn field 'lineCount' must not have an edited pic");
    }

    @Test
    void rejectsAnEditedEmptyKey() {
        assertError(compiler.compile(
                invoice(field("@BookCollection(order = 2, maxOccurs = 5, fixed = true,"
                    + " emptyKey = \"quantity\")", "List<Line>", "lines")),
                source("Line", "public class Line {",
                    field("@BookField(order = 1, pic = \"ZZ9\")", "int", "quantity"), "}")),
            "Invoice.lines (order 2): emptyKey field 'quantity' must not have an edited pic");
    }

    @Test
    void rejectsAnEditedDiscriminator() {
        assertError(compiler.compile(
                source("GetAccount", "@BookOperation @BookSubTypes(discriminator = \"kind\","
                        + " value = {@BookSubTypes.Type(value = \"1\", type = Checking.class)})",
                    "public class GetAccount {",
                    field("@BookField(order = 1, pic = \"Z9\")", "int", "kind"), "}"),
                source("Checking", "public class Checking extends GetAccount {",
                    field("@BookField(order = 2, pic = \"X(2)\")", "String", "code"), "}")),
            "discriminator must not have an edited pic");
    }
}
