package pt.daaz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static pt.daaz.bookmap.processor.TestCompiler.accessors;
import static pt.daaz.bookmap.processor.TestCompiler.lombok;
import static pt.daaz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.convert.CodecContext;
import pt.daaz.bookmap.convert.FieldContext;
import pt.daaz.bookmap.exception.MappingException;
import pt.daaz.bookmap.spi.BookCodec;

class GenerationTest {

    enum ProcessorOrder { LOMBOK_FIRST, BOOKMAP_FIRST }

    private static final String SERVICES = "META-INF/services/pt.daaz.bookmap.spi.BookCodec";
    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private final TestCompiler compiler = TestCompiler.create();

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /** customerId (INPUT, 10, order 20), region (INPUT_OUTPUT, 2, order 10), name (OUTPUT, 30, order 30). */
    private static JavaFileObject customerProfile() {
        return source("CustomerProfile",
            "@BookOperation public class CustomerProfile {",
            field("@BookField(order = 20, length = 10, direction = Direction.INPUT)", "String",
                "customerId"),
            field("@BookField(order = 10, length = 2)", "String", "region"),
            field("@BookField(order = 30, length = 30, direction = Direction.OUTPUT)", "String",
                "name"),
            "}");
    }

    private Compilation compileCustomerProfile() {
        Compilation compilation = compiler.compile(customerProfile());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return compilation;
    }

    private static JavaFileObject flagConverter() {
        return source("FlagConverter",
            "public class FlagConverter implements FieldConverter<Boolean> {",
            "public static FieldContext last;",
            "public Boolean decode(byte[] b, FieldContext c) {",
            "  last = c;",
            "  String s = new String(b, c.charset());",
            "  return s.equals(\"Y\") ? Boolean.TRUE : s.equals(\"N\") ? Boolean.FALSE : null;",
            "}",
            "public byte[] encode(Boolean v, FieldContext c) {",
            "  last = c;",
            "  return (v == null ? \" \" : v ? \"Y\" : \"N\").getBytes(c.charset());",
            "}",
            "}");
    }

    /** Converts one digit. A blank gives null. */
    private static JavaFileObject digitConverter() {
        return source("DigitConverter",
            "public class DigitConverter implements FieldConverter<Integer> {",
            "public Integer decode(byte[] b, FieldContext c) {",
            "  String s = new String(b, c.charset());",
            "  return s.isBlank() ? null : Integer.valueOf(s);",
            "}",
            "public byte[] encode(Integer v, FieldContext c) {",
            "  return String.valueOf(v).getBytes(c.charset());",
            "}",
            "}");
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
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
    void generatesCodecAndServicesFile() {
        Compilation compilation = compileCustomerProfile();

        CompilationSubject.assertThat(compilation)
            .generatedSourceFile("test.CustomerProfile_BookCodec");
        CompilationSubject.assertThat(compilation)
            .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICES)
            .contentsAsUtf8String().contains("test.CustomerProfile_BookCodec");
        String source = sourceOf(compilation, "test.CustomerProfile_BookCodec");
        assertThat(source)
            .contains("@Generated(\"pt.daaz.bookmap.processor.BookmapProcessor\")")
            .contains("public final class CustomerProfile_BookCodec implements "
                + "BookCodec<CustomerProfile>")
            .contains("public CustomerProfile_BookCodec()")
            .contains("private static final String")
            .contains("\"CustomerProfile.region (order 10)\"");
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.CustomerProfile");
        assertThat(codec.type().getName()).isEqualTo("test.CustomerProfile");
        assertThat(codec.newInstance().getClass()).isSameAs(codec.type());
    }

    @Test
    void requestContainsOnlyInputFieldsInOrder() {
        BookCodec<Object> codec = CompiledCodec.load(compileCustomerProfile(),
            "test.CustomerProfile");
        Object profile = codec.newInstance();
        CompiledCodec.set(profile, "customerId", "C-1001");
        CompiledCodec.set(profile, "region", "EU");
        CompiledCodec.set(profile, "name", "Ana Smith");

        byte[] request = codec.encodeRequest(profile, LATIN);

        assertThat(text(request)).isEqualTo("EUC-1001    ");
        Object decoded = codec.decodeRequest(request, LATIN);
        assertThat(CompiledCodec.get(decoded, "region")).isEqualTo("EU");
        assertThat(CompiledCodec.get(decoded, "customerId")).isEqualTo("C-1001");
        assertThat(CompiledCodec.get(decoded, "name")).isNull();
    }

