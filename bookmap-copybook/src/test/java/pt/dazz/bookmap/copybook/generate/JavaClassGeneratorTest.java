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
import pt.dazz.bookmap.copybook.model.FieldMember;
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
    void collectionGetsTheEmptyItemMarkers() {
        assertThat(JavaClassGenerator.generate(
            Models.markerModel(), PLAIN).get(0).toString())
            .contains("fixed = true,", "emptyKey = \"invoiceNumber\",",
                "emptyItem = EmptyItem.SPACES");
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
}
