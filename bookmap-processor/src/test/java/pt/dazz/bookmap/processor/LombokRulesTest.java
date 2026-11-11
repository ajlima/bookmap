package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.TestCompiler.lombok;
import static pt.dazz.bookmap.processor.TestCompiler.source;
import static pt.dazz.bookmap.processor.TestCompiler.sourceIn;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Checks the Lombok constructor rules and the Lombok access levels across packages, with Lombok
 * before and after bookmap. The result must be the same in both orders, and a problem must be a
 * bookmap error, never a javac error in the generated codec.
 */
class LombokRulesTest {

    enum ProcessorOrder { LOMBOK_FIRST, BOOKMAP_FIRST }

    private static final String NO_CONSTRUCTOR =
        "Order: operation class must have a public no-argument constructor";
    private static final String ITEM_NO_CONSTRUCTOR = "Order.lines (order 2): item class "
        + "test.Line must be a public class with a public no-argument constructor";

    private final TestCompiler compiler = TestCompiler.create();

    private Compilation compile(ProcessorOrder order, JavaFileObject... files) {
        List<Processor> processors = order == ProcessorOrder.LOMBOK_FIRST
            ? List.of(lombok(), compiler.processor())
            : List.of(compiler.processor(), lombok());
        return compiler.compileWithProcessors(processors, files);
    }

    /** An operation class with Lombok accessors and the given class annotations. */
    private static JavaFileObject order(String annotations, String... extraLines) {
        return source("Order",
            annotations + " @lombok.Getter @lombok.Setter @BookOperation public class Order {",
            "@BookField(order = 1, length = 5) private String id;",
            String.join("\n", extraLines),
            "}");
    }