    @Test
    void responseDecodesOnlyOutputFields() {
        BookCodec<Object> codec = CompiledCodec.load(compileCustomerProfile(),
            "test.CustomerProfile");
        Object profile = codec.newInstance();
        CompiledCodec.set(profile, "customerId", "C-1001");
        String response = "US" + String.format("%-30s", "Ana Smith");

        codec.decodeResponse(bytes(response), profile, LATIN);

        assertThat(CompiledCodec.get(profile, "region")).isEqualTo("US");
        assertThat(CompiledCodec.get(profile, "name")).isEqualTo("Ana Smith");
        assertThat(CompiledCodec.get(profile, "customerId")).isEqualTo("C-1001");
        assertThat(text(codec.encodeResponse(profile, LATIN))).isEqualTo(response);
    }

    private static JavaFileObject allTypes() {
        return source("AllTypes",
            "@BookOperation public class AllTypes {",
            field("@BookField(order = 1, length = 5)", "String", "text"),
            field("@BookField(order = 2, length = 5)", "int", "count"),
            field("@BookField(order = 3, length = 5, signed = true)", "Integer", "boxedCount"),
            field("@BookField(order = 4, length = 12, usage = Usage.COMP)", "long", "total"),
            field("@BookField(order = 5, length = 12, signed = true, usage = Usage.COMP3)",
                "Long", "boxedTotal"),
            field("@BookField(order = 6, length = 15, scale = 2, signed = true,"
                + " usage = Usage.COMP3)", "BigDecimal", "amount"),
            field("@BookField(order = 7, length = 9, scale = 4, usage = Usage.COMP)",
                "BigDecimal", "rate"),
            field("@BookField(order = 8)", "LocalDate", "day"),
            field("@BookField(order = 9, pattern = \"HHmmss\")", "LocalTime", "time"),
            field("@BookField(order = 10, pattern = \"yyyyMMddHHmmss\")", "LocalDateTime",
                "stamp"),
            field("@BookField(order = 11, length = 1, converter = FlagConverter.class)",
                "Boolean", "express"),
            field("@BookField(order = 12, length = 1, converter = DigitConverter.class)", "int",
                "level"),
            "}");
    }

    private BookCodec<Object> allTypesCodec() {
        Compilation compilation = compiler.compile(allTypes(), flagConverter(), digitConverter());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test.AllTypes");
    }

    private static final List<String> ALL_TYPES_FIELDS = List.of("text", "count", "boxedCount",
        "total", "boxedTotal", "amount", "rate", "day", "time", "stamp", "express", "level");

