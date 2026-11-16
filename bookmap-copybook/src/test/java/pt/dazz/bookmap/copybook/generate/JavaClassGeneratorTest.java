package pt.dazz.bookmap.copybook.generate;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javapoet.JavaFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import pt.dazz.bookmap.annotation.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.dazz.bookmap.copybook.Golden;
import pt.dazz.bookmap.copybook.Models;
import pt.dazz.bookmap.copybook.model.BookModel;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.GroupMember;
import pt.dazz.bookmap.copybook.model.JavaType;

class JavaClassGeneratorTest {

    private static final GenerateOptions PLAIN = new GenerateOptions("com.example.accounts",
        false);
    private static final GenerateOptions LOMBOK = new GenerateOptions("com.example.accounts",
        true);

    private static void assertGolden(String directory, List<JavaFile> files) {
        assertThat(files).extracting(file -> file.typeSpec().name())
            .containsExactly("GetAccount", "Holder", "CheckingData");
        // Write all actual files before the first assertion, for a review of all of them.
        files.forEach(file -> Golden.writeActual(golden(directory, file), file.toString()));
        for (JavaFile file : files) {
            Golden.assertMatches(golden(directory, file), file.toString());
        }
    }

    private static String golden(String directory, JavaFile file) {
        return "golden/" + directory + "/" + file.typeSpec().name() + ".java";
    }

    @Test
    void plainOutputMatchesGolden() {
        assertGolden("plain", JavaClassGenerator.generate(Models.accountModel(), PLAIN));
    }

    @Test
    void lombokOutputMatchesGolden() {
        assertGolden("lombok", JavaClassGenerator.generate(Models.accountModel(), LOMBOK));
    }

    @Test
    void outputIsDeterministic() {
        assertThat(JavaClassGenerator.generate(Models.accountModel(), PLAIN).toString())
            .isEqualTo(JavaClassGenerator.generate(Models.accountModel(), PLAIN).toString());
    }

    @Test
    void initialValueOfADateFieldIsAnIsoText() {
        FieldMember shipDate = new FieldMember("shipDate", JavaType.LOCAL_DATE, 10, "9(8)",
            Direction.INPUT_OUTPUT, "SHIP-DATE", "05 SHIP-DATE PIC 9(8).", "yyyyMMdd", null,
            "2026-01-01", List.of());
        BookModel model = new BookModel(BookModel.FORMAT_VERSION, "SHIPMENT.cpy", List.of(
            new ClassModel("Shipment", ClassKind.OPERATION, null, null, List.of(shipDate))));
        assertThat(JavaClassGenerator.generate(model, PLAIN).get(0).toString())
            .contains("private LocalDate shipDate = LocalDate.parse(\"2026-01-01\");");
    }

    @Test
    void viewAreaGetsRedefinesAndBoxedNumbers() {
        List<JavaFile> files = JavaClassGenerator.generate(Models.viewModel(), PLAIN);
        String operation = files.get(0).toString();
        assertThat(operation).contains("direction = Direction.OUTPUT", "private String code;",
            "redefines = \"code\"", "private Integer number;", "private Long total;",
            "@BookGroup(", "private Detail detail;", "private List<Parts> parts;",
            "public static final Integer ZERO_NUM = 0;",
            "return number != null && (number == 0);",
            "return total != null && (total.compareTo(1L) >= 0 && total.compareTo(9L) <= 0);")
            .containsOnlyOnce("direction = Direction.OUTPUT");
        // The two fields, the collection, and the group of the area are views.
        assertThat(operation.split("redefines = \"code\"", -1)).hasSize(5);
        assertThat(files.get(2).toString()).contains("public class Detail")
            .doesNotContain("direction");
    }

    @Test
    void redefinedGroupGetsItsDirection() {
        FieldMember text = new FieldMember("text", JavaType.STRING, 10, "X(4)",
            Direction.OUTPUT, "TEXT", "10 TEXT PIC X(4).");
        BookModel model = new BookModel(BookModel.FORMAT_VERSION, "GET-CODE.cpy", List.of(
            new ClassModel("GetCode", ClassKind.OPERATION, null, null, List.of(
                new GroupMember("first", 10, Direction.OUTPUT, "FIRST", "05 FIRST.", "Detail",
                    ""),
                new GroupMember("second", 20, Direction.OUTPUT, "SECOND",
                    "05 SECOND REDEFINES FIRST.", "Detail", "first"),
                new GroupMember("third", 30, Direction.INPUT_OUTPUT, "THIRD", "05 THIRD.",
                    "Detail", ""),
                new GroupMember("fourth", 40, Direction.INPUT_OUTPUT, "FOURTH",
                    "05 FOURTH REDEFINES THIRD.", "Detail", "third"),
                new CollectionMember("entries", 50, Direction.OUTPUT, "ENTRIES",
                    "05 ENTRIES OCCURS 2.", 2, true, null, "Entries"))),
            new ClassModel("Entries", ClassKind.ITEM, null, null, List.of(
                new GroupMember("inner", 10, Direction.OUTPUT, "INNER", "10 INNER.", "Detail",
                    ""))),
            new ClassModel("Detail", ClassKind.GROUP, null, null, List.of(text))));
        List<JavaFile> files = JavaClassGenerator.generate(model, PLAIN);
        assertThat(files.get(0).toString()).containsOnlyOnce("direction = Direction.OUTPUT\n"
            + "    )\n    private Detail first;");
        assertThat(files.get(1).toString()).doesNotContain("direction");
    }

    @Test
    void collectionGetsTheEmptyItemMarkers() {
        assertThat(JavaClassGenerator.generate(
            Models.markerModel(), PLAIN).get(0).toString())
            .contains("fixed = true,", "emptyKey = \"invoiceNumber\",",
                "emptyItem = EmptyItem.SPACES");
    }

    @Test
    void collectionGetsThePartialAttribute() {
        assertThat(JavaClassGenerator.generate(Models.partialModel(), PLAIN).get(0).toString())
            .contains("fixed = true,", "partial = true");
        assertThat(JavaClassGenerator.generate(Models.markerModel(), PLAIN).get(0).toString())
            .doesNotContain("partial");
    }

    @Test
    void writeToCreatesPackageDirectoriesAndOverwrites(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("com/example/accounts/Holder.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "old");
        JavaClassGenerator.writeTo(JavaClassGenerator.generate(Models.accountModel(), PLAIN),
            dir);
        assertThat(Files.readString(file)).contains("public class Holder");
        assertThat(dir.resolve("com/example/accounts/GetAccount.java")).exists();
    }

    @Test
    void aDeclarationThatEndsACommentUsesHtml() {
        assertThat(JavaClassGenerator.declaration("05 A PIC ZZ9.")).hasToString(
            "{@code 05 A PIC ZZ9.}\n");
        assertThat(JavaClassGenerator.declaration("05 A<B> & @C PIC **/**/99.")).hasToString(
            "<code>05 A&lt;B&gt; &amp; &#64;C PIC **&#47;**&#47;99.</code>\n");
    }
}
