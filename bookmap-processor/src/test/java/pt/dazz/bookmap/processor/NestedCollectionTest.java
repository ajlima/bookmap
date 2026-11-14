package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.spi.BookCodec;

/** Tests a collection in an item class (a nested collection). */
class NestedCollectionTest {

    private static final CodecContext LATIN = new CodecContext(StandardCharsets.ISO_8859_1);

    private final TestCompiler compiler = TestCompiler.create();

    // ---- test sources ----

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /**
     * orderId (INPUT, 4), lines (OUTPUT, fixed, maxOccurs 2), status (OUTPUT, 2).
     *
     * @param lines the attributes of the lines collection after {@code order = 20}
     */
    private static JavaFileObject getOrder(String lines) {
        return source("GetOrder",
            "@BookOperation public class GetOrder {",
            field("@BookField(order = 10, pic = \"X(4)\", direction = Direction.INPUT)",
                "String", "orderId"),
            field("@BookCollection(order = 20" + lines + ")", "List<OrderLine>", "lines"),
            field("@BookField(order = 30, pic = \"X(2)\", direction = Direction.OUTPUT)",
                "String", "status"),
            "}");
    }

    private static final String FIXED_LINES = ", maxOccurs = 2, fixed = true";

    /**
     * productCode (3), taxCount (1), taxes (fixed, maxOccurs 2): 4 + 2 x 5 = 14 bytes.
     *
     * @param taxes the attributes of the taxes collection after {@code order = 30}
     */
    private static JavaFileObject orderLine(String taxes) {
        return source("OrderLine", "public class OrderLine {",
            field("@BookField(order = 10, pic = \"X(3)\")", "String", "productCode"),
            field("@BookField(order = 20, pic = \"9\")", "int", "taxCount"),
            field("@BookCollection(order = 30" + taxes + ")", "List<LineTax>", "taxes"),
            "}");
    }

    private static final String COUNTED_TAXES = ", maxOccurs = 2, fixed = true,"
        + " dependsOn = \"taxCount\"";

    /** taxCode (2), amount (3): 5 bytes. */
    private static JavaFileObject lineTax() {
        return source("LineTax", "public class LineTax {",
            field("@BookField(order = 10, pic = \"X(2)\")", "String", "taxCode"),
            field("@BookField(order = 20, pic = \"9(3)\")", "int", "amount"),
            "}");
    }

    // ---- helpers ----

    private Compilation compile(JavaFileObject... files) {
        return compiler.compile(files);
    }

    private BookCodec<Object> codec(String name, JavaFileObject... files) {
        Compilation compilation = compile(files);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return CompiledCodec.load(compilation, "test." + name);
    }

    private static void assertError(Compilation compilation, String descriptor, String phrase) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(descriptor + ": " + phrase);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    static Object newObject(BookCodec<Object> codec, String simpleName) {
        try {
            return codec.getClass().getClassLoader().loadClass("test." + simpleName)
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object tax(BookCodec<Object> codec, String code, int amount) {
        Object tax = newObject(codec, "LineTax");
        CompiledCodec.set(tax, "taxCode", code);
        CompiledCodec.set(tax, "amount", amount);
        return tax;
    }

    private static Object line(BookCodec<Object> codec, String product, Object... taxes) {
        Object line = newObject(codec, "OrderLine");
        CompiledCodec.set(line, "productCode", product);
        CompiledCodec.set(line, "taxes", new ArrayList<>(List.of(taxes)));
        return line;
    }

    private static Object order(BookCodec<Object> codec, Object... lines) {
        Object order = codec.newInstance();
        CompiledCodec.set(order, "lines", new ArrayList<>(List.of(lines)));
        CompiledCodec.set(order, "status", "OK");
        return order;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object bean, String property) {
        return (List<Object>) CompiledCodec.get(bean, property);
    }

    private static Object decodeResponse(BookCodec<Object> codec, byte[] bytes) {
        Object target = codec.newInstance();
        codec.decodeResponse(bytes, target, LATIN);
        return target;
    }

    // ---- fixed in fixed ----

    @Test
    void fixedInFixedCountModeWritesAndReadsBothLevels() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(COUNTED_TAXES), lineTax());
        Object line = line(codec, "P01", tax(codec, "VA", 10));
        byte[] bytes = codec.encodeResponse(order(codec, line), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P011VA010"), new byte[5], new byte[14],
            bytes("OK")));
        assertThat(CompiledCodec.get(line, "taxCount")).isEqualTo(1);
        assertThat(codec.maxResponseLength()).isEqualTo(30);