    private static void assertOnlyError(Compilation compilation, String message) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(message);
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    // ---- subtypes ----

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void subclassWithLombokData(ProcessorOrder order) {
        Compilation compilation = compile(order,
            source("GetAccount",
                "@lombok.Getter @lombok.Setter @BookOperation",
                "@BookSubTypes(discriminator = \"accountType\", value = {",
                "    @BookSubTypes.Type(value = \"01\", type = test.accounts.CheckingAccount.class)})",
                "public class GetAccount {",
                "@BookField(order = 1, length = 5) private String id;",
                "@BookField(order = 2, length = 2) private int accountType;",
                "}"),
            sourceIn("test.accounts", "CheckingAccount",
                "@lombok.Data @lombok.EqualsAndHashCode(callSuper = true)",
                "public class CheckingAccount extends test.GetAccount {",
                "@BookField(order = 3, length = 19) private String cardNumberMask;",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    // ---- I2: constructors ----

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void noArgsConstructorWithAnExplicitConstructor(ProcessorOrder order) {
        Compilation compilation = compile(order, order("@lombok.NoArgsConstructor",
            "public Order(String id) { this.id = id; }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void builderWithNoArgsAndAllArgsConstructors(ProcessorOrder order) {
        Compilation compilation = compile(order, order(
            "@lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void explicitNoArgConstructorWithAllArgsConstructor(ProcessorOrder order) {
        Compilation compilation = compile(order, order("@lombok.AllArgsConstructor",
            "public Order() { }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void allArgsConstructorRemovesTheDefaultConstructor(ProcessorOrder order) {
        assertOnlyError(compile(order, order("@lombok.AllArgsConstructor")), NO_CONSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void builderRemovesTheDefaultConstructor(ProcessorOrder order) {
        assertOnlyError(compile(order, order("@lombok.Builder")), NO_CONSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void requiredArgsConstructorRemovesTheDefaultConstructor(ProcessorOrder order) {
        assertOnlyError(compile(order, order("@lombok.RequiredArgsConstructor",
            "private final String region;")), NO_CONSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void protectedNoArgsConstructorIsNotPublic(ProcessorOrder order) {
        assertOnlyError(compile(order,
            order("@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)")),
            NO_CONSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void noArgsConstructorWithStaticNameIsPrivate(ProcessorOrder order) {
        assertOnlyError(compile(order,
            order("@lombok.NoArgsConstructor(staticName = \"create\")")), NO_CONSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void publicNoArgsConstructorWithExplicitAccess(ProcessorOrder order) {
        Compilation compilation = compile(order, order(
            "@lombok.NoArgsConstructor(access = lombok.AccessLevel.PUBLIC)"
                + " @lombok.AllArgsConstructor"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    /** An operation class with a collection of {@code test.Line}. */
    private static JavaFileObject orderWithLines() {
        return source("Order",
            "@lombok.Data @BookOperation public class Order {",
            "@BookField(order = 1, length = 1) private int lineCount;",
            "@BookCollection(order = 2, maxOccurs = 3) private List<Line> lines;",
            "}");
    }

    private static JavaFileObject line(String annotations, String... extraLines) {
        return source("Line",
            annotations + " @lombok.Getter @lombok.Setter public class Line {",
            "@BookField(order = 1, length = 5) private String sku;",
            String.join("\n", extraLines),
            "}");
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void itemClassWithAllArgsConstructorFails(ProcessorOrder order) {
        assertOnlyError(compile(order, orderWithLines(), line("@lombok.AllArgsConstructor")),
            ITEM_NO_CONSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void itemClassWithBuilderFails(ProcessorOrder order) {
        assertOnlyError(compile(order, orderWithLines(), line("@lombok.Builder")),
            ITEM_NO_CONSTRUCTOR);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void itemClassWithNoArgsConstructorAndExplicitConstructor(ProcessorOrder order) {
        Compilation compilation = compile(order, orderWithLines(),
            line("@lombok.NoArgsConstructor", "public Line(String sku) { this.sku = sku; }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    // ---- I5: PACKAGE and PROTECTED accessors in another package ----

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void protectedGetterOfASuperclassInAnotherPackageFails(ProcessorOrder order) {
        Compilation compilation = compile(order,
            sourceIn("test.base", "Base", "public class Base {",
                "@lombok.Getter(lombok.AccessLevel.PROTECTED) @lombok.Setter",
                "@BookField(order = 1, length = 5) private String id;",
                "}"),
            source("Order", "@BookOperation public class Order extends test.base.Base { }"));
        assertOnlyError(compilation, "Base.id (order 1): missing getter getId");
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void packageSetterOfAClassLevelAnnotationInAnotherPackageFails(ProcessorOrder order) {
        Compilation compilation = compile(order,
            sourceIn("test.base", "Base",
                "@lombok.Getter @lombok.Setter(lombok.AccessLevel.PACKAGE) public class Base {",
                "@BookField(order = 1, length = 5) private String id;",
                "}"),
            source("Order", "@BookOperation public class Order extends test.base.Base { }"));
        assertOnlyError(compilation, "Base.id (order 1): missing setter setId");
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void packageGetterOfAnItemClassInAnotherPackageFails(ProcessorOrder order) {
        Compilation compilation = compile(order,
            source("Order",
                "@lombok.Data @BookOperation public class Order {",
                "@BookField(order = 1, length = 1) private int lineCount;",
                "@BookCollection(order = 2, maxOccurs = 3)",
                "private List<test.items.Line> lines;",
                "}"),
            sourceIn("test.items", "Line", "@lombok.Setter public class Line {",
                "@lombok.Getter(lombok.AccessLevel.PACKAGE)",
                "@BookField(order = 1, length = 5) private String sku;",
                "}"));
        assertOnlyError(compilation, "Line.sku (order 1): missing getter getSku");
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void packageAndProtectedAccessorsInTheCodecPackageAreCallable(ProcessorOrder order) {
        Compilation compilation = compile(order,
            source("Base", "public class Base {",
                "@lombok.Getter(lombok.AccessLevel.PROTECTED)",
                "@lombok.Setter(lombok.AccessLevel.PACKAGE)",
                "@BookField(order = 1, length = 5) private String id;",
                "}"),
            source("Order", "@BookOperation public class Order extends Base {",
                "@SuppressWarnings(\"deprecation\") @lombok.Getter(lombok.AccessLevel.MODULE) @lombok.Setter",
                "@BookField(order = 2, length = 5) private String code;",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }
}
