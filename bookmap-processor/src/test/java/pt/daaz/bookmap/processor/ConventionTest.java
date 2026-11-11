package pt.daaz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.daaz.bookmap.processor.TestCompiler.accessors;
import static pt.daaz.bookmap.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