        Object decoded = decodeResponse(codec, bytes);
        List<Object> lines = list(decoded, "lines");
        assertThat(lines).hasSize(1);
        assertThat(CompiledCodec.get(lines.get(0), "productCode")).isEqualTo("P01");
        assertThat(list(lines.get(0), "taxes")).extracting(t -> CompiledCodec.get(t, "taxCode"),
            t -> CompiledCodec.get(t, "amount")).containsExactly(
                org.assertj.core.groups.Tuple.tuple("VA", 10));
        assertThat(CompiledCodec.get(decoded, "status")).isEqualTo("OK");
    }

    @Test
    void countModeIgnoresTheUnusedInnerSlots() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(COUNTED_TAXES), lineTax());
        Object decoded = decodeResponse(codec, concat(bytes("P011VA010XX999"),
            bytes("P020YY111ZZ222"), bytes("OK")));
        List<Object> lines = list(decoded, "lines");
        assertThat(lines).hasSize(2);
        assertThat(list(lines.get(0), "taxes")).hasSize(1);
        assertThat(list(lines.get(1), "taxes")).isEmpty();
    }

    @Test
    void nullInnerListWritesTheCountZero() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(COUNTED_TAXES), lineTax());
        Object line = line(codec, "P01");
        CompiledCodec.set(line, "taxes", null);
        byte[] bytes = codec.encodeResponse(order(codec, line), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P010"), new byte[10], new byte[14],
            bytes("OK")));
    }

    @Test
    void innerLowValuesModeFindsTheUsedSlots() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(", maxOccurs = 2, fixed = true"), lineTax());
        byte[] bytes = codec.encodeResponse(order(codec,
            line(codec, "P01", tax(codec, "VA", 10), tax(codec, "EX", 0))), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P010VA010EX000"), new byte[14],
            bytes("OK")));
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        assertThat(list(lines.get(0), "taxes")).hasSize(2);
    }

    @Test
    void innerSpacesModeWritesSpacesInTheUnusedSlots() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(", maxOccurs = 2, fixed = true, emptyItem = EmptyItem.SPACES"),
            lineTax());
        byte[] bytes = codec.encodeResponse(order(codec,
            line(codec, "P01", tax(codec, "VA", 10))), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P010VA010     "), new byte[14],
            bytes("OK")));
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        assertThat(list(lines.get(0), "taxes")).hasSize(1);
    }

    @Test
    void innerKeyModeWritesZeroInTheUnusedKeys() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(", maxOccurs = 2, fixed = true, emptyKey = \"amount\","
                + " emptyItem = EmptyItem.SPACES"),
            lineTax());
        byte[] bytes = codec.encodeResponse(order(codec,
            line(codec, "P01", tax(codec, "VA", 10))), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P010VA010  000"), new byte[14],
            bytes("OK")));
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        assertThat(list(lines.get(0), "taxes")).hasSize(1);
    }

    @Test
    void outerKeyAfterAnInnerCollectionHasTheOffsetOfTheArea() {
        BookCodec<Object> codec = codec("GetOrder",
            getOrder(FIXED_LINES + ", emptyKey = \"productCode\""),
            source("OrderLine", "public class OrderLine {",
                field("@BookCollection(order = 10, maxOccurs = 1, fixed = true)",
                    "List<LineTax>", "taxes"),
                field("@BookField(order = 20, pic = \"X(3)\")", "String", "productCode"),
                "}"),
            lineTax());
        Object line = newObject(codec, "OrderLine");
        CompiledCodec.set(line, "taxes", List.of(tax(codec, "VA", 10)));
        CompiledCodec.set(line, "productCode", "P01");
        byte[] bytes = codec.encodeResponse(order(codec, line), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("VA010P01"), new byte[8], bytes("OK")));
        // The second slot has data in the inner area, but its key is empty.
        List<Object> lines = list(decodeResponse(codec, concat(bytes("VA010P01"),
            bytes("EX020   "), bytes("OK"))), "lines");
        assertThat(lines).hasSize(1);
    }

    // ---- fixed in variable ----

    @Test
    void fixedInVariableReadsEachEntryAtItsOffset() {
        BookCodec<Object> codec = codec("GetOrder",
            source("GetOrder", "@BookOperation public class GetOrder {",
                field("@BookField(order = 10, pic = \"9\", direction = Direction.OUTPUT)",
                    "int", "lineCount"),
                field("@BookCollection(order = 20, maxOccurs = 3)", "List<OrderLine>", "lines"),
                field("@BookField(order = 30, pic = \"X(2)\", direction = Direction.OUTPUT)",
                    "String", "status"),
                "}"),
            orderLine(COUNTED_TAXES), lineTax());
        byte[] bytes = codec.encodeResponse(order(codec,
            line(codec, "P01", tax(codec, "VA", 10)),
            line(codec, "P02", tax(codec, "EX", 1), tax(codec, "VR", 2))), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("2"), bytes("P011VA010"), new byte[5],
            bytes("P022EX001VR002"), bytes("OK")));
        assertThat(codec.maxResponseLength()).isEqualTo(1 + 3 * 14 + 2);
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        assertThat(lines).hasSize(2);
        assertThat(list(lines.get(1), "taxes")).extracting(t -> CompiledCodec.get(t, "taxCode"))
            .containsExactly("EX", "VR");
    }

    // ---- three levels ----

    @Test
    void threeLevelsWriteAndRead() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(COUNTED_TAXES),
            source("LineTax", "public class LineTax {",
                field("@BookField(order = 10, pic = \"X(2)\")", "String", "taxCode"),
                field("@BookCollection(order = 20, maxOccurs = 2, fixed = true)",
                    "List<TaxNote>", "notes"),
                "}"),
            source("TaxNote", "public class TaxNote {",
                field("@BookField(order = 10, pic = \"X\")", "String", "note"), "}"));
        Object note = newObject(codec, "TaxNote");
        CompiledCodec.set(note, "note", "A");
        Object tax = newObject(codec, "LineTax");
        CompiledCodec.set(tax, "taxCode", "VA");
        CompiledCodec.set(tax, "notes", List.of(note));
        byte[] bytes = codec.encodeResponse(order(codec, line(codec, "P01", tax)), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P011VAA"), new byte[1], new byte[4],
            new byte[12], bytes("OK")));
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        Object decodedTax = list(lines.get(0), "taxes").get(0);
        assertThat(list(decodedTax, "notes")).extracting(n -> CompiledCodec.get(n, "note"))
            .containsExactly("A");
    }

    // ---- subtypes ----

    /** kind (1): ProductLine has taxes (fixed, 2 x 5), ServiceLine has hours (2). */
    private static JavaFileObject[] lineSubtypes() {
        return new JavaFileObject[] {
            source("OrderLine",
                "@BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"P\", type = ProductLine.class),",
                "  @BookSubTypes.Type(value = \"S\", type = ServiceLine.class)})",
                "public abstract class OrderLine {",
                field("@BookField(order = 10, pic = \"X\")", "String", "kind"),
                "}"),
            source("ProductLine", "public class ProductLine extends OrderLine {",
                field("@BookCollection(order = 20, maxOccurs = 2, fixed = true)",
                    "List<LineTax>", "taxes"),
                "}"),
            source("ServiceLine", "public class ServiceLine extends OrderLine {",
                field("@BookField(order = 20, pic = \"9(2)\")", "int", "hours"), "}"),
            lineTax()
        };
    }

    @Test
    void innerCollectionInAnItemSubtype() {
        List<JavaFileObject> files = new ArrayList<>(List.of(getOrder(FIXED_LINES)));
        files.addAll(List.of(lineSubtypes()));
        BookCodec<Object> codec = codec("GetOrder", files.toArray(JavaFileObject[]::new));
        Object product = newObject(codec, "ProductLine");
        CompiledCodec.set(product, "taxes", List.of(tax(codec, "VA", 10)));
        Object service = newObject(codec, "ServiceLine");
        CompiledCodec.set(service, "hours", 8);
        byte[] bytes = codec.encodeResponse(order(codec, product, service), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("PVA010"), new byte[5], bytes("S08"),
            bytes("        "), bytes("OK")));
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        assertThat(lines).hasSize(2);
        assertThat(list(lines.get(0), "taxes")).hasSize(1);
        assertThat(CompiledCodec.get(lines.get(1), "hours")).isEqualTo(8);
    }

    @Test
    void innerItemWithSubtypes() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            orderLine(COUNTED_TAXES),
            source("LineTax",
                "@BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"R\", type = RateTax.class)})",
                "public class LineTax {",
                field("@BookField(order = 10, pic = \"X\")", "String", "kind"),
                "}"),
            source("RateTax", "public class RateTax extends LineTax {",
                field("@BookField(order = 20, pic = \"9(4)\")", "int", "rate"), "}"));
        Object rate = newObject(codec, "RateTax");
        CompiledCodec.set(rate, "rate", 2300);
        byte[] bytes = codec.encodeResponse(order(codec, line(codec, "P01", rate)), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("P011R2300"), new byte[5], new byte[14],
            bytes("OK")));
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        assertThat(CompiledCodec.get(list(lines.get(0), "taxes").get(0), "rate"))
            .isEqualTo(2300);
    }

    @Test
    void innerCollectionInTheBaseClassOfItemSubtypes() {
        BookCodec<Object> codec = codec("GetOrder", getOrder(FIXED_LINES),
            source("OrderLine",
                "@BookSubTypes(discriminator = \"kind\", value = {",
                "  @BookSubTypes.Type(value = \"P\", type = ProductLine.class),",
                "  @BookSubTypes.Type(value = \"S\", type = ServiceLine.class)})",
                "public abstract class OrderLine {",
                field("@BookField(order = 10, pic = \"X\")", "String", "kind"),
                field("@BookCollection(order = 20, maxOccurs = 1, fixed = true)",
                    "List<LineTax>", "taxes"),
                "}"),
            source("ProductLine", "public class ProductLine extends OrderLine {",
                field("@BookField(order = 30, pic = \"X(2)\")", "String", "code"), "}"),
            source("ServiceLine", "public class ServiceLine extends OrderLine {",
                field("@BookField(order = 30, pic = \"9\")", "int", "hours"), "}"),
            lineTax());
        Object service = newObject(codec, "ServiceLine");
        CompiledCodec.set(service, "taxes", List.of(tax(codec, "VA", 10)));
        CompiledCodec.set(service, "hours", 8);
        byte[] bytes = codec.encodeResponse(order(codec, service), LATIN);
        assertThat(bytes).isEqualTo(concat(bytes("SVA0108 "), new byte[8], bytes("OK")));
        List<Object> lines = list(decodeResponse(codec, bytes), "lines");
        assertThat(list(lines.get(0), "taxes")).hasSize(1);
        assertThat(CompiledCodec.get(lines.get(0), "hours")).isEqualTo(8);
    }

    // ---- model snapshot ----

    @Test
    void snapshotHasTheInnerCollection() {
        CompilationSubject.assertThat(compile(getOrder(FIXED_LINES), orderLine(COUNTED_TAXES),
            lineTax())).succeeded();
        ModelSnapshot.Member lines = compiler.only().members().get(1);
        assertThat(lines.byteLength()).isEqualTo(14);
        ModelSnapshot.Member taxes = lines.itemMembers().get(2);
        assertThat(taxes.name()).isEqualTo("taxes");
        assertThat(taxes.direction()).isEqualTo("OUTPUT");
        assertThat(taxes.countField()).isEqualTo("taxCount");
        assertThat(taxes.itemMembers()).extracting(ModelSnapshot.Member::name)
            .containsExactly("taxCode", "amount");
    }

    // ---- compile errors ----

    @Test
    void innerVariableCollectionIsAnError() {
        assertError(compile(getOrder(FIXED_LINES),
                orderLine(", maxOccurs = 2, dependsOn = \"taxCount\""), lineTax()),
            "OrderLine.taxes (order 30)", "a collection in an item class must have fixed = true"
                + " (nested OCCURS DEPENDING ON is not supported)");
    }

    @Test
    void directionOnAnInnerCollectionIsAnError() {
        assertError(compile(getOrder(FIXED_LINES),
                orderLine(COUNTED_TAXES + ", direction = Direction.OUTPUT"), lineTax()),
            "OrderLine.taxes (order 30)",
            "direction must not be set on a collection in an item class");
    }

    @Test
    void countFieldOutsideTheItemClassIsAnError() {
        assertError(compile(getOrder(FIXED_LINES),
                orderLine(", maxOccurs = 2, fixed = true, dependsOn = \"orderId\""), lineTax()),
            "OrderLine.taxes (order 30)", "dependsOn field 'orderId' does not exist in item"
                + " class OrderLine. The count field of a nested collection must be in the item"
                + " class");
    }

    @Test
    void innerCountFieldRulesStillApply() {
        assertError(compile(getOrder(FIXED_LINES),
                orderLine(", maxOccurs = 12, fixed = true, dependsOn = \"taxCount\""), lineTax()),
            "OrderLine.taxes (order 30)",
            "dependsOn field 'taxCount' has 1 digits and cannot hold maxOccurs 12");
    }

    @Test
    void innerEmptyKeyMustBeAFieldOfTheInnerItem() {
        assertError(compile(getOrder(FIXED_LINES),
                orderLine(", maxOccurs = 2, fixed = true, emptyKey = \"productCode\""),
                lineTax()),
            "OrderLine.taxes (order 30)",
            "emptyKey field 'productCode' is not a field of item class test.LineTax");
    }

    @Test
    void anItemClassThatContainsItselfIsAnError() {
        assertError(compile(getOrder(FIXED_LINES),
                source("OrderLine", "public class OrderLine {",
                    field("@BookField(order = 10, pic = \"X\")", "String", "code"),
                    field("@BookCollection(order = 20, maxOccurs = 2, fixed = true)",
                        "List<SubLine>", "parts"),
                    "}"),
                source("SubLine", "public class SubLine {",
                    field("@BookField(order = 10, pic = \"X\")", "String", "code"),
                    field("@BookCollection(order = 20, maxOccurs = 2, fixed = true)",
                        "List<OrderLine>", "lines"),
                    "}")),
            "SubLine.lines (order 20)",
            "item class OrderLine contains itself: OrderLine > SubLine > OrderLine");
    }

    @Test
    void aCycleBelowTheFirstItemClassNamesOnlyTheCycle() {
        assertError(compile(getOrder(FIXED_LINES),
                source("OrderLine", "public class OrderLine {",
                    field("@BookCollection(order = 10, maxOccurs = 2, fixed = true)",
                        "List<SubLine>", "parts"),
                    "}"),
                source("SubLine", "public class SubLine {",
                    field("@BookCollection(order = 10, maxOccurs = 2, fixed = true)",
                        "List<Part>", "parts"),
                    "}"),
                source("Part", "public class Part {",
                    field("@BookCollection(order = 10, maxOccurs = 2, fixed = true)",
                        "List<SubLine>", "lines"),
                    "}")),
            "Part.lines (order 10)",
            "item class SubLine contains itself: SubLine > Part > SubLine");
    }

    @Test
    void moreThanSevenLevelsIsAnError() {
        List<JavaFileObject> files = new ArrayList<>();
        files.add(source("Deep", "@BookOperation public class Deep {",
            field("@BookCollection(order = 10, maxOccurs = 1, fixed = true)", "List<L1>",
                "items"),
            "}"));
        for (int level = 1; level <= 8; level++) {
            files.add(source("L" + level, "public class L" + level + " {",
                field("@BookField(order = 10, pic = \"X\")", "String", "code"),
                level < 8 ? field("@BookCollection(order = 20, maxOccurs = 1, fixed = true)",
                    "List<L" + (level + 1) + ">", "items") : "",
                "}"));
        }
        assertError(compile(files.toArray(JavaFileObject[]::new)), "L7.items (order 20)",
            "a collection must not have more than 7 levels of nested collections");
    }

    @Test
    void sevenLevelsArePermitted() {
        List<JavaFileObject> files = new ArrayList<>();
        files.add(source("Deep", "@BookOperation public class Deep {",
            field("@BookCollection(order = 10, maxOccurs = 1, fixed = true)", "List<L1>",
                "items"),
            "}"));
        for (int level = 1; level <= 7; level++) {
            files.add(source("L" + level, "public class L" + level + " {",
                field("@BookField(order = 10, pic = \"X\")", "String", "code"),
                level < 7 ? field("@BookCollection(order = 20, maxOccurs = 1, fixed = true)",
                    "List<L" + (level + 1) + ">", "items") : "",
                "}"));
        }
        CompilationSubject.assertThat(compile(files.toArray(JavaFileObject[]::new)))
            .succeededWithoutWarnings();
    }

    @Test
    void innerCollectionInAContinuableClassIsPermitted() {
        CompilationSubject.assertThat(compile(
            source("GetOrder", "@BookOperation public class GetOrder implements Continuable {",
                field("@BookCollection(order = 20, maxOccurs = 2, fixed = true)",
                    "List<OrderLine>", "lines"),
                "public boolean hasContinuation() { return false; }",
                "}"),
            orderLine(COUNTED_TAXES), lineTax())).succeededWithoutWarnings();
    }
}
