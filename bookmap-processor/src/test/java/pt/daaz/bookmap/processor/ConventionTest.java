package pt.daaz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pt.daaz.bookmap.processor.TestCompiler.accessors;
import static pt.daaz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import pt.daaz.bookmap.processor.model.CollectionModel;
import pt.daaz.bookmap.processor.model.FieldModel;
import pt.daaz.bookmap.processor.model.MemberModel;
import pt.daaz.bookmap.processor.model.OperationModel;

class ConventionTest {

    private final TestCompiler compiler = TestCompiler.create();

    @Test
    void operationNameDefaultsToSimpleClassName() {
        Compilation compilation = compiler.compile(source("GetCustomerOrders",
            "@BookOperation public class GetCustomerOrders { }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().operationName()).isEqualTo("GetCustomerOrders");
    }

    @Test
    void explicitOperationNameWins() {
        Compilation compilation = compiler.compile(source("GetCustomerOrders",
            "@BookOperation(\"ORDLIST\") public class GetCustomerOrders { }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().operationName()).isEqualTo("ORDLIST");
    }

    @Test
    void collectionDefaultsToOutput() {
        Compilation compilation = compiler.compile(
            source("Order", "@BookOperation public class Order {",
                "@BookField(order = 1, length = 3) private int count;",
                accessors("int", "count"),
                "@BookCollection(order = 2, maxOccurs = 3, dependsOn = \"count\")",
                "private List<Line> lines;",
                accessors("List<Line>", "lines"),
                "}"),
            source("Line", "public class Line {",
                "@BookField(order = 1, length = 5) private String sku;",
                accessors("String", "sku"),
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot.Member lines = compiler.only().members().get(1);
        assertThat(lines.direction()).isEqualTo("OUTPUT");
        assertThat(lines.itemFields().get(0).direction()).isEqualTo("OUTPUT");
    }

    @Test
    void nestedOperationClass() {
        Compilation compilation = compiler.compile(source("Outer",
            "public class Outer {",
            "@BookOperation public static class Inner {",
            "@BookField(order = 1, length = 5) private String id;",
            accessors("String", "id"),
            "}",
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(model.operationName()).isEqualTo("Inner");
        assertThat(model.members().get(0).owner()).isEqualTo("Inner");
    }

    @Test
    void explicitAttributesReachTheModel() {
        Compilation compilation = compiler.compile(source("Order",
            "@BookOperation public class Order {",
            "@BookField(order = 5, length = 15, scale = 2, signed = true, usage = Usage.COMP3,",
            "    direction = Direction.INPUT)",
            "private BigDecimal amount;",
            accessors("BigDecimal", "amount"),
            "@BookField(order = 1, length = 6, pattern = \"yyMMdd\") private LocalDate day;",
            accessors("LocalDate", "day"),
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(model.members()).extracting(ModelSnapshot.Member::name)
            .containsExactly("day", "amount");
        assertThat(model.members().get(1)).isEqualTo(new ModelSnapshot.Member("Order", "amount", 5,
            "INPUT", 15, 2, true, "COMP3", "yyyyMMdd", 8, "BIG_DECIMAL", "getAmount",
            "setAmount", "", "", 0, List.of()));
        assertThat(model.members().get(0).pattern()).isEqualTo("yyMMdd");
        assertThat(model.members().get(0).direction()).isEqualTo("INPUT_OUTPUT");
    }

    // ---- pic and defaults ----

    private static ModelSnapshot.Member member(ModelSnapshot model, String name) {
        return model.members().stream().filter(m -> m.name().equals(name)).findFirst()
            .orElseThrow();
    }

    @Test
    void picResolvesLengthScaleSignedUsage() {
        Compilation compilation = compiler.compile(source("Order",
            "@BookOperation public class Order {",
            "@BookField(order = 1, pic = \"S9(13)V99 COMP-3\") private BigDecimal amount;",
            accessors("BigDecimal", "amount"),
            "@BookField(order = 2, pic = \"PIC X(10).\") private String customerId;",
            accessors("String", "customerId"),
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot.Member amount = member(compiler.only(), "amount");
        assertThat(amount.length()).isEqualTo(15);
        assertThat(amount.scale()).isEqualTo(2);
        assertThat(amount.signed()).isTrue();
        assertThat(amount.usage()).isEqualTo("COMP3");
        assertThat(amount.byteLength()).isEqualTo(8);
        ModelSnapshot.Member customerId = member(compiler.only(), "customerId");
        assertThat(customerId.length()).isEqualTo(10);
        assertThat(customerId.usage()).isEqualTo("DISPLAY");
        assertThat(customerId.byteLength()).isEqualTo(10);
    }

    @Test
    void picWithoutUsageUsesBookDefaults() {
        Compilation compilation = compiler.compile(source("Order",
            "@BookDefaults(usage = Usage.COMP3, signed = true) @BookOperation",
            "public class Order {",
            "@BookField(order = 1, pic = \"S9(5)\") private int quantity;",
            accessors("int", "quantity"),
            "@BookField(order = 2, pic = \"9(5)\") private int units;",
            accessors("int", "units"),
            "@BookField(order = 3, pic = \"9(4) COMP\") private int lines;",
            accessors("int", "lines"),
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(member(model, "quantity").usage()).isEqualTo("COMP3");
        assertThat(member(model, "quantity").byteLength()).isEqualTo(3);
        // The pic gives the sign. The signed default does not apply.
        assertThat(member(model, "units").signed()).isFalse();
        assertThat(member(model, "lines").usage()).isEqualTo("COMP");
    }

    @Test
    void classDefaultsOverridePackageDefaults() {
        Compilation compilation = compiler.compile(
            TestCompiler.packageInfo("test",
                "@BookDefaults(usage = Usage.COMP, signed = true, pattern = \"yyMMdd\")"),
            source("Order", "@BookDefaults(usage = Usage.COMP3) @BookOperation",
                "public class Order {",
                "@BookField(order = 1, length = 5) private int quantity;",
                accessors("int", "quantity"),
                "@BookField(order = 2) private LocalDate shipped;",
                accessors("LocalDate", "shipped"),
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(member(model, "quantity").usage()).isEqualTo("COMP3");
        assertThat(member(model, "quantity").signed()).isTrue();
        assertThat(member(model, "shipped").pattern()).isEqualTo("yyMMdd");
        assertThat(member(model, "shipped").length()).isEqualTo(6);
    }

    @Test
    void explicitAttributeOverridesDefaults() {
        Compilation compilation = compiler.compile(source("Order",
            "@BookDefaults(usage = Usage.COMP3, signed = true, pattern = \"yyMMdd\")",
            "@BookOperation public class Order {",
            "@BookField(order = 1, length = 5, signed = false, usage = Usage.DISPLAY)",
            "private int quantity;",
            accessors("int", "quantity"),
            "@BookField(order = 2, pattern = \"yyyyMMdd\") private LocalDate shipped;",
            accessors("LocalDate", "shipped"),
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(member(model, "quantity").signed()).isFalse();
        assertThat(member(model, "quantity").usage()).isEqualTo("DISPLAY");
        assertThat(member(model, "shipped").pattern()).isEqualTo("yyyyMMdd");
        assertThat(member(model, "shipped").length()).isEqualTo(8);
    }

    @Test
    void superclassFieldUsesItsOwnDefaults() {
        Compilation compilation = compiler.compile(
            source("Base", "@BookDefaults(usage = Usage.COMP3) public class Base {",
                "@BookField(order = 1, length = 5) private int quantity;",
                accessors("int", "quantity"),
                "}"),
            source("Order", "@BookOperation public class Order extends Base {",
                "@BookField(order = 2, length = 5) private int units;",
                accessors("int", "units"),
                "@BookCollection(order = 3, maxOccurs = 2, dependsOn = \"units\")",
                "private List<Line> lines;",
                accessors("List<Line>", "lines"),
                "}"),
            source("Line", "@BookDefaults(usage = Usage.COMP) public class Line {",
                "@BookField(order = 1, length = 5) private int quantity;",
                accessors("int", "quantity"),
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(member(model, "quantity").owner()).isEqualTo("Base");
        assertThat(member(model, "quantity").usage()).isEqualTo("COMP3");
        assertThat(member(model, "units").usage()).isEqualTo("DISPLAY");
        assertThat(member(model, "lines").itemFields().get(0).usage()).isEqualTo("COMP");
    }

    @Test
    void dateLengthFromDefaultPattern() {
        Compilation compilation = compiler.compile(source("Order",
            "@BookOperation public class Order {",
            "@BookField(order = 1) private LocalDate shipped;",
            accessors("LocalDate", "shipped"),
            "@BookField(order = 2, pattern = \"HHmmss\") private LocalTime time;",
            accessors("LocalTime", "time"),
            "@BookField(order = 3, pattern = \"yyyy-MM-dd'T'HH:mm:ss\")",
            "private LocalDateTime created;",
            accessors("LocalDateTime", "created"),
            "@BookField(order = 4, pic = \"9(8)\") private LocalDate delivered;",
            accessors("LocalDate", "delivered"),
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(member(model, "shipped").length()).isEqualTo(8);
        assertThat(member(model, "shipped").pattern()).isEqualTo("yyyyMMdd");
        assertThat(member(model, "shipped").byteLength()).isEqualTo(8);
        assertThat(member(model, "time").length()).isEqualTo(6);
        // The length is the length of the formatted text. Quotes do not count.
        assertThat(member(model, "created").length()).isEqualTo(19);
        assertThat(member(model, "delivered").length()).isEqualTo(8);
    }

    @Test
    void dateLengthFromDefaultsPattern() {
        Compilation compilation = compiler.compile(source("Order",
            "@BookDefaults(pattern = \"yyyyDDD\") @BookOperation public class Order {",
            "@BookField(order = 1) private LocalDate shipped;",
            accessors("LocalDate", "shipped"),
            "@BookField(order = 2, length = 5, scale = 2) private BigDecimal rate;",
            accessors("BigDecimal", "rate"),
            "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(member(model, "shipped").length()).isEqualTo(7);
        assertThat(member(model, "shipped").pattern()).isEqualTo("yyyyDDD");
        // The pattern default applies only to date and time fields.
        assertThat(member(model, "rate").pattern()).isEqualTo("yyyyMMdd");
    }

    @Test
    void dependsOnDefaultsToPreviousMember() {
        Compilation compilation = compiler.compile(
            source("Order", "@BookOperation public class Order {",
                "@BookCollection(order = 30, maxOccurs = 3) private List<Line> orders;",
                accessors("List<Line>", "orders"),
                "@BookField(order = 20, length = 3) private int orderCount;",
                accessors("int", "orderCount"),
                "@BookField(order = 10, length = 5) private int other;",
                accessors("int", "other"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(member(compiler.only(), "orders").countField()).isEqualTo("orderCount");
    }

    @Test
    void dependsOnDefaultsToPreviousMemberInSuperclass() {
        Compilation compilation = compiler.compile(
            source("Base", "public class Base {",
                "@BookField(order = 1, length = 3) private Long orderCount;",
                accessors("Long", "orderCount"),
                "}"),
            source("Order", "@BookOperation public class Order extends Base {",
                "@BookCollection(order = 2, maxOccurs = 3, dependsOn = \"\")",
                "private List<Line> orders;",
                accessors("List<Line>", "orders"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(member(compiler.only(), "orders").countField()).isEqualTo("orderCount");
    }

    @Test
    void countFieldInheritsCollectionDirection() {
        Compilation compilation = compiler.compile(
            source("Order", "@BookOperation public class Order {",
                "@BookField(order = 1, length = 3) private int orderCount;",
                accessors("int", "orderCount"),
                "@BookCollection(order = 2, maxOccurs = 3) private List<Line> orders;",
                accessors("List<Line>", "orders"),
                "@BookField(order = 3, length = 3) private int inputCount;",
                accessors("int", "inputCount"),
                "@BookCollection(order = 4, maxOccurs = 3, direction = Direction.INPUT)",
                "private List<Line> inputs;",
                accessors("List<Line>", "inputs"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(member(model, "orderCount").direction()).isEqualTo("OUTPUT");
        assertThat(member(model, "inputCount").direction()).isEqualTo("INPUT");
    }

    @Test
    void countFieldInTheModelIsTheMemberInstance() {
        List<String> found = new ArrayList<>();
        Compilation compilation = compiler.compileWithProcessors(
            List.of(new ModelInspector(model -> {
                for (MemberModel member : model.members()) {
                    if (member instanceof CollectionModel collection) {
                        FieldModel count = collection.countField();
                        found.add(count.direction() + " "
                            + model.members().stream().anyMatch(m -> m == count));
                    }
                }
            })),
            source("Order", "@BookOperation public class Order {",
                "@BookField(order = 1, length = 3) private int orderCount;",
                accessors("int", "orderCount"),
                "@BookCollection(order = 2, maxOccurs = 3) private List<Line> orders;",
                accessors("List<Line>", "orders"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(found).containsExactly("OUTPUT true");
    }

    @Test
    void explicitCountDirectionIsKept() {
        Compilation compilation = compiler.compile(
            source("Order", "@BookOperation public class Order {",
                "@BookField(order = 1, length = 3, direction = Direction.INPUT_OUTPUT)",
                "private int orderCount;",
                accessors("int", "orderCount"),
                "@BookCollection(order = 2, maxOccurs = 3) private List<Line> orders;",
                accessors("List<Line>", "orders"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(member(compiler.only(), "orderCount").direction()).isEqualTo("INPUT_OUTPUT");
    }

    @Test
    void previousMemberNotIntegerFails() {
        Compilation compilation = compiler.compile(
            source("Order", "@BookOperation public class Order {",
                "@BookField(order = 1, length = 10) private String customerId;",
                accessors("String", "customerId"),
                "@BookCollection(order = 2, maxOccurs = 3) private List<Line> orders;",
                accessors("List<Line>", "orders"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining("Order.orders (order 2): "
            + "dependsOn field 'customerId' must be int, Integer, long, or Long");
    }

    @Test
    void charsetFromDefaults() {
        Compilation compilation = compiler.compile(source("Order",
            "@BookDefaults(charset = \"IBM500\") @BookOperation public class Order { }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().charsetName()).isEqualTo("IBM500");
    }

    @Test
    void emptyClassCharsetOverridesPackageCharset() {
        Compilation compilation = compiler.compile(
            TestCompiler.packageInfo("test", "@BookDefaults(charset = \"IBM500\")"),
            source("Order", "@BookDefaults(charset = \"\") @BookOperation public class Order { }"),
            source("Invoice", "@BookOperation public class Invoice { }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.snapshots).extracting(ModelSnapshot::operationName,
                ModelSnapshot::charsetName)
            .containsExactlyInAnyOrder(tuple("Order", ""), tuple("Invoice", "IBM500"));
    }

    @Test
    void defaultsUsageIgnoredForTextAndTemporal() {
        Compilation compilation = compiler.compile(
            TestCompiler.packageInfo("test", "@BookDefaults(usage = Usage.COMP3, signed = true)"),
            source("Order", "@BookOperation public class Order {",
                "@BookField(order = 1, pic = \"X(10)\") private String customerId;",
                accessors("String", "customerId"),
                "@BookField(order = 2) private LocalDate shipped;",
                accessors("LocalDate", "shipped"),
                "@BookField(order = 3, length = 1, converter = FlagConverter.class)",
                "private Boolean flag;",
                accessors("Boolean", "flag"),
                "@BookField(order = 4, length = 5) private Long quantity;",
                accessors("Long", "quantity"),
                "}"),
            source("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean> {",
                "public Boolean decode(byte[] b, FieldContext c) { return null; }",
                "public byte[] encode(Boolean v, FieldContext c) { return null; }",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(model.members()).extracting(ModelSnapshot.Member::name,
                ModelSnapshot.Member::usage, ModelSnapshot.Member::signed)
            .containsExactly(tuple("customerId", "DISPLAY", false),
                tuple("shipped", "DISPLAY", false), tuple("flag", "DISPLAY", false),
                tuple("quantity", "COMP3", true));
    }

    @Test
    void nestedOperationClassUsesItsOwnDefaultsThenThePackage() {
        Compilation compilation = compiler.compile(
            TestCompiler.packageInfo("test", "@BookDefaults(usage = Usage.COMP3)"),
            source("Outer",
                "@BookDefaults(usage = Usage.COMP, signed = true) public class Outer {",
                "@BookDefaults(charset = \"IBM500\")",
                "@BookOperation public static class Inner {",
                "@BookField(order = 1, length = 5) private int quantity;",
                accessors("int", "quantity"),
                "}",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(model.charsetName()).isEqualTo("IBM500");
        // The enclosing class is not used: the usage comes from the package.
        assertThat(model.members().get(0).usage()).isEqualTo("COMP3");
        assertThat(model.members().get(0).signed()).isFalse();
    }

    private static JavaFileObject line() {
        return source("Line", "public class Line {",
            "@BookField(order = 1, length = 5) private String sku;",
            accessors("String", "sku"),
            "}");
    }

    /** Builds the models with the real builder and gives them to a test check. */
    static final class ModelInspector extends AbstractProcessor {

        private final Consumer<OperationModel> check;

        ModelInspector(Consumer<OperationModel> check) {
            this.check = check;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(ModelBuilder.BOOK_OPERATION);
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            ModelBuilder builder = new ModelBuilder(processingEnv);
            for (TypeElement type : ElementFilter.typesIn(round.getElementsAnnotatedWith(
                processingEnv.getElementUtils().getTypeElement(ModelBuilder.BOOK_OPERATION)))) {
                builder.build(type, false).ifPresent(check);
            }
            return false;
        }
    }
}
