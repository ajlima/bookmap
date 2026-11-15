package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.spi.BookCodec;

/** Tests the maximum length of a field, an item, a collection, and a record. */
class RecordLengthTest {

    private static final String MAX = "more than the maximum of 2147483647 bytes";
    /** 999999999 bytes. */
    private static final String LARGE = "pic = \"X(999999999)\"";

    private final TestCompiler compiler = TestCompiler.create();

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static String text(int order, String pic, String name) {
        return field("@BookField(order = " + order + ", " + pic + ")", "String", name);
    }

    private static String large(int order, String name) {
        return text(order, LARGE, name);
    }

    private static JavaFileObject operation(String... members) {
        return source("GetShipments", "@BookOperation public class GetShipments {",
            String.join("\n", members), "}");
    }

    private static String lines(int maxOccurs, String type) {
        return field("@BookCollection(order = 10, maxOccurs = " + maxOccurs + ", fixed = true)",
            "List<" + type + ">", "lines");
    }

    private static JavaFileObject item(String name, String... members) {
        return source(name, "public class " + name + " {", String.join("\n", members), "}");
    }

    /** Gives the error messages of a compilation. */
    private static long errors(Compilation compilation) {
        return compilation.diagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
    }

    private void assertOnlyError(Compilation compilation, String message) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(message);
        assertThat(errors(compilation)).isEqualTo(1);
    }

    @Test
    void rejectsAFieldLongerThanTheMaximum() {
        assertOnlyError(compiler.compile(operation(field("@BookField(order = 1, pic ="
                + " \"S9(999999999)9(999999999)9(147483649) SIGN LEADING SEPARATE\")",
                "BigDecimal", "amount"))),
            "GetShipments.amount (order 1): the field has 2147483648 bytes, " + MAX);
    }

    @Test
    void acceptsAFieldOfTheMaximumLength() {
        Compilation compilation = compiler.compile(operation(field("@BookField(order = 1, pic ="
            + " \"S9(999999999)9(999999999)9(147483648) SIGN LEADING SEPARATE\")", "BigDecimal",
            "amount")));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.GetShipments");
        assertThat(codec.maxResponseLength()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void rejectsARecordLongerThanTheMaximum() {
        assertOnlyError(compiler.compile(operation(large(1, "a"), large(2, "b"),
                text(3, "pic = \"X(147483650)\"", "c"))),
            "GetShipments: the record has 2147483648 bytes, " + MAX);
    }

    @Test
    void acceptsARecordOfTheMaximumLength() {
        Compilation compilation = compiler.compile(operation(large(1, "a"), large(2, "b"),
            text(3, "pic = \"X(147483649)\"", "c")));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        BookCodec<Object> codec = CompiledCodec.load(compilation, "test.GetShipments");
        assertThat(codec.maxResponseLength()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void recordCountsBothDirections() {
        assertOnlyError(compiler.compile(operation(
                field("@BookField(order = 1, " + LARGE + ", direction = Direction.INPUT)",
                    "String", "a"),
                field("@BookField(order = 2, " + LARGE + ", direction = Direction.INPUT)",
                    "String", "b"),
                field("@BookField(order = 3, " + LARGE + ", direction = Direction.OUTPUT)",
                    "String", "c"))),
            "GetShipments: the record has 2999999997 bytes, " + MAX);
    }

    @Test
    void rejectsAnItemClassLongerThanTheMaximum() {
        assertOnlyError(compiler.compile(operation(lines(1, "ShipmentLine")),
                item("ShipmentLine", large(1, "a"), large(2, "b"), large(3, "c"))),
            "GetShipments.lines (order 10): item class ShipmentLine has 2999999997 bytes, " + MAX);
    }

    @Test
    void rejectsACollectionLongerThanTheMaximum() {
        assertOnlyError(compiler.compile(operation(lines(4096, "ShipmentLine")),
                item("ShipmentLine", text(1, "pic = \"X(1048576)\"", "a"))),
            "GetShipments.lines (order 10): the collection has 4294967296 bytes (maxOccurs 4096"
                + " x 1048576 bytes), " + MAX);
    }

    @Test
    void acceptsACollectionOfTheMaximumLength() {
        Compilation compilation = compiler.compile(operation(lines(1, "ShipmentLine")),
            item("ShipmentLine", large(1, "a"), large(2, "b"),
                text(3, "pic = \"X(147483649)\"", "c")));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    void innerCollectionErrorIsNotReportedAgain() {
        assertOnlyError(compiler.compile(operation(lines(2, "ShipmentPackage")),
                item("ShipmentPackage",
                    field("@BookCollection(order = 1, maxOccurs = 3000, fixed = true)",
                        "List<ShipmentLine>", "items")),
                item("ShipmentLine", text(1, "pic = \"X(1000000)\"", "a"))),
            "ShipmentPackage.items (order 1): the collection has 3000000000 bytes (maxOccurs 3000"
                + " x 1000000 bytes), " + MAX);
    }

    @Test
    void rejectsAnItemSubtypeLongerThanTheMaximum() {
        assertOnlyError(compiler.compile(operation(lines(1, "ShipmentLine")),
                source("ShipmentLine",
                    "@BookSubTypes(discriminator = \"kind\", value = {",
                    "  @BookSubTypes.Type(value = \"S\", type = SmallLine.class),",
                    "  @BookSubTypes.Type(value = \"H\", type = HugeLine.class)})",
                    "public abstract class ShipmentLine {",
                    text(1, "length = 1", "kind"), "}"),
                source("SmallLine", "public class SmallLine extends ShipmentLine {",
                    text(2, "length = 10", "code"), "}"),
                source("HugeLine", "public class HugeLine extends ShipmentLine {",
                    large(2, "a"), large(3, "b"), large(4, "c"), "}")),
            "GetShipments.lines (order 10): subtype HugeLine of item class ShipmentLine has"
                + " 2999999998 bytes, " + MAX);
    }

    @Test
    void rejectsAnOperationSubtypeLongerThanTheMaximum() {
        assertOnlyError(compiler.compile(
                source("GetShipments",
                    "@BookOperation @BookSubTypes(discriminator = \"kind\", value = {",
                    "  @BookSubTypes.Type(value = \"S\", type = SmallShipments.class),",
                    "  @BookSubTypes.Type(value = \"H\", type = HugeShipments.class)})",
                    "public class GetShipments {",
                    text(1, "length = 1", "kind"), "}"),
                source("SmallShipments", "public class SmallShipments extends GetShipments {",
                    text(2, "length = 10", "code"), "}"),
                source("HugeShipments", "public class HugeShipments extends GetShipments {",
                    large(2, "a"), large(3, "b"), large(4, "c"), "}")),
            "GetShipments: the record of subtype HugeShipments has 2999999998 bytes, " + MAX);
    }

    @Test
    void rejectsAnOperationBaseLayoutLongerThanTheMaximum() {
        assertOnlyError(compiler.compile(
                source("GetShipments",
                    "@BookOperation @BookSubTypes(discriminator = \"kind\", value = {",
                    "  @BookSubTypes.Type(value = \"S\", type = SmallShipments.class)})",
                    "public class GetShipments {",
                    text(1, "length = 1", "kind"), large(2, "a"), large(3, "b"),
                    large(4, "c"), "}"),
                source("SmallShipments", "public class SmallShipments extends GetShipments {",
                    text(5, "length = 10", "code"), "}")),
            "GetShipments: the record has 2999999998 bytes, " + MAX);
        // The subtype also has the base members, but the builder does not report it again.
    }
}
