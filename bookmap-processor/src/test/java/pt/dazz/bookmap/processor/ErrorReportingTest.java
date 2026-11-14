package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.bookmap.processor.TestCompiler.accessors;
import static pt.dazz.bookmap.processor.TestCompiler.lombok;
import static pt.dazz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.util.List;
import java.util.Map;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * Checks which compile errors the processor reports, and how many times: no duplicate error, no
 * lost error, and no misleading error next to a javac error.
 */
class ErrorReportingTest {

    private final TestCompiler compiler = TestCompiler.create();

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    /** An item class with one text field. */
    private static JavaFileObject line() {
        return source("Line", "public class Line {",
            field("@BookField(order = 1, length = 5)", "String", "sku"), "}");
    }

    /** A converter class for {@code String} values. */
    private static String codeConverter() {
        return String.join("\n",
            "public class CodeConverter implements FieldConverter<String> {",
            "public String decode(byte[] b, FieldContext c) { return null; }",
            "public byte[] encode(String v, FieldContext c) { return null; }",
            "}");
    }

    private static void assertOnlyJavacError(Compilation compilation) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("cannot find symbol");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    // ---- an error is reported one time ----

    @Test
    void errorOfASharedSuperclassIsReportedOneTime() {
        Compilation compilation = compiler.compile(
            source("Base", "public class Base {",
                field("@BookField(order = 1)", "String", "code"), "}"),
            source("GetOrder", "@BookOperation public class GetOrder extends Base {",
                field("@BookField(order = 2, length = 5)", "String", "id"), "}"),
            source("CancelOrder", "@BookOperation public class CancelOrder extends Base {",
                field("@BookField(order = 2, length = 5)", "String", "id"), "}"));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Base.code (order 1): pic or length is missing");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void errorOfASharedSuperclassIsReportedOneTimeInLaterRounds() {
        // CancelOrder waits for a generated converter, so the processor builds it in the
        // second round. GetOrder reports the error of Base in the first round.
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of("test.CodeConverter", codeConverter()));
        Compilation compilation = compiler.compile(List.of(generator),
            source("Base", "public class Base {",
                field("@BookField(order = 1)", "String", "code"), "}"),
            source("GetOrder", "@BookOperation public class GetOrder extends Base {",
                field("@BookField(order = 2, length = 5)", "String", "id"), "}"),
            source("CancelOrder", "@BookOperation public class CancelOrder extends Base {",
                field("@BookField(order = 2, length = 5, converter = CodeConverter.class)",
                    "String", "id"), "}"));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Base.code (order 1): pic or length is missing");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void errorOfASharedItemClassIsReportedOneTime() {
        Compilation compilation = compiler.compile(
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1, length = 3)", "int", "lineCount"),
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Line>", "lines"),
                field("@BookField(order = 3, length = 3)", "int", "backorderCount"),
                field("@BookCollection(order = 4, maxOccurs = 2)", "List<Line>", "backorders"),
                "}"),
            source("Line", "public class Line {",
                field("@BookField(order = 1)", "String", "sku"), "}"));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Line.sku (order 1): pic or length is missing");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void sameMessageOnTwoFieldsIsReportedForEachField() {
        Compilation compilation = compiler.compile(
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1)", "String", "code"), "}"),
            source("Invoice", "@BookOperation public class Invoice {",
                field("@BookField(order = 1)", "String", "code"), "}"));
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Order.code (order 1): pic or length is missing");
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Invoice.code (order 1): pic or length is missing");
        CompilationSubject.assertThat(compilation).hadErrorCount(2);
    }

    // ---- an interface from another generator ----

    @Test
    void continuableThroughAGeneratedInterfaceIsDetected() {
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of("test.Paged", "public interface Paged extends Continuable { }"));
        Compilation compilation = compiler.compile(List.of(generator),
            source("Order", "@BookOperation public class Order implements Paged {",
                TestCompiler.ID_FIELD,
                "public boolean hasContinuation() { return false; }",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().continuable()).isTrue();
    }

    @Test
    void validatableThroughAGeneratedInterfaceOfASuperinterfaceIsDetected() {
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of("test.Checked", "public interface Checked extends Validatable { }"));
        Compilation compilation = compiler.compile(List.of(generator),
            source("Audited", "public interface Audited extends Checked { }"),
            source("Order", "@BookOperation public class Order implements Audited {",
                TestCompiler.ID_FIELD,
                "public void validate() { }",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().validatable()).isTrue();
    }

    @Test
    void continuableCheckOfAGeneratedInterfaceAppliesToTheCollections() {
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of("test.Paged", "public interface Paged extends Continuable { }"));
        Compilation compilation = compiler.compile(List.of(generator),
            source("Order", "@BookOperation public class Order implements Paged {",
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2,"
                    + " direction = Direction.INPUT_OUTPUT)", "List<Line>", "lines"),
                "public boolean hasContinuation() { return false; }",
                "}"),
            line());
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("Order.lines (order 2):"
            + " an INPUT_OUTPUT collection is not permitted in a Continuable class");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void missingInterfaceIsLeftToJavac() {
        assertOnlyJavacError(compiler.compile(
            source("Order", "@BookOperation public class Order implements Missing {",
                TestCompiler.ID_FIELD, "}")));
        assertThat(compiler.snapshots).isEmpty();
    }

    // ---- missing accessors with other errors ----

    @Test
    void accessorsOfALaterRoundAreAcceptedWhenTheClassHasAnotherError() {
        // Bookmap runs before Lombok. The Accessors annotation stops the name rule, so the
        // accessors are visible only in the next round. Only the other error is reported.
        Compilation compilation = compiler.compileWithProcessors(
            List.of(compiler.processor(), lombok()),
            source("Order", "@lombok.Data @lombok.experimental.Accessors(chain = true)",
                "@BookOperation public class Order {",
                "@BookField(order = 1, length = 5) private String id;",
                "@BookField(order = 2) private String code;",
                "}"));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Order.code (order 2): pic or length is missing");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    // ---- unresolved values ----

    @Test
    void unresolvedCountFieldDoesNotGiveAnotherCountField() {
        assertOnlyJavacError(compiler.compile(
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1, length = 5)", "String", "id"),
                field("@BookField(order = 2, length = MISSING)", "int", "count"),
                field("@BookCollection(order = 3, maxOccurs = 2)", "List<Line>", "lines"),
                "}"),
            line()));
    }

    @Test
    void unresolvedDependsOnFieldIsNotReportedAsMissing() {
        assertOnlyJavacError(compiler.compile(
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1, length = MISSING)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines"),
                "}"),
            line()));
    }

    @Test
    void unresolvedDiscriminatorIsNotReportedAsMissing() {
        assertOnlyJavacError(compiler.compile(
            source("GetAccount", "@BookOperation @BookSubTypes(discriminator = \"kind\","
                    + " value = @BookSubTypes.Type(value = \"01\", type = Checking.class))",
                "public class GetAccount {",
                field("@BookField(order = 1, length = MISSING)", "int", "kind"),
                "}"),
            source("Checking", "public class Checking extends GetAccount {",
                field("@BookField(order = 2, length = 5)", "String", "card"), "}")));
    }

    @Test
    void unresolvedEmptyKeyIsNotReportedAsMissing() {
        assertOnlyJavacError(compiler.compile(
            source("Order", "@BookOperation public class Order {",
                field("@BookCollection(order = 1, maxOccurs = 2, fixed = true,"
                    + " emptyKey = \"sku\")", "List<Line>", "lines"),
                "}"),
            source("Line", "public class Line {",
                field("@BookField(order = 1, length = MISSING)", "String", "sku"),
                field("@BookField(order = 2, length = 3)", "int", "qty"), "}")));
    }

    @Test
    void unresolvedOperationCharsetIsNotComparedWithOtherCharsets() {
        assertOnlyJavacError(compiler.compile(
            source("Order", "@BookDefaults(charset = MISSING) @BookOperation",
                "public class Order {",
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Line>", "lines"),
                "}"),
            source("Line", "@BookDefaults(charset = \"IBM500\") public class Line {",
                field("@BookField(order = 1, length = 5)", "String", "sku"), "}")));
    }
}
