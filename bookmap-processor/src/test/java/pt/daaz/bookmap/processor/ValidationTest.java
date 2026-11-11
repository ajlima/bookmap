package pt.daaz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pt.daaz.bookmap.processor.TestCompiler.accessors;
import static pt.daaz.bookmap.processor.TestCompiler.lombok;
import static pt.daaz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ValidationTest {

    enum ProcessorOrder { LOMBOK_FIRST, BOOKMAP_FIRST }

    private final TestCompiler compiler = TestCompiler.create();

    private Compilation compile(JavaFileObject... files) {
        return compiler.compile(files);
    }

    private static void assertError(Compilation compilation, String descriptor, String phrase) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(descriptor + ": " + phrase);
    }

    /** One operation class with the given members and their accessors. */
    private static JavaFileObject order(String... members) {
        return source("Order",
            "@BookOperation public class Order {",
            String.join("\n", members),
            "}");
    }

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    @Test
    void validClassCompiles() {
        Compilation compilation = compile(
            source("Order",
                "@BookOperation public class Order implements Continuable, Validatable {",
                field("@BookField(order = 1, length = 10)", "String", "customerId"),
                field("@BookField(order = 2, length = 9, usage = Usage.COMP)", "int", "a"),
                field("@BookField(order = 3, length = 9, signed = true)", "Integer", "b"),
                field("@BookField(order = 4, length = 18, usage = Usage.COMP3)", "long", "c"),
                field("@BookField(order = 5, length = 18)", "Long", "d"),
                field("@BookField(order = 6, length = 15, scale = 2, usage = Usage.COMP3)",
                    "BigDecimal", "total"),
                field("@BookField(order = 7, length = 8)", "LocalDate", "orderDate"),
                field("@BookField(order = 8, length = 6, pattern = \"HHmmss\")", "LocalTime",
                    "orderTime"),
                field("@BookField(order = 9, length = 14, pattern = \"yyyyMMddHHmmss\")",
                    "LocalDateTime", "created"),
                field("@BookField(order = 10, length = 1, converter = FlagConverter.class)",
                    "boolean", "express"),
                field("@BookField(order = 11, length = 4, converter = NoConverter.class)",
                    "String", "region"),
                field("@BookField(order = 12, length = 3, direction = Direction.OUTPUT)", "int",
                    "lineCount"),
                field("@BookCollection(order = 13, maxOccurs = 5, dependsOn = \"lineCount\")",
                    "List<Line>", "lines"),
                "public boolean hasContinuation() { return false; }",
                "public void validate() { }",
                "}"),
            source("Line",
                "public class Line {",
                field("@BookField(order = 1, length = 5)", "String", "sku"),
                field("@BookField(order = 2, length = 3, usage = Usage.COMP3)", "int", "quantity"),
                "}"),
            source("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean> {",
                "public Boolean decode(byte[] b, FieldContext c) { return null; }",
                "public byte[] encode(Boolean v, FieldContext c) { return null; }",
                "}"));

        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(model.operationName()).isEqualTo("Order");
        assertThat(model.charsetName()).isEmpty();
        assertThat(model.continuable()).isTrue();
        assertThat(model.validatable()).isTrue();
        assertThat(model.members()).extracting(ModelSnapshot.Member::name).containsExactly(
            "customerId", "a", "b", "c", "d", "total", "orderDate", "orderTime", "created",
            "express", "region", "lineCount", "lines");
        assertThat(model.members()).extracting(ModelSnapshot.Member::javaKind).containsExactly(
            "STRING", "INT", "INTEGER", "LONG", "LONG_BOXED", "BIG_DECIMAL", "LOCAL_DATE",
            "LOCAL_TIME", "LOCAL_DATE_TIME", "CONVERTED", "STRING", "INT", "");
        assertThat(model.members()).extracting(ModelSnapshot.Member::byteLength).containsExactly(
            10, 4, 9, 10, 18, 8, 8, 6, 14, 1, 4, 3, 7);
        ModelSnapshot.Member express = model.members().get(9);
        assertThat(express.converter()).isEqualTo("test.FlagConverter");
        assertThat(express.getter()).isEqualTo("getExpress");
        assertThat(model.members().get(10).converter()).isEmpty();
        ModelSnapshot.Member lines = model.members().get(12);
        assertThat(lines.countField()).isEqualTo("lineCount");
        assertThat(lines.maxOccurs()).isEqualTo(5);
        assertThat(lines.getter()).isEqualTo("getLines");
        assertThat(lines.setter()).isEqualTo("setLines");
        assertThat(lines.itemFields()).extracting(ModelSnapshot.Member::owner,
                ModelSnapshot.Member::name, ModelSnapshot.Member::direction,
                ModelSnapshot.Member::byteLength)
            .containsExactly(
                tuple("Line", "sku", "OUTPUT", 5),
                tuple("Line", "quantity", "OUTPUT", 2));
    }

    // ---- order ----

    @Test
    void duplicateOrder() {
        assertError(compile(order(
                field("@BookField(order = 10, length = 1)", "String", "a"),
                field("@BookField(order = 10, length = 1)", "String", "b"))),
            "Order.b (order 10)", "duplicate order, also used by Order.a");
    }

    @Test
    void duplicateOrderAcrossSuperclass() {
        Compilation compilation = compile(
            source("Base", "public class Base {",
                field("@BookField(order = 1, length = 1)", "String", "a"), "}"),
            source("Order", "@BookOperation public class Order extends Base {",
                field("@BookField(order = 1, length = 1)", "String", "b"), "}"));
        assertError(compilation, "Order.b (order 1)", "duplicate order, also used by Base.a");
    }

    @Test
    void duplicateOrderInItemClass() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")),
            source("Line", "public class Line {",
                field("@BookField(order = 1, length = 1)", "String", "a"),
                field("@BookField(order = 1, length = 1)", "String", "b"), "}"));
        assertError(compilation, "Line.b (order 1)", "duplicate order, also used by Line.a");
    }

    // ---- type x usage ----

    @Test
    void stringWithComp3() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 5, usage = Usage.COMP3)", "String", "id"))),
            "Order.id (order 1)", "COMP3 is not permitted for String");
    }

    @Test
    void temporalWithComp() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 8, usage = Usage.COMP)", "LocalDate", "day"))),
            "Order.day (order 1)", "COMP is not permitted for LocalDate");
    }

    @Test
    void intWithScale() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 5, scale = 2)", "int", "qty"))),
            "Order.qty (order 1)", "scale must be 0 for int");
    }

    @Test
    void longBoxedWithScale() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 5, scale = 1)", "Long", "qty"))),
            "Order.qty (order 1)", "scale must be 0 for Long");
    }

    @Test
    void intLongerThanNineDigits() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 10)", "Integer", "qty"))),
            "Order.qty (order 1)", "length 10 is more than 9 digits for Integer");
    }

    @Test
    void longLongerThanEighteenDigits() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 19, usage = Usage.COMP3)", "long", "qty"))),
            "Order.qty (order 1)", "length 19 is more than 18 digits for long");
    }

    @Test
    void compLongerThanEighteenDigits() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 19, usage = Usage.COMP)", "BigDecimal",
                    "amount"))),
            "Order.amount (order 1)", "COMP length must not be more than 18");
    }

    @Test
    void bigDecimalScaleMoreThanLength() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3, scale = 4)", "BigDecimal", "rate"))),
            "Order.rate (order 1)", "scale must not be more than length");
    }

    @Test
    void lengthBelowOne() {
        assertError(compile(order(field("@BookField(order = 1, length = 0)", "String", "id"))),
            "Order.id (order 1)", "length must be at least 1");
    }

    @Test
    void negativeScale() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 5, scale = -1)", "BigDecimal", "rate"))),
            "Order.rate (order 1)", "scale must not be negative");
    }

    @Test
    void missingLengthAndPic() {
        assertError(compile(order(field("@BookField(order = 1)", "String", "id"))),
            "Order.id (order 1)", "pic or length is missing");
    }

    @Test
    void unsupportedTypeWithoutConverter() {
        assertError(compile(order(field("@BookField(order = 1, length = 1)", "boolean", "flag"))),
            "Order.flag (order 1)", "type boolean is not supported without a converter");
    }

    @Test
    void unsupportedDeclaredTypeWithoutConverter() {
        assertError(compile(order(field("@BookField(order = 1, length = 1)", "Object", "any"))),
            "Order.any (order 1)", "type java.lang.Object is not supported without a converter");
    }

    // ---- converters ----

    private static JavaFileObject converter(String name, String declaration, String javaType) {
        return source(name,
            declaration + " {",
            "public " + javaType + " decode(byte[] b, FieldContext c) { return null; }",
            "public byte[] encode(" + javaType + " v, FieldContext c) { return null; }",
            "}");
    }

    @Test
    void converterWithoutNoArgConstructor() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "Boolean", "flag")),
            source("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean> {",
                "public FlagConverter(int x) { }",
                "public Boolean decode(byte[] b, FieldContext c) { return null; }",
                "public byte[] encode(Boolean v, FieldContext c) { return null; }",
                "}"));
        assertError(compilation, "Order.flag (order 1)",
            "converter test.FlagConverter must be a public class with a public no-argument"
                + " constructor");
    }

    @Test
    void converterNotPublic() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "Boolean", "flag")),
            converter("FlagConverter", "class FlagConverter implements FieldConverter<Boolean>",
                "Boolean"));
        assertError(compilation, "Order.flag (order 1)",
            "converter test.FlagConverter must be a public class");
    }

    @Test
    void converterAbstract() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "Boolean", "flag")),
            converter("FlagConverter",
                "public abstract class FlagConverter implements FieldConverter<Boolean>",
                "Boolean"));
        assertError(compilation, "Order.flag (order 1)",
            "converter test.FlagConverter must be a public class");
    }

    @Test
    void converterTypeMismatch() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "String", "flag")),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        assertError(compilation, "Order.flag (order 1)",
            "converter type java.lang.Boolean does not agree with field type java.lang.String");
    }

    @Test
    void genericItemClassIsRejected() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1)", "int", "boxCount"),
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Box<String>>", "boxes")),
            source("Box",
                "public class Box<T> {",
                field("@BookField(order = 1, length = 3)", "String", "code"),
                "}"));
        assertError(compilation, "Order.boxes (order 2)",
            "item class test.Box must not have type parameters");
    }

    @Test
    void rawConverterIsRejected() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = RawConverter.class)",
                "String", "flag")),
            converter("RawConverter", "public class RawConverter implements FieldConverter",
                "Object"));
        assertError(compilation, "Order.flag (order 1)",
            "converter test.RawConverter must implement FieldConverter with a type argument");
    }

    @Test
    void rawConverterOnObjectFieldIsRejected() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = RawConverter.class)",
                "Object", "any")),
            converter("RawConverter", "public class RawConverter implements FieldConverter",
                "Object"));
        assertError(compilation, "Order.any (order 1)",
            "converter test.RawConverter must implement FieldConverter with a type argument");
    }

    @Test
    void converterThroughRawSuperclassIsRejected() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = CodeConverter.class)",
                "Object", "any")),
            source("BaseConverter",
                "public abstract class BaseConverter<T> implements FieldConverter<T> {",
                "}"),
            converter("CodeConverter", "public class CodeConverter extends BaseConverter",
                "Object"));
        assertError(compilation, "Order.any (order 1)",
            "converter test.CodeConverter must implement FieldConverter with a type argument");
    }

    @Test
    void genericConverterIsRejected() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "Boolean", "flag")),
            converter("FlagConverter",
                "public class FlagConverter<T> implements FieldConverter<Boolean>", "Boolean"));
        assertError(compilation, "Order.flag (order 1)",
            "converter test.FlagConverter must not have type parameters");
    }

    @Test
    void objectFieldWithObjectConverterIsAccepted() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, converter = AnyConverter.class)",
                "Object", "any")),
            converter("AnyConverter", "public class AnyConverter implements FieldConverter<Object>",
                "Object"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    void converterThroughSuperclassAgrees() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 3, converter = CodeConverter.class)",
                "int", "code")),
            source("BaseConverter",
                "public abstract class BaseConverter<T> implements Runnable, FieldConverter<T> {",
                "public void run() { }",
                "}"),
            source("CodeConverter",
                "public class CodeConverter extends BaseConverter<Integer> {",
                "public Integer decode(byte[] b, FieldContext c) { return null; }",
                "public byte[] encode(Integer v, FieldContext c) { return null; }",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).converter()).isEqualTo("test.CodeConverter");
    }

    @Test
    void converterWithoutLength() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, converter = FlagConverter.class)", "Boolean",
                "flag")),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        assertError(compilation, "Order.flag (order 1)", "pic or length is missing");
    }

    // ---- accessors ----

    @Test
    void missingSetterWithoutLombok() {
        Compilation compilation = compile(order(
            "@BookField(order = 1, length = 5) private String id;",
            "public String getId() { return id; }"));
        assertError(compilation, "Order.id (order 1)", "missing setter setId");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void missingGetterWithoutLombok() {
        Compilation compilation = compile(order(
            "@BookField(order = 1, length = 5) private String id;",
            "public void setId(String v) { id = v; }"));
        assertError(compilation, "Order.id (order 1)", "missing getter getId");
    }

    @Test
    void accessorsMustBePublicInstanceMethodsWithTheFieldType() {
        Compilation compilation = compile(order(
            "@BookField(order = 1, length = 5) private String id;",
            "String getId() { return id; }",
            "public static void setId(String v) { }",
            "@BookField(order = 2, length = 5) private String code;",
            "public Object getCode() { return code; }",
            "public void setCode(Object v) { }",
            "public void setCode() { }",
            "public String getCode(int x) { return code; }",
            "@BookField(order = 3, length = 5) private String other;",
            "public static String getOther() { return null; }",
            "public void setOther(String a, String b) { }"));
        assertError(compilation, "Order.id (order 1)", "missing getter getId");
        assertError(compilation, "Order.id (order 1)", "missing setter setId");
        assertError(compilation, "Order.code (order 2)", "missing getter getCode");
        assertError(compilation, "Order.code (order 2)", "missing setter setCode");
        assertError(compilation, "Order.other (order 3)", "missing getter getOther");
        assertError(compilation, "Order.other (order 3)", "missing setter setOther");
    }

    @Test
    void accessorsInSuperclassAndChainedSetterAreAccepted() {
        Compilation compilation = compile(
            source("Base", "public class Base {",
                "public String getId() { return null; }",
                "public Base setId(String v) { return this; }",
                "}"),
            source("Order", "@BookOperation public class Order extends Base {",
                "@BookField(order = 1, length = 5) private String id;",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).setter()).isEqualTo("setId");
    }

    @Test
    void missingAccessorsAreNotReportedBeforeTheLastRound() {
        // Another error stops the deferral. A generator can still add the accessors later, so
        // the processor does not report them in this round.
        Compilation compilation = compile(order(
            "@BookField(order = 1, length = 5) private String id;",
            field("@BookField(order = 2)", "String", "code")));
        assertError(compilation, "Order.code (order 2)", "pic or length is missing");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    // ---- Lombok ----

    private Compilation compileWithLombok(ProcessorOrder order, JavaFileObject... files) {
        List<Processor> processors = order == ProcessorOrder.LOMBOK_FIRST
            ? List.of(lombok(), compiler.processor())
            : List.of(compiler.processor(), lombok());
        return compiler.compileWithProcessors(processors, files);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void lombokDataSatisfiesAccessors(ProcessorOrder order) {
        Compilation compilation = compileWithLombok(order,
            source("Order", "@lombok.Data @BookOperation public class Order {",
                "@BookField(order = 1, length = 3) private int count;",
                "@BookCollection(order = 2, maxOccurs = 4, dependsOn = \"count\")",
                "private List<Line> lines;",
                "}"),
            source("Line", "@lombok.Data public class Line {",
                "@BookField(order = 1, length = 5) private String sku;",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot model = compiler.only();
        assertThat(model.members()).extracting(ModelSnapshot.Member::getter)
            .containsExactly("getCount", "getLines");
        assertThat(model.members().get(1).itemFields().get(0).setter()).isEqualTo("setSku");
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void lombokGetterSetterOnSomeFields(ProcessorOrder order) {
        Compilation compilation = compileWithLombok(order,
            source("Order", "@BookOperation public class Order {",
                "@lombok.Getter @lombok.Setter @BookField(order = 1, length = 5) private String id;",
                field("@BookField(order = 2, length = 5)", "String", "code"),
                "@lombok.Getter @BookField(order = 3, length = 5) private String region;",
                "public void setRegion(String v) { region = v; }",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::setter)
            .containsExactly("setId", "setCode", "setRegion");
    }

    @Test
    void lombokClassGetterAndSetter() {
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Order", "@lombok.Getter @lombok.Setter @BookOperation public class Order {",
                "@BookField(order = 1, length = 5) private String id;",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).getter()).isEqualTo("getId");
    }

    @Test
    void lombokDataFinalFieldHasNoSetter() {
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Order", "@lombok.Data @BookOperation public class Order {",
                "@BookField(order = 1, length = 5) private final String id = \"A\";",
                "}"));
        assertError(compilation, "Order.id (order 1)", "missing setter setId");
    }

    @Test
    void lombokSetterOnFinalFieldHasNoSetter() {
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Order", "@lombok.Getter @lombok.Setter @BookOperation public class Order {",
                "@lombok.Setter @BookField(order = 1, length = 5) private final String id = \"A\";",
                "}"));
        assertError(compilation, "Order.id (order 1)", "missing setter setId");
    }

    @Test
    void lombokAccessLevelNoneGivesNoAccessor() {
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Order", "@lombok.Data @BookOperation public class Order {",
                "@lombok.Getter(lombok.AccessLevel.NONE) @BookField(order = 1, length = 5)",
                "private String id;",
                "}"),
            source("Invoice",
                "@lombok.Data @lombok.Setter(lombok.AccessLevel.NONE) @BookOperation",
                "public class Invoice {",
                "@BookField(order = 1, length = 5) private String id;",
                "}"));
        assertError(compilation, "Order.id (order 1)", "missing getter getId");
        assertError(compilation, "Invoice.id (order 1)", "missing setter setId");
        CompilationSubject.assertThat(compilation).hadErrorCount(2);
    }

    @Test
    void lombokCallableAccessLevelsGiveAccessors() {
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Order", "@BookOperation public class Order {",
                "@lombok.Getter(lombok.AccessLevel.PUBLIC) @lombok.Setter",
                "@BookField(order = 1, length = 5) private String id;",
                "@lombok.Getter(lombok.AccessLevel.PACKAGE)",
                "@lombok.Setter(lombok.AccessLevel.PROTECTED)",
                "@BookField(order = 2, length = 5) private String code;",
                "@lombok.Getter(lombok.AccessLevel.MODULE) @lombok.Setter",
                "@BookField(order = 3, length = 5) private String region;",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::getter,
                ModelSnapshot.Member::setter)
            .containsExactly(tuple("getId", "setId"), tuple("getCode", "setCode"),
                tuple("getRegion", "setRegion"));
    }

    @Test
    void lombokPrivateAccessLevelGivesNoAccessor() {
        Compilation compilation = compileWithLombok(ProcessorOrder.LOMBOK_FIRST,
            source("Order", "@BookOperation public class Order {",
                "@lombok.Getter(lombok.AccessLevel.PRIVATE) @lombok.Setter",
                "@BookField(order = 1, length = 5) private String id;",
                "@lombok.Getter @lombok.Setter(lombok.AccessLevel.PRIVATE)",
                "@BookField(order = 2, length = 5) private String code;",
                "}"));
        assertError(compilation, "Order.id (order 1)", "missing getter getId");
        assertError(compilation, "Order.code (order 2)", "missing setter setCode");
        CompilationSubject.assertThat(compilation).hadErrorCount(2);
    }

    @ParameterizedTest
    @EnumSource(ProcessorOrder.class)
    void lombokGetterOfPrimitiveBooleanIsIsName(ProcessorOrder order) {
        Compilation compilation = compileWithLombok(order,
            source("Order", "@lombok.Data @BookOperation public class Order {",
                "@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "private boolean express;",
                "@BookField(order = 2, length = 1, converter = FlagConverter.class)",
                "private Boolean gift;",
                "}"),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::getter,
                ModelSnapshot.Member::setter)
            .containsExactly(tuple("isExpress", "setExpress"), tuple("getGift", "setGift"));
    }

    @Test
    void missingGetterOfPrimitiveBooleanNamesTheIsName() {
        Compilation compilation = compile(
            order("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "private boolean gift;",
                "public void setGift(boolean v) { gift = v; }"),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        assertError(compilation, "Order.gift (order 1)", "missing getter isGift");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void isPrefixOfPrimitiveBooleanIsRemovedAsLombokDoes() {
        Compilation compilation = compile(
            order("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "private boolean isActive;",
                "public boolean isActive() { return isActive; }",
                "public void setActive(boolean v) { isActive = v; }",
                "@BookField(order = 2, length = 1, converter = FlagConverter.class)",
                "private boolean island;",
                "public boolean isIsland() { return island; }",
                "public void setIsland(boolean v) { island = v; }",
                "@BookField(order = 3, length = 1, converter = FlagConverter.class)",
                "private boolean is;",
                "public boolean isIs() { return is; }",
                "public void setIs(boolean v) { is = v; }",
                "@BookField(order = 4, length = 1, converter = FlagConverter.class)",
                "private boolean isOpen;",
                "public boolean getOpen() { return isOpen; }",
                "public void setOpen(boolean v) { isOpen = v; }"),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::getter,
                ModelSnapshot.Member::setter)
            .containsExactly(tuple("isActive", "setActive"), tuple("isIsland", "setIsland"),
                tuple("isIs", "setIs"), tuple("getOpen", "setOpen"));
    }

    @Test
    void isPrefixIsRemovedWhenTheThirdCharacterIsNotLowercase() {
        Compilation compilation = compile(
            order("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "private boolean is_open;",
                "public boolean is_open() { return is_open; }",
                "public void set_open(boolean v) { is_open = v; }",
                "@BookField(order = 2, length = 1, converter = FlagConverter.class)",
                "private boolean is2fa;",
                "public boolean is2fa() { return is2fa; }",
                "public void set2fa(boolean v) { is2fa = v; }"),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::getter,
                ModelSnapshot.Member::setter)
            .containsExactly(tuple("is_open", "set_open"), tuple("is2fa", "set2fa"));
    }

    @Test
    void missingSetterOfIsPrefixedBooleanNamesTheLombokName() {
        Compilation compilation = compile(
            order("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "private boolean isActive;",
                "public boolean isActive() { return isActive; }",
                "public void setIsActive(boolean v) { isActive = v; }"),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        assertError(compilation, "Order.isActive (order 1)", "missing setter setActive");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void handwrittenIsGetterOfPrimitiveBooleanIsFound() {
        Compilation compilation = compile(
            order("@BookField(order = 1, length = 1, converter = FlagConverter.class)",
                "private boolean express;",
                "public boolean isExpress() { return express; }",
                "public void setExpress(boolean v) { express = v; }"),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).getter()).isEqualTo("isExpress");
    }

    @Test
    void lombokOnSuperclassDoesNotApplyToSubclassFields() {
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Base", "@lombok.Data public class Base {",
                "@BookField(order = 1, length = 5) private String id;",
                "}"),
            source("Order", "@BookOperation public class Order extends Base {",
                "@BookField(order = 2, length = 5) private String code;",
                "}"));
        assertError(compilation, "Order.code (order 2)", "missing getter getCode");
        assertError(compilation, "Order.code (order 2)", "missing setter setCode");
        CompilationSubject.assertThat(compilation).hadErrorCount(2);
    }

    @Test
    void lombokAccessorsAnnotationNeedsVisibleMethods() {
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Order", "@lombok.Data @BookOperation public class Order {",
                "@lombok.experimental.Accessors(fluent = true)",
                "@BookField(order = 1, length = 5) private String id;",
                "}"),
            source("Invoice",
                "@lombok.Data @lombok.experimental.Accessors(fluent = true) @BookOperation",
                "public class Invoice {",
                "@BookField(order = 1, length = 5) private String id;",
                "}"));
        assertError(compilation, "Order.id (order 1)", "missing getter getId");
        assertError(compilation, "Invoice.id (order 1)", "missing setter setId");
    }

    // ---- collections ----

    private static JavaFileObject line() {
        return source("Line", "public class Line {",
            field("@BookField(order = 1, length = 5)", "String", "sku"), "}");
    }

    @Test
    void collectionTypeNotList() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = 3)", "int", "count"),
            field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                "Set<Line>", "lines"),
            field("@BookCollection(order = 3, maxOccurs = 2, dependsOn = \"count\")", "String",
                "text"),
            field("@BookCollection(order = 4, maxOccurs = 2, dependsOn = \"count\")", "List",
                "raw"),
            field("@BookCollection(order = 5, maxOccurs = 2, dependsOn = \"count\")",
                "List<? extends Line>", "wild")), line());
        assertError(compilation, "Order.lines (order 2)", "a collection must have the type List<X>");
        assertError(compilation, "Order.text (order 3)", "a collection must have the type List<X>");
        assertError(compilation, "Order.raw (order 4)", "a collection must have the type List<X>");
        assertError(compilation, "Order.wild (order 5)", "a collection must have the type List<X>");
    }

    @Test
    void collectionDependsOnMissingField() {
        assertError(compile(order(
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")), line()),
            "Order.lines (order 2)", "dependsOn field 'count' does not exist");
    }

    @Test
    void collectionWithoutDependsOnAndWithoutPreviousMember() {
        assertError(compile(order(
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Line>", "lines"),
                field("@BookField(order = 3, length = 3)", "int", "count")), line()),
            "Order.lines (order 2)", "dependsOn is missing and no member comes before the"
                + " collection");
    }

    @Test
    void previousMemberIsACollection() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Line>", "first"),
                field("@BookCollection(order = 3, maxOccurs = 2)", "List<Line>", "second")),
                line()),
            "Order.second (order 3)", "dependsOn field 'first' must be int, Integer, long, or Long");
    }

    @Test
    void collectionDependsOnAfterCollection() {
        assertError(compile(order(
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines"),
                field("@BookField(order = 3, length = 3)", "int", "count")), line()),
            "Order.lines (order 2)", "dependsOn field 'count' must come before the collection");
    }

    @Test
    void collectionDependsOnWrongDirection() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3, direction = Direction.INPUT)", "int",
                    "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")), line()),
            "Order.lines (order 2)",
            "dependsOn field 'count' has direction INPUT, which does not include OUTPUT");
    }

    @Test
    void inputCollectionDependsOnOutputCount() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3, direction = Direction.OUTPUT)", "long",
                    "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\","
                    + " direction = Direction.INPUT)", "List<Line>", "lines")), line()),
            "Order.lines (order 2)",
            "dependsOn field 'count' has direction OUTPUT, which does not include INPUT");
    }

    @Test
    void inputOutputCollectionNeedsInputOutputCount() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = 3, direction = Direction.INPUT)", "Long", "a"),
            field("@BookField(order = 2, length = 3, direction = Direction.OUTPUT)", "Integer",
                "b"),
            field("@BookCollection(order = 3, maxOccurs = 2, dependsOn = \"a\","
                + " direction = Direction.INPUT_OUTPUT)", "List<Line>", "x"),
            field("@BookCollection(order = 4, maxOccurs = 2, dependsOn = \"b\","
                + " direction = Direction.INPUT_OUTPUT)", "List<Line>", "y")), line());
        assertError(compilation, "Order.x (order 3)",
            "dependsOn field 'a' has direction INPUT, which does not include INPUT_OUTPUT");
        assertError(compilation, "Order.y (order 4)",
            "dependsOn field 'b' has direction OUTPUT, which does not include INPUT_OUTPUT");
    }

    @Test
    void collectionDependsOnNonInteger() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3)", "BigDecimal", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")), line()),
            "Order.lines (order 2)", "dependsOn field 'count' must be int, Integer, long, or Long");
    }

    @Test
    void collectionDependsOnAnotherCollection() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "first"),
                field("@BookCollection(order = 3, maxOccurs = 2, dependsOn = \"first\")",
                    "List<Line>", "second")), line()),
            "Order.second (order 3)", "dependsOn field 'first' must be int, Integer, long, or Long");
    }

    @Test
    void collectionDependsOnInvalidFieldReportsOnlyTheFieldError() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1)", "int", "count"),
            field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                "List<Line>", "lines")), line());
        assertError(compilation, "Order.count (order 1)", "pic or length is missing");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void inputCollectionWithInputOutputCount() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = 3, direction = Direction.INPUT_OUTPUT)", "int",
                "count"),
            field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\","
                + " direction = Direction.INPUT)", "List<Line>", "lines")), line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(1).direction()).isEqualTo("INPUT");
    }

    @Test
    void missingAccessorsOfCollectionsAndItems() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 3)", "int", "a"),
                field("@BookField(order = 2, length = 3)", "int", "b"),
                field("@BookField(order = 3, length = 3)", "int", "c"),
                field("@BookCollection(order = 4, maxOccurs = 2, dependsOn = \"a\")",
                    "List<Part>", "parts"),
                "@BookCollection(order = 5, maxOccurs = 2, dependsOn = \"b\")",
                "private List<Line> noGetter;",
                "public void setNoGetter(List<Line> v) { }",
                "@BookCollection(order = 6, maxOccurs = 2, dependsOn = \"c\")",
                "private List<Line> noSetter;",
                "public List<Line> getNoSetter() { return noSetter; }"),
            line(),
            source("Part", "public class Part {",
                "@BookField(order = 1, length = 5) private String sku;",
                "public String getSku() { return sku; }",
                "}"));
        assertError(compilation, "Part.sku (order 1)", "missing setter setSku");
        assertError(compilation, "Order.noGetter (order 5)", "missing getter getNoGetter");
        assertError(compilation, "Order.noSetter (order 6)", "missing setter setNoSetter");
        CompilationSubject.assertThat(compilation).hadErrorCount(3);
    }

    @Test
    void countFieldSharedByTwoCollections() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines"),
                field("@BookCollection(order = 3, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "others")), line()),
            "Order.others (order 3)", "dependsOn field 'count' is already used by collection 'lines'");
    }

    @Test
    void nestedCollection() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")),
            source("Line", "public class Line {",
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "parts"),
                "}"));
        assertError(compilation, "Order.lines (order 2)",
            "item class test.Line must not contain a @BookCollection (nested collection)");
    }

    @Test
    void maxOccursBelowOne() {
        assertError(compile(order(
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 0, dependsOn = \"count\")",
                    "List<Line>", "lines")), line()),
            "Order.lines (order 2)", "maxOccurs must be at least 1");
    }

    @Test
    void itemWithoutNoArgConstructor() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")),
            source("Line", "public class Line {",
                "public Line(String sku) { this.sku = sku; }",
                field("@BookField(order = 1, length = 5)", "String", "sku"), "}"));
        assertError(compilation, "Order.lines (order 2)",
            "item class test.Line must be a public class with a public no-argument constructor");
    }

    @Test
    void itemNotInstantiable() {
        Compilation compilation = compile(
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Inner>", "inner"),
                field("@BookCollection(order = 3, maxOccurs = 2, dependsOn = \"count2\")",
                    "List<Iface>", "iface"),
                field("@BookField(order = 0, length = 3)", "int", "count2"),
                "public class Inner { }",
                "public interface Iface { }",
                "}"));
        assertError(compilation, "Order.inner (order 2)",
            "item class test.Order.Inner must be a public class with a public no-argument"
                + " constructor");
        assertError(compilation, "Order.iface (order 3)",
            "item class test.Order.Iface must be a public class with a public no-argument"
                + " constructor");
    }

    @Test
    void staticNestedItemIsAccepted() {
        Compilation compilation = compile(
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Entry>", "entries"),
                "public static class Entry {",
                field("@BookField(order = 1, length = 5)", "String", "sku"),
                "}",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(1).itemFields().get(0).owner())
            .isEqualTo("Entry");
    }

    @Test
    void itemFieldWithDirection() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")),
            source("Line", "public class Line {",
                field("@BookField(order = 1, length = 5, direction = Direction.OUTPUT)", "String",
                    "sku"), "}"));
        assertError(compilation, "Line.sku (order 1)",
            "direction must not be set on an item field");
    }

    @Test
    void itemFieldErrorsAreReported() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\")",
                    "List<Line>", "lines")),
            source("Line", "public class Line {",
                field("@BookField(order = 1)", "String", "sku"),
                "@BookField(order = 2, length = 2) private static String code;",
                "}"));
        assertError(compilation, "Line.sku (order 1)", "pic or length is missing");
        assertError(compilation, "Line.code (order 2)", "an annotated field must not be static");
    }

    @Test
    void inputOutputCollectionInContinuable() {
        Compilation compilation = compile(source("Order",
            "@BookOperation public class Order implements Continuable {",
            field("@BookField(order = 1, length = 3)", "int", "count"),
            field("@BookCollection(order = 2, maxOccurs = 2, dependsOn = \"count\","
                + " direction = Direction.INPUT_OUTPUT)", "List<Line>", "lines"),
            "public boolean hasContinuation() { return false; }",
            "}"), line());
        assertError(compilation, "Order.lines (order 2)",
            "an INPUT_OUTPUT collection is not permitted in a Continuable class");
    }

    // ---- fields ----

    @Test
    void staticAnnotatedField() {
        assertError(compile(order(
                "@BookField(order = 1, length = 5) private static String id;")),
            "Order.id (order 1)", "an annotated field must not be static");
    }

    @Test
    void staticAnnotatedCollection() {
        assertError(compile(order(
                "@BookCollection(order = 1, maxOccurs = 2, dependsOn = \"x\")",
                "private static List<Line> lines;"), line()),
            "Order.lines (order 1)", "an annotated field must not be static");
    }

    @Test
    void fieldWithBothAnnotations() {
        assertError(compile(order(
                "@BookField(order = 1, length = 5) @BookCollection(order = 1, maxOccurs = 2)",
                "private List<Line> lines;"), line()),
            "Order.lines (order 1)",
            "a field must not have both @BookField and @BookCollection");
    }

    // ---- operation class ----

    @Test
    void blankOperationName() {
        assertError(compile(source("Order",
                "@BookOperation(\"  \") public class Order { }")),
            "Order", "operation name must not be blank");
    }

    @Test
    void operationClassNotPublic() {
        assertError(compile(source("Order", "@BookOperation class Order { }")),
            "Order", "operation class must be public");
    }

    @Test
    void operationOnEnumOrInterface() {
        Compilation compilation = compile(
            source("Status", "@BookOperation public enum Status { OPEN }"),
            source("Api", "@BookOperation public interface Api { }"));
        assertError(compilation, "Status", "@BookOperation must be on a class");
        assertError(compilation, "Api", "@BookOperation must be on a class");
    }

    @Test
    void operationClassAbstract() {
        assertError(compile(source("Order", "@BookOperation public abstract class Order { }")),
            "Order", "operation class must not be abstract");
    }

    @Test
    void operationClassWithoutNoArgConstructor() {
        Compilation compilation = compile(source("Order",
            "@BookOperation public class Order {",
            "public Order(String id) { }",
            "Order() { }",
            "}"));
        assertError(compilation, "Order", "operation class must have a public no-argument constructor");
    }

    @Test
    void operationClassNonStaticInner() {
        assertError(compile(source("Outer", "public class Outer {",
                "@BookOperation public class Inner { }",
                "}")),
            "Inner", "operation class must be top-level or static");
    }

    @Test
    void operationClassGeneric() {
        assertError(compile(source("Order", "@BookOperation public class Order<T> { }")),
            "Order", "operation class must not be generic");
    }

    @Test
    void operationRecord() {
        assertError(compile(source("Order", "@BookOperation public record Order(String id) { }")),
            "Order", "operation class must not be a record");
    }

    @Test
    void duplicateOperationName() {
        Compilation compilation = compile(
            source("GetOrder", "@BookOperation public class GetOrder { }"),
            source("FindOrder", "@BookOperation(\"GetOrder\") public class FindOrder { }"));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("operation name 'GetOrder' is already used by test.");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
        assertThat(compiler.snapshots).hasSize(1);
    }

    // ---- rounds ----

    @Test
    void fieldTypeFromAnotherGeneratorDefersTheClass() {
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of(
                "test.Shipment", "public class Shipment { }",
                "test.ShipmentLine", String.join("\n", "public class ShipmentLine {",
                    field("@BookField(order = 1, length = 5)", "String", "sku"), "}")));
        Compilation compilation = compiler.compile(List.of(generator),
            source("Order", "@BookOperation public class Order {",
                field("@BookField(order = 1, length = 5, converter = ShipmentConverter.class)",
                    "Shipment", "shipment"),
                field("@BookField(order = 2, length = 3)", "int", "count"),
                field("@BookCollection(order = 3, maxOccurs = 2, dependsOn = \"count\")",
                    "List<ShipmentLine>", "lines"),
                "}"),
            source("ShipmentConverter",
                "public class ShipmentConverter implements FieldConverter<Shipment> {",
                "public Shipment decode(byte[] b, FieldContext c) { return null; }",
                "public byte[] encode(Shipment v, FieldContext c) { return null; }",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::javaKind)
            .containsExactly("CONVERTED", "INT", "");
    }

    @Test
    void superclassFromAnotherGeneratorDefersTheClass() {
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of("test.Base", String.join("\n", "public class Base {",
                field("@BookField(order = 1, length = 5)", "String", "id"), "}")));
        Compilation compilation = compiler.compile(List.of(generator),
            source("Order", "@BookOperation public class Order extends Base {",
                field("@BookField(order = 2, length = 5)", "String", "code"),
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::owner)
            .containsExactly("Base", "Order");
    }

    @Test
    void unresolvedConverterIsLeftToJavac() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = 5, converter = Missing.class)", "String", "id")));
        CompilationSubject.assertThat(compilation).hadErrorContaining("cannot find symbol");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void unresolvedLengthConstantIsLeftToJavac() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = MISSING)", "String", "id")));
        CompilationSubject.assertThat(compilation).hadErrorContaining("cannot find symbol");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void unresolvedOperationNameConstantIsLeftToJavac() {
        Compilation compilation = compile(source("Order",
            "@BookOperation(MISSING) public class Order { }"));
        CompilationSubject.assertThat(compilation).hadErrorContaining("cannot find symbol");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
        assertThat(compiler.snapshots).isEmpty();
    }

    @Test
    void converterFromAnotherGeneratorDefersTheClass() {
        TestCompiler.GeneratingProcessor generator = new TestCompiler.GeneratingProcessor(
            Map.of("test.CodeConverter", String.join("\n",
                "public class CodeConverter implements FieldConverter<String> {",
                "public String decode(byte[] b, FieldContext c) { return null; }",
                "public byte[] encode(String v, FieldContext c) { return null; }",
                "}")));
        Compilation compilation = compiler.compile(List.of(generator), order(
            field("@BookField(order = 1, length = 5, converter = CodeConverter.class)", "String",
                "code")));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).converter()).isEqualTo("test.CodeConverter");
    }

    @Test
    void typeUseAnnotationsAreIgnored() {
        Compilation compilation = compile(
            source("Nullable",
                "@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)",
                "public @interface Nullable { }"),
            order(
                field("@BookField(order = 1, length = 5)", "@Nullable Integer", "qty"),
                "@BookField(order = 2, length = 5) private @Nullable String id;",
                "public String getId() { return id; }",
                "public void setId(String v) { id = v; }",
                field("@BookField(order = 3, length = 1, converter = FlagConverter.class)",
                    "@Nullable Boolean", "flag")),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::javaKind)
            .containsExactly("INTEGER", "STRING", "CONVERTED");
    }

    @Test
    void arrayTypeWithoutConverter() {
        assertError(compile(order(field("@BookField(order = 1, length = 4)", "byte[]", "data"))),
            "Order.data (order 1)", "type byte[] is not supported without a converter");
    }

    @Test
    void typeCompletedOnlyInTheLastRoundIsReported() {
        // javac gives no new source in the last round, so a deferred class cannot become valid
        // there. This test calls the report method of the last round directly.
        Compilation compilation = compiler.compileWithProcessors(
            List.of(new LastRoundReporter()), order());
        assertError(compilation, "Order",
            "codec could not be generated: the type was completed only in the last processing"
                + " round");
    }

    /** Builds a valid model and gives it to the last-round report of the processor. */
    static final class LastRoundReporter extends AbstractProcessor {

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(ModelBuilder.BOOK_OPERATION);
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> a,
                               RoundEnvironment round) {
            ModelBuilder builder = new ModelBuilder(processingEnv);
            for (TypeElement type : ElementFilter.typesIn(round.getRootElements())) {
                BookmapProcessor.reportCompletedInLastRound(processingEnv.getMessager(),
                    builder.build(type, true).orElseThrow());
            }
            return false;
        }
    }

    @Test
    void unresolvedFieldTypeIsReportedInTheLastRound() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = 5)", "Missing", "missing")));
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation)
            .hadErrorContaining("Order.missing (order 1): type Missing is not supported");
    }

    @Test
    void lombokAfterBookmapWithAccessorsAnnotationBuildsInALaterRound() {
        // Bookmap runs before Lombok. The Accessors annotation stops the name rule, so the first
        // round defers the class. Lombok starts one more round, in which its methods are visible.
        Compilation compilation = compileWithLombok(ProcessorOrder.BOOKMAP_FIRST,
            source("Order", "@lombok.Data @lombok.experimental.Accessors(chain = true)",
                "@BookOperation public class Order {",
                "@BookField(order = 1, length = 5) private String id;",
                "}"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).getter()).isEqualTo("getId");
    }

    // ---- pic ----

    @Test
    void invalidPic() {
        assertError(compile(order(field("@BookField(order = 1, pic = \"ZZ9\")", "int", "qty"))),
            "Order.qty (order 1)", "Invalid PIC string \"ZZ9\"");
    }

    @Test
    void picTogetherWithLength() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, pic = \"X(5)\", length = 5)", "String", "id"),
            field("@BookField(order = 2, pic = \"S9(5)V99\", scale = 2, signed = false,"
                + " usage = Usage.DISPLAY)", "BigDecimal", "rate")));
        assertError(compilation, "Order.id (order 1)", "pic must not be combined with length");
        assertError(compilation, "Order.rate (order 2)",
            "pic must not be combined with scale, signed, usage");
    }

    @Test
    void textPicOnInt() {
        assertError(compile(order(field("@BookField(order = 1, pic = \"X(3)\")", "int", "qty"))),
            "Order.qty (order 1)", "a text pic is not permitted for int");
    }

    @Test
    void textPicOnBigDecimal() {
        assertError(compile(order(
                field("@BookField(order = 1, pic = \"XXX\")", "BigDecimal", "rate"))),
            "Order.rate (order 1)", "a text pic is not permitted for BigDecimal");
    }

    @Test
    void numericPicOnString() {
        assertError(compile(order(
                field("@BookField(order = 1, pic = \"9(3)\")", "String", "code"))),
            "Order.code (order 1)", "a numeric pic is not permitted for String");
    }

    @Test
    void signedOrDecimalPicOnTemporal() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, pic = \"S9(8)\")", "LocalDate", "shipped"),
            field("@BookField(order = 2, pic = \"9(6)V99\")", "LocalDate", "delivered")));
        assertError(compilation, "Order.shipped (order 1)",
            "a signed or decimal pic is not permitted for LocalDate");
        assertError(compilation, "Order.delivered (order 2)",
            "a signed or decimal pic is not permitted for LocalDate");
    }

    @Test
    void picUsageIsCheckedAgainstTheType() {
        assertError(compile(order(
                field("@BookField(order = 1, pic = \"9(8) COMP-3\")", "LocalDate", "shipped"))),
            "Order.shipped (order 1)", "COMP3 is not permitted for LocalDate");
    }

    @Test
    void emptyPicIsNotSet() {
        assertError(compile(order(field("@BookField(order = 1, pic = \"\")", "String", "id"))),
            "Order.id (order 1)", "pic or length is missing");
    }

    @Test
    void picOnConverterField() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, pic = \"S9(3) COMP-3\","
                + " converter = FlagConverter.class)", "Boolean", "flag")),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        ModelSnapshot.Member flag = compiler.only().members().get(0);
        assertThat(flag.length()).isEqualTo(3);
        assertThat(flag.signed()).isTrue();
        assertThat(flag.usage()).isEqualTo("COMP3");
        assertThat(flag.byteLength()).isEqualTo(2);
    }

    @Test
    void literalErrorTextIsAValue() {
        Compilation compilation = compile(source("Order",
            "@BookOperation(\"<error>\") public class Order {",
            field("@BookField(order = 1, pic = \"<error>\")", "String", "id"),
            "}"));
        assertError(compilation, "Order.id (order 1)", "Invalid PIC string \"<error>\"");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void literalErrorTextAsOperationName() {
        Compilation compilation = compile(source("Order",
            "@BookOperation(\"<error>\") public class Order { }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().operationName()).isEqualTo("<error>");
    }

    // ---- date and time patterns ----

    @Test
    void datePatternLengthMismatch() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = 6)", "LocalDate", "shipped"),
            field("@BookField(order = 2, pic = \"X(10)\", pattern = \"yyyyMMdd\")", "LocalDate",
                "delivered")));
        assertError(compilation, "Order.shipped (order 1)",
            "length 6 does not agree with pattern 'yyyyMMdd' (8 characters)");
        assertError(compilation, "Order.delivered (order 2)",
            "length 10 does not agree with pattern 'yyyyMMdd' (8 characters)");
    }

    @Test
    void invalidDatePattern() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, pattern = \"yyyyMMdd{\")", "LocalDate", "shipped"),
            field("@BookField(order = 2, length = 6, pattern = \"bb\")", "LocalTime", "time")));
        assertError(compilation, "Order.shipped (order 1)", "pattern 'yyyyMMdd{' is not valid");
        assertError(compilation, "Order.time (order 2)", "pattern 'bb' is not valid");
        CompilationSubject.assertThat(compilation).hadErrorCount(2);
    }

    @Test
    void datePatternWithVariableLength() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, pattern = \"yyyy-M-d\")", "LocalDate", "shipped"),
            field("@BookField(order = 3, pattern = \"Hmmss\")", "LocalTime", "time"),
            field("@BookField(order = 5, pattern = \"yyyyMMddhhmmssa\")", "LocalDateTime",
                "created"),
            field("@BookField(order = 6, pattern = \"yyyy-MM-dd H:mm:ss\")", "LocalDateTime",
                "changed")));
        assertError(compilation, "Order.shipped (order 1)",
            "pattern 'yyyy-M-d' gives text of variable length for LocalDate");
        assertError(compilation, "Order.time (order 3)",
            "pattern 'Hmmss' gives text of variable length for LocalTime");
        assertError(compilation, "Order.changed (order 6)",
            "pattern 'yyyy-MM-dd H:mm:ss' gives text of variable length for LocalDateTime");
        CompilationSubject.assertThat(compilation).hadErrorCount(3);
    }

    @Test
    void monthAndDayNamesHaveAFixedLengthInTheRootLocale() {
        // Locale.ROOT gives "Dec", "Jan", "Thu", "Wed" also for MMMM and EEEE.
        Compilation compilation = compile(order(
            field("@BookField(order = 1, pattern = \"yyyyMMMMdd\")", "LocalDate", "billed"),
            field("@BookField(order = 2, pattern = \"yyyyMMddEEEE\")", "LocalDate", "due")));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members()).extracting(ModelSnapshot.Member::length)
            .containsExactly(9, 11);
    }

    @Test
    void datePatternThatDoesNotFitTheType() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, pattern = \"HHmmss\")", "LocalDate", "shipped"),
            field("@BookField(order = 2, pattern = \"yyyyQQQQ-x\")", "LocalDate", "quarter"),
            field("@BookField(order = 3, pattern = \"yyyyMMdd\")", "LocalDateTime", "created"),
            field("@BookField(order = 4, pattern = \"hhmmss\")", "LocalTime", "time")));
        assertError(compilation, "Order.shipped (order 1)",
            "pattern 'HHmmss' does not fit LocalDate");
        assertError(compilation, "Order.quarter (order 2)",
            "pattern 'yyyyQQQQ-x' does not fit LocalDate");
        assertError(compilation, "Order.created (order 3)",
            "pattern 'yyyyMMdd' does not fit LocalDateTime");
        assertError(compilation, "Order.time (order 4)", "pattern 'hhmmss' does not fit LocalTime");
        CompilationSubject.assertThat(compilation).hadErrorCount(4);
    }

    @Test
    void temporalLengthBelowOne() {
        Compilation compilation = compile(order(
            field("@BookField(order = 1, length = 0)", "LocalDate", "shipped")));
        assertError(compilation, "Order.shipped (order 1)", "length must be at least 1");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void patternOnConverterFieldIsNotChecked() {
        Compilation compilation = compile(
            order(field("@BookField(order = 1, length = 1, pattern = \"{\","
                + " converter = FlagConverter.class)", "Boolean", "flag")),
            converter("FlagConverter",
                "public class FlagConverter implements FieldConverter<Boolean>", "Boolean"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().members().get(0).pattern()).isEqualTo("{");
    }

    // ---- charset ----

    @Test
    void unknownCharsetInDefaults() {
        Compilation compilation = compile(
            source("Order", "@BookDefaults(charset = \"NOPE\") @BookOperation",
                "public class Order { }"),
            source("Invoice", "@BookDefaults(charset = \"bad name!\") @BookOperation",
                "public class Invoice { }"));
        assertError(compilation, "Order", "charset 'NOPE' is not supported");
        assertError(compilation, "Invoice", "charset 'bad name!' is not supported");
    }

    @Test
    void multiByteCharsetInDefaults() {
        assertError(compile(source("Order", "@BookDefaults(charset = \"UTF-8\") @BookOperation",
                "public class Order { }")),
            "Order", "charset 'UTF-8' is not a single-byte charset");
    }

    @Test
    void charsetThatCannotEncode() {
        assertError(compile(source("Order",
                "@BookDefaults(charset = \"ISO-2022-CN\") @BookOperation",
                "public class Order { }")),
            "Order", "charset 'ISO-2022-CN' cannot encode text");
    }

    @Test
    void unknownCharsetInPackageDefaults() {
        assertError(compile(
                TestCompiler.packageInfo("test", "@BookDefaults(charset = \"NOPE\")"),
                source("Order", "@BookOperation public class Order { }")),
            "Order", "charset 'NOPE' is not supported");
    }

    @Test
    void differentCharsetOnItemClass() {
        Compilation compilation = compile(
            source("Order", "@BookDefaults(charset = \"IBM037\") @BookOperation",
                "public class Order {",
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Line>", "lines"),
                "}"),
            source("Line", "@BookDefaults(charset = \"IBM500\") public class Line {",
                field("@BookField(order = 1, length = 5)", "String", "sku"), "}"));
        assertError(compilation, "Order",
            "charset 'IBM500' of test.Line is different from the operation charset 'IBM037'");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void charsetOnSuperclassPackageWithoutOperationCharset() {
        Compilation compilation = compile(
            TestCompiler.packageInfo("base", "@BookDefaults(charset = \"IBM500\")"),
            TestCompiler.sourceIn("base", "Base", "public class Base {",
                field("@BookField(order = 1, length = 5)", "String", "id"), "}"),
            source("Order", "@BookOperation public class Order extends base.Base { }"));
        assertError(compilation, "Order",
            "charset 'IBM500' of base.Base is different from the operation charset (none)");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void sameCharsetOnSuperclassAndItemClassIsAccepted() {
        Compilation compilation = compile(
            TestCompiler.packageInfo("test", "@BookDefaults(charset = \"IBM500\")"),
            source("Base", "@BookDefaults(charset = \"IBM500\") public class Base {",
                field("@BookField(order = 1, length = 3)", "int", "count"), "}"),
            source("Order", "@BookOperation public class Order extends Base {",
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Line>", "lines"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().charsetName()).isEqualTo("IBM500");
    }

    @Test
    void operationClassCharsetOverridesThePackageForItsPackage() {
        Compilation compilation = compile(
            TestCompiler.packageInfo("test", "@BookDefaults(charset = \"IBM500\")"),
            source("Order", "@BookDefaults(charset = \"IBM037\") @BookOperation",
                "public class Order {",
                field("@BookField(order = 1, length = 3)", "int", "count"),
                field("@BookCollection(order = 2, maxOccurs = 2)", "List<Line>", "lines"),
                "}"),
            line());
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        assertThat(compiler.only().charsetName()).isEqualTo("IBM037");
    }

    @Test
    void unresolvedDefaultsValueIsLeftToJavac() {
        Compilation compilation = compile(source("Order",
            "@BookDefaults(charset = MISSING) @BookOperation public class Order {",
            field("@BookField(order = 1, length = 5)", "String", "id"),
            "}"));
        CompilationSubject.assertThat(compilation).hadErrorContaining("cannot find symbol");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
        assertThat(compiler.snapshots).isEmpty();
    }
}
