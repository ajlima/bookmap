package pt.dazz.copymapper.processor;

import static pt.dazz.copymapper.processor.TestCompiler.accessors;
import static pt.dazz.copymapper.processor.TestCompiler.source;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * An annotation of another library with the same simple name as a CopyMapper annotation, on a
 * class that the processor maps. The IDE can import it by mistake.
 */
class ForeignAnnotationTest {

    private static final List<JavaFileObject> FOREIGN = List.of(
        foreign("Field", "FIELD", "int order();"),
        foreign("Occurs", "FIELD", "int order();"),
        foreign("Group", "FIELD", "int order();"),
        foreign("SubTypes", "TYPE", ""),
        foreign("Defaults", "TYPE", ""),
        foreign("Column", "FIELD", ""));

    private final TestCompiler compiler = TestCompiler.create();

    private static JavaFileObject foreign(String name, String target, String body) {
        return JavaFileObjects.forSourceString("other." + name, String.join("\n",
            "package other;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Target;",
            "@Target(ElementType." + target + ")",
            "public @interface " + name + " { " + body + " }"));
    }

    private static String field(String annotation, String type, String name) {
        return annotation + " private " + type + " " + name + "; " + accessors(type, name);
    }

    private static final String ID = field("@Field(order = 1, pic = \"X(10)\")", "String", "id");

    private Compilation compile(JavaFileObject... files) {
        List<JavaFileObject> all = new ArrayList<>(FOREIGN);
        all.addAll(Arrays.asList(files));
        return compiler.compile(all.toArray(JavaFileObject[]::new));
    }

    private static JavaFileObject getOrder(String classAnnotations, String... members) {
        return source("GetOrder", "@Operation " + classAnnotations + " public class GetOrder {",
            String.join("\n", members), "}");
    }

    private static void assertError(Compilation compilation, String message) {
        CompilationSubject.assertThat(compilation).failed();
        CompilationSubject.assertThat(compilation).hadErrorContaining(message);
    }

    @Test
    void foreignFieldOnAMemberIsAnError() {
        assertError(compile(getOrder("", ID, "@other.Field(order = 2) private String note;")),
            "GetOrder.note: other.Field is not pt.dazz.copymapper.annotation.Field."
                + " Change the import.");
    }

    @Test
    void foreignOccursOnAMemberIsAnError() {
        assertError(compile(getOrder("", ID,
                "@other.Occurs(order = 2) private List<String> lines;")),
            "GetOrder.lines: other.Occurs is not pt.dazz.copymapper.annotation.Occurs."
                + " Change the import.");
    }

    @Test
    void foreignGroupOnAMemberIsAnError() {
        assertError(compile(getOrder("", ID, "@other.Group(order = 2) private Object detail;")),
            "GetOrder.detail: other.Group is not pt.dazz.copymapper.annotation.Group."
                + " Change the import.");
    }

    @Test
    void memberWithBothAnnotationsIsValid() {
        CompilationSubject.assertThat(compile(getOrder("", ID,
            field("@Field(order = 2, pic = \"X(5)\") @other.Field(order = 2)", "String",
                "note")))).succeeded();
    }

    @Test
    void foreignFieldInAGroupClassIsAnError() {
        assertError(compile(
            getOrder("", ID, field("@Group(order = 2, redefines = \"id\")", "Detail", "detail")),
            source("Detail", "public class Detail {",
                field("@Field(order = 1, pic = \"X(4)\")", "String", "code"),
                field("@Field(order = 3, pic = \"X(6)\")", "String", "rest"),
                "@other.Field(order = 2) private String text;",
                "}")),
            "Detail.text: other.Field is not pt.dazz.copymapper.annotation.Field.");
    }

    @Test
    void foreignFieldInAnItemClassIsAnError() {
        assertError(compile(
            getOrder("", ID,
                field("@Occurs(order = 2, maxOccurs = 3, fixed = true)", "List<Line>", "lines")),
            source("Line", "public class Line {",
                field("@Field(order = 1, pic = \"X(4)\")", "String", "sku"),
                "@other.Field(order = 2) private String text;",
                "}")),
            "Line.text: other.Field is not pt.dazz.copymapper.annotation.Field.");
    }

    @Test
    void classUsedTwiceIsReportedOnce() {
        Compilation compilation = compile(
            getOrder("", ID,
                field("@Occurs(order = 2, maxOccurs = 3, fixed = true)", "List<Line>", "lines"),
                field("@Occurs(order = 3, maxOccurs = 2, fixed = true)", "List<Line>", "extras")),
            source("Line", "public class Line {",
                field("@Field(order = 1, pic = \"X(4)\")", "String", "sku"),
                "@other.Field(order = 2) private String text;",
                "}"));
        assertError(compilation, "Line.text: other.Field is not");
        CompilationSubject.assertThat(compilation).hadErrorCount(1);
    }

    @Test
    void foreignFieldInASuperclassIsAnError() {
        assertError(compile(
            source("GetOrder", "@Operation public class GetOrder extends BaseOrder {",
                field("@Field(order = 2, pic = \"X(5)\")", "String", "status"), "}"),
            source("BaseOrder", "public class BaseOrder {", ID,
                "@other.Field(order = 3) private String note;", "}")),
            "BaseOrder.note: other.Field is not pt.dazz.copymapper.annotation.Field.");
    }

    @Test
    void foreignFieldInASubtypeIsAnError() {
        assertError(compile(
            source("GetAccount", "@Operation @SubTypes(discriminator = \"kind\", value = {"
                    + "@SubTypes.Type(value = \"01\", type = Checking.class)})",
                "public class GetAccount {",
                field("@Field(order = 1, pic = \"X(2)\")", "String", "kind"), "}"),
            source("Checking", "public class Checking extends GetAccount {",
                field("@Field(order = 2, pic = \"X(5)\")", "String", "limit"),
                "@other.Field(order = 3) private String note;", "}")),
            "Checking.note: other.Field is not pt.dazz.copymapper.annotation.Field.");
    }

    @Test
    void foreignSubTypesOnAMappedClassIsAnError() {
        assertError(compile(getOrder("@other.SubTypes", ID)),
            "GetOrder: other.SubTypes is not pt.dazz.copymapper.annotation.SubTypes."
                + " Change the import.");
    }

    @Test
    void foreignDefaultsOnAMappedClassIsAnError() {
        assertError(compile(getOrder("@other.Defaults", ID)),
            "GetOrder: other.Defaults is not pt.dazz.copymapper.annotation.Defaults."
                + " Change the import.");
    }

    @Test
    void classWithBothDefaultsIsValid() {
        CompilationSubject.assertThat(compile(
            getOrder("@Defaults(charset = \"IBM037\") @other.Defaults", ID))).succeeded();
    }

    @Test
    void otherAnnotationsAreValid() {
        CompilationSubject.assertThat(compile(getOrder("@Deprecated", ID,
            "@other.Column private String note;"))).succeeded();
    }

    @Test
    void classThatIsNotMappedIsNotChecked() {
        CompilationSubject.assertThat(compile(getOrder("", ID),
            source("Helper", "@other.Defaults public class Helper {",
                "@other.Field(order = 1) private String text;", "}"))).succeeded();
    }
}
