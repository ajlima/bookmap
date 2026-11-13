package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;
import static pt.dazz.bookmap.processor.TestCompiler.sourceIn;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

class EnumFieldTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private final TestCompiler compiler = TestCompiler.create();

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static JavaFileObject partyRole() {
        return source("PartyRole",
            "public enum PartyRole {",
            "  @CobolValue(\"SELLER\") SELLER,",
            "  @CobolValue(\"BILL-TO  \") BILL_TO,",
            "  @CobolValue(\"SHIP-TO\") SHIP_TO",
            "}");
    }

    /** A package-private enum with a range and a fallback constant without a value. */
    private static JavaFileObject grade() {
        return source("Grade",
            "enum Grade {",
            "  @CobolValue({\"A\", \"A+\"}) EXCELLENT,",
            "  @CobolValue(ranges = @CobolValue.Range(from = \"B\", thru = \"D\")) PASS,",
            "  @CobolUnknown OTHER",
            "}");
    }

    private static JavaFileObject status() {
        return source("Status",
            "public enum Status { @CobolValue(\" \") NONE, @CobolValue(\"A\") ACTIVE }");
    }

    /** A numeric enum. NORMAL writes 5 and reads 2 to 4.5. */
    private static JavaFileObject priority() {
        return source("Priority",
            "public enum Priority {",
            "  @CobolValue(\"1\") HIGH,",
            "  @CobolValue(value = \"5\", ranges = @CobolValue.Range(from = \"2\", thru = \"4.5\"))"
                + " NORMAL,",
            "  @CobolValue(\"-1.5\") NEGATIVE",
            "}");
    }

    /** The operation class. It has a nested numeric enum with a fallback constant. */
    private static JavaFileObject invoice() {
        return source("Invoice",
            "@BookOperation public class Invoice {",
            "public enum Tier { @CobolValue(\"1\") GOLD, @CobolUnknown @CobolValue(\"9\") OTHER }",
            field("@BookField(order = 1, pic = \"X(8)\")", "PartyRole", "seller"),
            field("@BookField(order = 2, pic = \"X(8)\")", "PartyRole", "buyer"),
            field("@BookField(order = 3, length = 2)", "Grade", "grade"),
            field("@BookField(order = 4, length = 1)", "Status", "status"),
            field("@BookField(order = 5, pic = \"S9(3)V9\")", "Priority", "priority"),
            field("@BookField(order = 6, pic = \"9 COMP-3\")", "Tier", "tier"),
            "}");
    }

    private Compilation compileInvoice() {
        Compilation compilation = compiler.compile(invoice(), partyRole(), grade(), status(),
            priority());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return compilation;
    }

    private static Object constant(BookCodec<Object> codec, String enumName, String name) {
        try {
            Class<?> type = Class.forName(enumName, true, codec.type().getClassLoader());
            return Arrays.stream(type.getEnumConstants())
                .filter(value -> ((Enum<?>) value).name().equals(name)).findFirst()
                .orElseThrow();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Gives the response bytes: the text fields, the zoned priority, and the packed tier. */
    private static byte[] response(String text, String priority, int tier) {
        byte[] head = bytes(text + priority);
        byte[] all = Arrays.copyOf(head, head.length + 1);
        all[head.length] = (byte) (tier << 4 | 0x0F);
        return all;
    }

    private static final String TEXT = "SELLER  BILL-TO B A";

    @Test
    void writesTheCanonicalValues() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();
        CompiledCodec.set(invoice, "seller", constant(codec, "test.PartyRole", "SELLER"));
        CompiledCodec.set(invoice, "buyer", constant(codec, "test.PartyRole", "BILL_TO"));
        CompiledCodec.set(invoice, "grade", constant(codec, "test.Grade", "PASS"));
        CompiledCodec.set(invoice, "status", constant(codec, "test.Status", "ACTIVE"));
        CompiledCodec.set(invoice, "priority", constant(codec, "test.Priority", "NORMAL"));
        CompiledCodec.set(invoice, "tier", constant(codec, "test.Invoice$Tier", "OTHER"));

        byte[] request = codec.encodeRequest(invoice, LATIN);

        assertThat(request).isEqualTo(response(TEXT, "005{", 9));
    }

    @Test
    void readsTheConstantThatContainsTheValue() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();

        codec.decodeResponse(response("SHIP-TO SELLER  A+A", "0010", 1), invoice, LATIN);

        assertThat(CompiledCodec.get(invoice, "seller")).hasToString("SHIP_TO");
        assertThat(CompiledCodec.get(invoice, "buyer")).hasToString("SELLER");
        assertThat(CompiledCodec.get(invoice, "grade")).hasToString("EXCELLENT");
        assertThat(CompiledCodec.get(invoice, "status")).hasToString("ACTIVE");
        assertThat(CompiledCodec.get(invoice, "priority")).hasToString("HIGH");
        assertThat(CompiledCodec.get(invoice, "tier")).hasToString("GOLD");
    }

    @Test
    void readsRangesNumbersAndFallbacks() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();

        // The overpunch N is a negative 5: the value is -1.5.
        codec.decodeResponse(response("SELLER  SELLER  Z A", "001N", 7), invoice, LATIN);

        assertThat(CompiledCodec.get(invoice, "grade")).hasToString("OTHER");
        assertThat(CompiledCodec.get(invoice, "priority")).hasToString("NEGATIVE");
        assertThat(CompiledCodec.get(invoice, "tier")).hasToString("OTHER");

        codec.decodeResponse(response("SELLER  SELLER  C A", "0030", 1), invoice, LATIN);

        assertThat(CompiledCodec.get(invoice, "grade")).hasToString("PASS");
        assertThat(CompiledCodec.get(invoice, "priority")).hasToString("NORMAL");
    }

    @Test
    void blankFieldsReadAsNullUnlessAConstantHasTheBlankValue() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();

        codec.decodeResponse(response(" ".repeat(19), "    ", 1), invoice, LATIN);

        assertThat(CompiledCodec.get(invoice, "seller")).isNull();
        assertThat(CompiledCodec.get(invoice, "grade")).isNull();
        assertThat(CompiledCodec.get(invoice, "status")).hasToString("NONE");
        assertThat(CompiledCodec.get(invoice, "priority")).isNull();
    }

    @Test
    void nullAndBlankConstantsWriteSpacesAndZeros() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();
        CompiledCodec.set(invoice, "status", constant(codec, "test.Status", "NONE"));
        CompiledCodec.set(invoice, "tier", constant(codec, "test.Invoice$Tier", "GOLD"));

        assertThat(codec.encodeRequest(invoice, LATIN))
            .isEqualTo(response(" ".repeat(19), "000{", 1));
    }

    @Test
    void unknownTextValueFails() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();

        assertThatThrownBy(() -> codec.decodeResponse(response("AGENT   " + TEXT.substring(8),
            "0010", 1), invoice, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("Invoice.seller (order 1): unknown value 'AGENT' for enum PartyRole");
    }

    @Test
    void unknownNumericValueFails() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();

        assertThatThrownBy(() -> codec.decodeResponse(response(TEXT, "0090", 1), invoice, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("Invoice.priority (order 5): unknown value '9.0' for enum Priority");
    }

    @Test
    void fallbackConstantWithoutValueCannotBeWritten() {
        BookCodec<Object> codec = CompiledCodec.load(compileInvoice(), "test.Invoice");
        Object invoice = codec.newInstance();
        CompiledCodec.set(invoice, "grade", constant(codec, "test.Grade", "OTHER"));

        assertThatThrownBy(() -> codec.encodeRequest(invoice, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("Invoice.grade (order 3): the constant OTHER of enum Grade has no COBOL"
                + " value");
    }

    @Test
    void fieldsOfOneEnumShareTheMethods() throws IOException {
        Compilation compilation = compileInvoice();
        String code = compilation.generatedSourceFile("test.Invoice_BookCodec").orElseThrow()
            .getCharContent(true).toString();

        assertThat(code.split("private static PartyRole partyRoleOf\\(", -1)).hasSize(2);
        assertThat(code).contains("private static Invoice.Tier invoiceTierOf(")
            .doesNotContain("switch").doesNotContain("valueOf(");
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::javaKind)
            .containsOnly("ENUM");
    }

    @Test
    void enumWorksInAnItemClass() {
        Compilation compilation = compiler.compile(partyRole(),
            source("Party", "public class Party {",
                field("@BookField(order = 1, pic = \"X(8)\")", "PartyRole", "role"), "}"),
            source("Parties", "@BookOperation public class Parties {",
                field("@BookCollection(order = 1, maxOccurs = 2, fixed = true)",
                    "java.util.List<Party>", "parties"), "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Parties");
        Object parties = codec.newInstance();

        codec.decodeResponse(bytes("SHIP-TO " + "\0".repeat(8)), parties, LATIN);

        Object party = ((java.util.List<?>) CompiledCodec.get(parties, "parties")).get(0);
        assertThat(CompiledCodec.get(party, "role")).hasToString("SHIP_TO");
    }

    @Test
    void converterTakesPrecedence() {
        Compilation compilation = compiler.compile(partyRole(),
            source("RoleConverter",
                "public class RoleConverter implements FieldConverter<PartyRole> {",
                "public PartyRole decode(byte[] b, FieldContext c) { return PartyRole.SELLER; }",
                "public byte[] encode(PartyRole v, FieldContext c) { return new byte[1]; }",
                "}"),
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1, length = 1, converter = RoleConverter.class)",
                    "PartyRole", "role"), "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).javaKind()).isEqualTo("CONVERTED");
    }

    // ---- compile errors ----

    private Compilation compileEnum(String fieldAnnotation, String... enumLines) {
        return compiler.compile(source("Code", enumLines),
            source("Order", "@BookOperation public class Order {",
                field(fieldAnnotation, "Code", "code"), "}"));
    }

    private static void assertError(Compilation compilation, String phrase) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Order.code (order 1): " + phrase);
    }

    private static final String TEXT_FIELD = "@BookField(order = 1, pic = \"X(3)\")";
    private static final String NUMBER_FIELD = "@BookField(order = 1, pic = \"S9(3)V9\")";
    private static final String UNSIGNED_FIELD = "@BookField(order = 1, pic = \"9(2)\")";

    @Test
    void privateEnumIsRejected() {
        Compilation nested = compiler.compile(source("Order",
            "@BookOperation public class Order {",
            "private enum Code { @CobolValue(\"A\") A }",
            field("@BookField(order = 1, length = 1)", "Code", "code"), "}"));
        assertError(nested, "enum Code: the enum must be public, or in the package test");
    }

    @Test
    void packagePrivateEnumOfAnotherPackageIsRejected() {
        Compilation compilation = compiler.compile(
            sourceIn("other", "Base", "public class Base {",
                "protected enum Code { @CobolValue(\"A\") A }",
                field("@BookField(order = 1, length = 1)", "Code", "code"), "}"),
            source("Order", "@BookOperation public class Order extends other.Base {",
                TestCompiler.ID_FIELD.replace("order = 1", "order = 2"), "}"));
        CompilationSubject.assertThat(compilation).hadErrorContaining(
            "Base.code (order 1): enum Code: the enum must be public, or in the package test");
    }

    @Test
    void enumNeedsAConstantWithAValue() {
        assertError(compileEnum(TEXT_FIELD, "public enum Code { @CobolUnknown A }"),
            "enum Code: no constant has @CobolValue");
    }

    @Test
    void eachConstantNeedsAValue() {
        assertError(compileEnum(TEXT_FIELD,
                "public enum Code { @CobolValue(\"A\") A, B, @CobolValue({}) C }"),
            "enum Code: the constant B has no @CobolValue");
        assertError(compileEnum(TEXT_FIELD,
                "public enum Code { @CobolValue(\"A\") A, @CobolValue({}) C }"),
            "enum Code: the constant C has no value");
    }

    @Test
    void onlyOneFallbackConstant() {
        assertError(compileEnum(TEXT_FIELD,
                "public enum Code { @CobolValue(\"A\") A, @CobolUnknown B, @CobolUnknown C }"),
            "enum Code: only one constant can have @CobolUnknown: B, C");
    }

    @Test
    void textValueMustFitTheField() {
        assertError(compileEnum(TEXT_FIELD,
                "public enum Code { @CobolValue(\"ABCD  \") A }"),
            "enum Code: the value 'ABCD' of A is longer than 3 characters");
    }

    @Test
    void numericValueMustBeADecimalNumber() {
        assertError(compileEnum(NUMBER_FIELD, "public enum Code { @CobolValue(\"1e3\") A }"),
            "enum Code: the value '1e3' of A is not a decimal number");
    }

    @Test
    void numericValueMustFitThePic() {
        assertError(compileEnum(UNSIGNED_FIELD, "public enum Code { @CobolValue(\"-1\") A }"),
            "enum Code: the value '-1' of A is negative, but the field has no sign");
        assertError(compileEnum(NUMBER_FIELD, "public enum Code { @CobolValue(\"1.25\") A }"),
            "enum Code: the value '1.25' of A has more than 1 decimal places");
        assertError(compileEnum(NUMBER_FIELD, "public enum Code { @CobolValue(\"1000\") A }"),
            "enum Code: the value '1000' of A has more than 4 digits");
    }

    @Test
    void numericValueWithTrailingZerosFits() {
        Compilation compilation = compileEnum(NUMBER_FIELD,
            "public enum Code { @CobolValue({\"1.50\", \"100\"}) A }");
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    void rangeMustNotBeEmpty() {
        assertError(compileEnum(TEXT_FIELD, "public enum Code {",
                "@CobolValue(ranges = @CobolValue.Range(from = \"C\", thru = \"A\")) A }"),
            "enum Code: the range 'C' THRU 'A' of A is empty: the first value is more than the"
                + " last value");
    }

    @Test
    void rangeBoundsAreChecked() {
        Compilation from = compileEnum(UNSIGNED_FIELD, "public enum Code {",
            "@CobolValue(ranges = @CobolValue.Range(from = \"-1\", thru = \"5\")) A }");
        assertError(from, "enum Code: the value '-1' of A is negative");
        CompilationSubject.assertThat(from).hadErrorCount(1);
        Compilation thru = compileEnum(UNSIGNED_FIELD, "public enum Code {",
            "@CobolValue(ranges = @CobolValue.Range(from = \"1\", thru = \"x\")) A }");
        assertError(thru, "enum Code: the value 'x' of A is not a decimal number");
        CompilationSubject.assertThat(thru).hadErrorCount(1);
    }

    @Test
    void constantsMustNotShareValues() {
        Compilation compilation = compileEnum(UNSIGNED_FIELD, "public enum Code {",
            "@CobolValue({\"1\", \"01\"}) A,",
            "@CobolValue(value = \"1\", ranges = @CobolValue.Range(from = \"5\", thru = \"9\")) B,",
            "@CobolValue(value = \"7\", ranges = @CobolValue.Range(from = \"9\", thru = \"12\")) C",
            "}");
        assertError(compilation, "enum Code: the value '1' of B overlaps the value '1' of A");
        assertError(compilation,
            "enum Code: the value '7' of C overlaps the range '5' THRU '9' of B");
        assertError(compilation,
            "enum Code: the range '9' THRU '12' of C overlaps the range '5' THRU '9' of B");
        CompilationSubject.assertThat(compilation).hadErrorCount(4);
    }

    @Test
    void valuesOfOneConstantCanOverlap() {
        Compilation compilation = compileEnum(TEXT_FIELD, "public enum Code {",
            "@CobolValue(value = \"B\", ranges = @CobolValue.Range(from = \"A\", thru = \"C\")) A,",
            "@CobolValue(\"D\") D }");
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    void unresolvedValueIsLeftToJavac() {
        Compilation compilation = compileEnum(TEXT_FIELD,
            "public enum Code { @CobolValue(MISSING) A }");
        CompilationSubject.assertThat(compilation).hadErrorContaining("cannot find symbol");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void textEnumHasTheRulesOfAString() {
        assertError(compileEnum("@BookField(order = 1, length = 1, usage = Usage.COMP3)",
                "public enum Code { @CobolValue(\"A\") A }"),
            "COMP3 is not permitted for Code");
        assertError(compileEnum("@BookField(order = 1, length = 1, scale = 1)",
                "public enum Code { @CobolValue(\"A\") A }"),
            "scale is not permitted for Code");
    }

    @Test
    void numericEnumUsesTheDefaultUsage() {
        Compilation compilation = compiler.compile(
            source("Code", "public enum Code { @CobolValue(\"1\") A }"),
            source("Order", "@BookOperation @BookDefaults(usage = Usage.COMP3)",
                "public class Order {",
                field("@BookField(order = 1, pic = \"9(3)\")", "Code", "code"),
                field("@BookField(order = 2, length = 1)", "Code", "letter"), "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::usage)
            .containsExactly("COMP3", "DISPLAY");
    }

    @Test
    void enumIsNotADiscriminator() {
        Compilation compilation = compiler.compile(
            source("Code", "public enum Code { @CobolValue(\"01\") A }"),
            source("Account", "@BookOperation",
                "@BookSubTypes(discriminator = \"code\", value = @BookSubTypes.Type(value = \"01\","
                    + " type = Checking.class))",
                "public abstract class Account {",
                field("@BookField(order = 1, pic = \"X(2)\")", "Code", "code"), "}"),
            source("Checking", "public class Checking extends Account {",
                field("@BookField(order = 2, length = 1)", "String", "flag"), "}"));
        CompilationSubject.assertThat(compilation).hadErrorContaining(
            "Account.code (order 1): discriminator must be String, int, Integer, long, or Long");
    }
}