    private static Object filledAllTypes(BookCodec<Object> codec) {
        Object value = codec.newInstance();
        CompiledCodec.set(value, "text", "Box");
        CompiledCodec.set(value, "count", 12345);
        CompiledCodec.set(value, "boxedCount", -42);
        CompiledCodec.set(value, "total", 999_999_999_999L);
        CompiledCodec.set(value, "boxedTotal", -123_456_789_012L);
        CompiledCodec.set(value, "amount", new BigDecimal("-1234567890123.45"));
        CompiledCodec.set(value, "rate", new BigDecimal("12.3456"));
        CompiledCodec.set(value, "day", LocalDate.of(2026, 11, 11));
        CompiledCodec.set(value, "time", LocalTime.of(9, 30, 15));
        CompiledCodec.set(value, "stamp", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        CompiledCodec.set(value, "express", Boolean.TRUE);
        CompiledCodec.set(value, "level", 7);
        return value;
    }

    @Test
    void allTypesRoundTrip() {
        BookCodec<Object> codec = allTypesCodec();
        Object value = filledAllTypes(codec);

        Object response = codec.newInstance();
        codec.decodeResponse(codec.encodeResponse(value, LATIN), response, LATIN);
        Object request = codec.decodeRequest(codec.encodeRequest(value, LATIN), LATIN);

        for (String name : ALL_TYPES_FIELDS) {
            assertThat(CompiledCodec.get(response, name)).as(name)
                .isEqualTo(CompiledCodec.get(value, name));
            assertThat(CompiledCodec.get(request, name)).as(name)
                .isEqualTo(CompiledCodec.get(value, name));
        }
        assertThat(codec.maxResponseLength()).isEqualTo(5 + 5 + 5 + 8 + 7 + 8 + 4 + 8 + 6 + 14 + 1 + 1);
    }

    @Test
    void converterGetsTheFieldContext() throws ReflectiveOperationException {
        BookCodec<Object> codec = allTypesCodec();
        codec.encodeRequest(filledAllTypes(codec), LATIN);

        FieldContext context = (FieldContext) codec.getClass().getClassLoader()
            .loadClass("test.FlagConverter").getField("last").get(null);
        assertThat(context).isEqualTo(new FieldContext("express", 1, 0, false,
            Usage.DISPLAY, "yyyyMMdd", 1, StandardCharsets.ISO_8859_1));
    }

    @Test
    void nullValuesWriteBlanksOrZeros() {
        BookCodec<Object> codec = allTypesCodec();
        Object empty = codec.newInstance();

        byte[] request = codec.encodeRequest(empty, LATIN);

        assertThat(request).hasSize(codec.maxResponseLength());
        Object decoded = codec.decodeRequest(request, LATIN);
        assertThat(CompiledCodec.get(decoded, "text")).isEqualTo("");
        assertThat(CompiledCodec.get(decoded, "boxedCount")).isEqualTo(0);
        assertThat(CompiledCodec.get(decoded, "boxedTotal")).isEqualTo(0L);
        assertThat((BigDecimal) CompiledCodec.get(decoded, "amount")).isZero();
        assertThat(CompiledCodec.get(decoded, "day")).isNull();
        assertThat(CompiledCodec.get(decoded, "time")).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(CompiledCodec.get(decoded, "stamp")).isNull();
        assertThat(CompiledCodec.get(decoded, "express")).isNull();
        assertThat(CompiledCodec.get(decoded, "level")).isEqualTo(0);
    }

    private static JavaFileObject counts() {
        return source("Counts",
            "@BookOperation public class Counts {",
            field("@BookField(order = 1, length = 3, direction = Direction.OUTPUT)", "int", "a"),
            field("@BookField(order = 2, length = 5, direction = Direction.OUTPUT)", "long", "b"),
            field("@BookField(order = 3, length = 3, direction = Direction.OUTPUT)", "Integer",
                "c"),
            field("@BookField(order = 4, length = 3, direction = Direction.OUTPUT)", "Long", "d"),
            field("@BookField(order = 5, length = 9, usage = Usage.COMP,"
                + " direction = Direction.OUTPUT)", "int", "e"),
            field("@BookField(order = 6, length = 9, usage = Usage.COMP,"
                + " direction = Direction.OUTPUT)", "Integer", "f"),
            field("@BookField(order = 7, length = 18, usage = Usage.COMP,"
                + " direction = Direction.OUTPUT)", "long", "g"),
            field("@BookField(order = 8, length = 18, usage = Usage.COMP,"
                + " direction = Direction.OUTPUT)", "Long", "h"),
            "}");
    }

    private BookCodec<Object> countsCodec() {
        Compilation compilation = compiler.compile(counts());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test.Counts");
    }

    @Test
    void primitiveReadsZeroFromBlank() {
        BookCodec<Object> codec = countsCodec();
        Object counts = codec.newInstance();
        CompiledCodec.set(counts, "a", 5);
        CompiledCodec.set(counts, "b", 6L);
        CompiledCodec.set(counts, "c", 7);
        CompiledCodec.set(counts, "d", 8L);
        byte[] data = new byte[codec.maxResponseLength()];
        Arrays.fill(data, 0, 14, (byte) ' ');

        codec.decodeResponse(data, counts, LATIN);

        assertThat(CompiledCodec.get(counts, "a")).isEqualTo(0);
        assertThat(CompiledCodec.get(counts, "b")).isEqualTo(0L);
        assertThat(CompiledCodec.get(counts, "c")).isNull();
        assertThat(CompiledCodec.get(counts, "d")).isNull();
    }

    @Test
    void binaryValueThatDoesNotFitTheJavaTypeFails() {
        BookCodec<Object> codec = countsCodec();
        byte[] blank = new byte[14];
        Arrays.fill(blank, (byte) ' ');
        byte[] ones = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] zeros4 = new byte[4];
        byte[] ones8 = new byte[8];
        Arrays.fill(ones8, (byte) 0xFF);
        byte[] zeros8 = new byte[8];

        assertThatThrownBy(() -> codec.decodeResponse(concat(blank, ones, zeros4, zeros8, zeros8),
            codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith("Counts.e (order 5): value 4294967295 does not fit int");
        assertThatThrownBy(() -> codec.decodeResponse(concat(blank, zeros4, ones, zeros8, zeros8),
            codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith("Counts.f (order 6): value 4294967295 does not fit Integer");
        assertThatThrownBy(() -> codec.decodeResponse(concat(blank, zeros4, zeros4, ones8, zeros8),
            codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith("Counts.g (order 7): value 18446744073709551615 does not fit long");
        assertThatThrownBy(() -> codec.decodeResponse(concat(blank, zeros4, zeros4, zeros8, ones8),
            codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith("Counts.h (order 8): value 18446744073709551615 does not fit Long");
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        byte[] result = new byte[length];
        int position = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, position, part.length);
            position += part.length;
        }
        return result;
    }

    @Test
    void errorMessageContainsDescriptor() {
        BookCodec<Object> codec = CompiledCodec.load(compileCustomerProfile(),
            "test.CustomerProfile");
        Object profile = codec.newInstance();
        CompiledCodec.set(profile, "name", "N".repeat(31));

        assertThatThrownBy(() -> codec.encodeResponse(profile, LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith("CustomerProfile.name (order 30)");
    }

    @Test
    void maxResponseLengthIsSumOfOutputFields() {
        BookCodec<Object> codec = CompiledCodec.load(compileCustomerProfile(),
            "test.CustomerProfile");

        assertThat(codec.maxResponseLength()).isEqualTo(32);
    }

    @Test
    void conventionAndExplicitFormsGiveSameBytes() {
        Compilation compilation = compiler.compile(
            source("Picked",
                "@BookOperation public class Picked {",
                field("@BookField(order = 1, pic = \"S9(13)V99 COMP-3\")", "BigDecimal",
                    "amount"),
                field("@BookField(order = 2)", "LocalDate", "day"),
                "}"),
            source("Explicit",
                "@BookOperation public class Explicit {",
                field("@BookField(order = 1, length = 15, scale = 2, signed = true,"
                    + " usage = Usage.COMP3)", "BigDecimal", "amount"),
                field("@BookField(order = 2, length = 8)", "LocalDate", "day"),
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> picked = CompiledCodec.load(compilation, "test.Picked");
        BookCodec<Object> explicit = CompiledCodec.load(compilation, "test.Explicit");
        Object a = picked.newInstance();
        Object b = explicit.newInstance();
        for (Object value : List.of(a, b)) {
            CompiledCodec.set(value, "amount", new BigDecimal("-98765.43"));
            CompiledCodec.set(value, "day", LocalDate.of(2026, 3, 9));
        }

        assertThat(picked.encodeRequest(a, CodecContext.defaults()))
            .isEqualTo(explicit.encodeRequest(b, CodecContext.defaults()))
            .hasSize(16);
    }

    @Test
    void generatedOperationNameAndCharset() {
        Compilation compilation = compiler.compile(
            source("Shipment",
                "@BookDefaults(charset = \"IBM500\") @BookOperation public class Shipment {",
                field("@BookField(order = 1, length = 4)", "String", "id"),
                "}"),
            source("Invoice",
                "@BookOperation(\"INVQ\") public class Invoice {",
                field("@BookField(order = 1, length = 4)", "String", "id"),
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();

        BookCodec<Object> shipment = CompiledCodec.load(compilation, "test.Shipment");
        BookCodec<Object> invoice = CompiledCodec.load(compilation, "test.Invoice");
        assertThat(shipment.operationName()).isEqualTo("Shipment");
        assertThat(shipment.charsetName()).isEqualTo("IBM500");
        assertThat(invoice.operationName()).isEqualTo("INVQ");
        assertThat(invoice.charsetName()).isEmpty();
    }

    @Test
    void extraTrailingBytesAreIgnored() {
        BookCodec<Object> codec = CompiledCodec.load(compileCustomerProfile(),
            "test.CustomerProfile");
        Object profile = codec.newInstance();

        codec.decodeResponse(bytes("EU" + String.format("%-30s", "Ana") + " ".repeat(10)),
            profile, LATIN);

        assertThat(CompiledCodec.get(profile, "name")).isEqualTo("Ana");
    }

    @Test
    void nestedClassCodecName() {
        Compilation compilation = compiler.compile(source("Outer",
            "public class Outer {",
            "@BookOperation public static class Inner {",
            field("@BookField(order = 1, length = 4)", "String", "id"),
            "}",
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();

        CompilationSubject.assertThat(compilation).generatedSourceFile("test.Outer_Inner_BookCodec");
        assertThat(sourceOf(compilation, "test.Outer_Inner_BookCodec"))
            .contains("implements BookCodec<Outer.Inner>");
        CompilationSubject.assertThat(compilation)
            .generatedFile(StandardLocation.CLASS_OUTPUT, SERVICES)
            .contentsAsUtf8String().contains("test.Outer_Inner_BookCodec");
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Outer$Inner");
        assertThat(codec.type().getName()).isEqualTo("test.Outer$Inner");
        assertThat(codec.operationName()).isEqualTo("Inner");
    }

    @Test
    void converterNullIntoPrimitiveFails() {
        Compilation compilation = compiler.compile(
            source("Stock",
                "@BookOperation public class Stock {",
                field("@BookField(order = 4, length = 1, converter = DigitConverter.class)", "int",
                    "level"),
                "}"),
            digitConverter());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Stock");

        assertThatThrownBy(() -> codec.decodeResponse(bytes(" "), codec.newInstance(), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessage("Stock.level (order 4): converter returned null for a primitive field");
        assertThatThrownBy(() -> codec.decodeRequest(bytes(" "), LATIN))
            .isInstanceOf(MappingException.class)
            .hasMessageStartingWith("Stock.level (order 4)");
    }

    @Test
    void servicesFileMergesExistingEntries(@TempDir Path output) throws IOException {
        Path sources = Files.createDirectories(output.resolve("src/test"));
        Path services = output.resolve("classes/" + SERVICES);
        Path classes = output.resolve("classes");
        Files.writeString(sources.resolve("Alpha.java"), operation("Alpha"));
        Files.writeString(sources.resolve("Beta.java"), operation("Beta"));

        javac(classes, sources.resolve("Alpha.java"));
        assertThat(Files.readAllLines(services)).containsExactly("test.Alpha_BookCodec");
        Files.writeString(services, "# codecs\ntest.Alpha_BookCodec\n\ntest.Gone_BookCodec\n");
        javac(classes, sources.resolve("Beta.java"));

        assertThat(Files.readAllLines(services))
            .containsExactly("test.Alpha_BookCodec", "test.Beta_BookCodec");
    }

    private static String operation(String name) {
        return "package test;\n"
            + "import pt.daaz.bookmap.annotation.*;\n"
            + "@BookOperation public class " + name + " {\n"
            + field("@BookField(order = 1, length = 4)", "String", "id") + "\n"
            + "}\n";
    }

    private static void javac(Path classes, Path source) throws IOException {
        Files.createDirectories(classes);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, null)) {
            String classpath = System.getProperty("java.class.path")
                + java.io.File.pathSeparator + classes;
            JavaCompiler.CompilationTask task = javac.getTask(null, files, null,
                List.of("-d", classes.toString(), "-classpath", classpath, "-proc:full"), null,
                files.getJavaFileObjects(source));
            task.setProcessors(List.of(new BookmapProcessor()));
            assertThat(task.call()).isTrue();
        }
    }

    @Test
    void generatedCodeUsesNoReflection() {
        Compilation compilation = compiler.compile(allTypes(), flagConverter(), digitConverter());

        assertThat(sourceOf(compilation, "test.AllTypes_BookCodec"))
            .doesNotContain("java.lang.reflect")
            .doesNotContain("getDeclaredField")
            .doesNotContain("Class.forName");
    }

    // ---- extra cases ----

    @Test
    void codecNameThatIsAlreadyUsedFails() {
        Compilation compilation = compiler.compile(
            source("Outer",
                "public class Outer {",
                "@BookOperation public static class Inner {",
                field("@BookField(order = 1, length = 4)", "String", "id"),
                "}",
                "}"),
            source("Outer_Inner",
                "@BookOperation public class Outer_Inner {",
                field("@BookField(order = 1, length = 4)", "String", "id"),
                "}"));

        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("codec test.Outer_Inner_BookCodec could not be generated");
    }

    @Test
    void namesOfGeneratedMembersDoNotCollide() {
        Compilation compilation = compiler.compile(source("Clash",
            "@BookOperation public class Clash {",
            field("@BookField(order = 1, length = 2)", "String", "orderId"),
            field("@BookField(order = 2, length = 2)", "String", "order_id"),
            field("@BookField(order = 3, length = 2)", "String", "ORDER_ID"),
            field("@BookField(order = 4, length = 2)", "String", "data"),
            field("@BookField(order = 5, length = 2)", "String", "ctx"),
            field("@BookField(order = 6, length = 1, converter = FlagConverter.class)", "Boolean",
                "flag"),
            field("@BookField(order = 7, length = 1, converter = FlagConverter.class)", "Boolean",
                "flagConverter"),
            field("@BookField(order = 8)", "LocalDate", "day"),
            field("@BookField(order = 9)", "LocalDate", "dayFormat"),
            "}"),
            flagConverter());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Clash");
        Object value = codec.newInstance();
        CompiledCodec.set(value, "orderId", "A1");
        CompiledCodec.set(value, "order_id", "B2");
        CompiledCodec.set(value, "ORDER_ID", "C3");
        CompiledCodec.set(value, "data", "D4");
        CompiledCodec.set(value, "ctx", "E5");

        byte[] request = codec.encodeRequest(value, LATIN);

        assertThat(text(request)).startsWith("A1B2C3D4E5 ");
    }

    @Test
    void collectionWithTheDefaultDirectionIsOnlyInTheResponse() {
        Compilation compilation = compiler.compile(
            source("Basket",
                "@BookOperation public class Basket {",
                field("@BookField(order = 1, length = 2)", "int", "lineCount"),
                field("@BookCollection(order = 2, maxOccurs = 3)", "List<Line>", "lines"),
                field("@BookField(order = 3, length = 4)", "String", "note"),
                "}"),
            source("Line",
                "public class Line {",
                field("@BookField(order = 1, length = 5)", "String", "sku"),
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Basket");
        Object basket = codec.newInstance();
        CompiledCodec.set(basket, "note", "GIFT");

        // The collection and its count field are OUTPUT (the default direction of a collection).
        assertThat(text(codec.encodeRequest(basket, LATIN))).isEqualTo("GIFT");
        assertThat(text(codec.encodeResponse(basket, LATIN))).isEqualTo("00GIFT");
        assertThat(codec.maxResponseLength()).isEqualTo(2 + 3 * 5 + 4);
        codec.resetOutputCollections(basket);
        assertThat(CompiledCodec.get(basket, "lines")).isEqualTo(List.of());
    }

    // ---- R20: Lombok primitive boolean field with an "is" prefix ----

    private static JavaFileObject flags(String classAnnotations) {
        return source("Flags",
            classAnnotations + " @BookOperation public class Flags {",
            "@BookField(order = 1, length = 1, converter = FlagConverter.class)",
            "private boolean isActive;",
            "@BookField(order = 2, length = 1, converter = FlagConverter.class)",
            "private Boolean isGift;",
            "@BookField(order = 3, length = 1, converter = FlagConverter.class)",
            "private boolean is_open;",
            "@BookField(order = 4, length = 1, converter = FlagConverter.class)",
            "private boolean is2fa;",
            "}");
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void lombokBooleanFieldWithIsPrefix(ProcessorOrder order) {
        List<Processor> processors = order == ProcessorOrder.LOMBOK_FIRST
            ? List.of(lombok(), compiler.processor())
            : List.of(compiler.processor(), lombok());
        Compilation compilation = compiler.compileWithProcessors(processors,
            flags("@lombok.Getter @lombok.Setter"), flagConverter());

        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members())
            .extracting(ModelSnapshot.Member::getter, ModelSnapshot.Member::setter)
            .containsExactly(tuple("isActive", "setActive"),
                tuple("getIsGift", "setIsGift"), tuple("is_open", "set_open"),
                tuple("is2fa", "set2fa"));
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.Flags");
        Object flags = codec.newInstance();
        codec.decodeResponse(bytes("YNYY"), flags, LATIN);
        assertThat(CompiledCodec.get(flags, "active")).isEqualTo(true);
        assertThat(CompiledCodec.get(flags, "isGift")).isEqualTo(false);
        assertThat(CompiledCodec.get(flags, "_open")).isEqualTo(true);
        assertThat(CompiledCodec.get(flags, "2fa")).isEqualTo(true);
    }
}
