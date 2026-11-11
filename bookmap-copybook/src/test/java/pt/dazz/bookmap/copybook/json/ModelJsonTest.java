package pt.dazz.bookmap.copybook.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.Golden;
import pt.dazz.bookmap.copybook.model.BookModel;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;

class ModelJsonTest {

    /** An account book with a fixed and a variable table, and two subtypes. */
    static BookModel accountModel() {
        ClassModel account = new ClassModel("GetAccount", ClassKind.OPERATION, null,
            new SubTypes("accountType", List.of(new SubTypeEntry("01", "CheckingData"),
                new SubTypeEntry("02", "SavingsAccount"))),
            List.of(
                new FieldMember("accountId", JavaType.STRING, 10, "X(12)",
                    Direction.INPUT_OUTPUT, "ACCOUNT-ID", "05 ACCOUNT-ID PIC X(12)."),
                new FieldMember("accountType", JavaType.INT, 20, "9(2)",
                    Direction.INPUT_OUTPUT, "ACCOUNT-TYPE", "05 ACCOUNT-TYPE PIC 9(2)."),
                new CollectionMember("holder", 30, Direction.INPUT_OUTPUT, "HOLDER",
                    "05 HOLDER OCCURS 2.", 2, true, null, "Holder")));
        ClassModel holder = new ClassModel("Holder", ClassKind.ITEM, null, null, List.of(
            new FieldMember("holderName", JavaType.STRING, 10, "X(30)", Direction.INPUT_OUTPUT,
                "HOLDER-NAME", "10 HOLDER-NAME PIC X(30).")));
        ClassModel checking = new ClassModel("CheckingData", ClassKind.SUBTYPE, "GetAccount",
            null, List.of(
                new FieldMember("overdraftLimit", JavaType.BIG_DECIMAL, 40, "S9(9)V99 COMP-3",
                    Direction.OUTPUT, "OVERDRAFT-LIMIT",
                    "10 OVERDRAFT-LIMIT PIC S9(9)V99 COMP-3."),
                new FieldMember("entryCount", JavaType.LONG, 50, "9(10)", Direction.OUTPUT,
                    "ENTRY-COUNT", "10 ENTRY-COUNT PIC 9(10)."),
                new CollectionMember("entry", 60, Direction.OUTPUT, "ENTRY",
                    "10 ENTRY OCCURS 0 TO 10 DEPENDING ON ENTRY-COUNT.", 10, false,
                    "entryCount", "Entry")));
        return new BookModel(BookModel.FORMAT_VERSION, "ACCOUNT.cpy",
            List.of(account, holder, checking));
    }

    @Test
    void writesTheGoldenFile() {
        Golden.assertMatches("json/model.json", ModelJson.write(accountModel()));
    }

    @Test
    void roundTripGivesAnEqualModel() {
        BookModel model = accountModel();
        assertThat(ModelJson.read(ModelJson.write(model))).isEqualTo(model);
    }

    @Test
    void writeIsDeterministic() {
        assertThat(ModelJson.write(accountModel())).isEqualTo(ModelJson.write(accountModel()));
    }

    @Test
    void rejectsUnknownVersion() {
        assertThatThrownBy(() -> ModelJson.read(
                "{\"formatVersion\": 2, \"sourceName\": \"A\", \"classes\": []}"))
            .isInstanceOf(ModelJsonException.class)
            .hasMessage("unsupported formatVersion 2. This reader supports 1.");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> ModelJson.read("{"))
            .isInstanceOf(ModelJsonException.class)
            .hasMessageStartingWith("the text is not valid JSON");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "[]|$ must be an object",
        "{\"formatVersion\": 1, \"sourceName\": \"A\", \"classes\": [], \"x\": 1}"
            + "|unknown key 'x' in $",
        "{\"formatVersion\": 1, \"classes\": []}|missing key 'sourceName' in $",
        "{\"formatVersion\": \"1\"}|key 'formatVersion' in $ must be an integer",
        "{\"formatVersion\": 1, \"sourceName\": 7, \"classes\": []}"
            + "|key 'sourceName' in $ must be a string",
        "{\"formatVersion\": 1, \"sourceName\": \"A\", \"classes\": {}}"
            + "|key 'classes' in $ must be an array",
        "{\"formatVersion\": 1, \"sourceName\": \"A\", \"classes\": [{\"name\": \"A\","
            + " \"kind\": \"TABLE\", \"members\": []}]}"
            + "|key 'kind' in $.classes[0] has the unknown value 'TABLE'",
        "{\"formatVersion\": 1, \"sourceName\": \"A\", \"classes\": [{\"name\": \"A\","
            + " \"kind\": \"ITEM\", \"members\": [{\"kind\": \"OTHER\"}]}]}"
            + "|key 'kind' in $.classes[0].members[0] must be FIELD or COLLECTION",
        "{\"formatVersion\": 1, \"sourceName\": \"A\", \"classes\": [{\"name\": \"A\","
            + " \"kind\": \"ITEM\", \"members\": [{\"name\": \"a\"}]}]}"
            + "|missing key 'kind' in $.classes[0].members[0]",
        "{\"formatVersion\": 1, \"sourceName\": \"A\", \"classes\": [{\"name\": \"A\","
            + " \"kind\": \"ITEM\", \"members\": [{\"kind\": \"COLLECTION\", \"name\": \"a\","
            + " \"order\": 1, \"direction\": \"OUTPUT\", \"maxOccurs\": 2, \"fixed\": 1,"
            + " \"itemClass\": \"B\", \"cobolName\": \"A\", \"declaration\": \"05 A.\"}]}]}"
            + "|key 'fixed' in $.classes[0].members[0] must be a boolean",
        "{\"formatVersion\": 1, \"sourceName\": \"A\", \"classes\": [{\"name\": \"A\","
            + " \"kind\": \"OPERATION\", \"subTypes\": {\"discriminator\": \"t\", \"types\":"
            + " [{\"value\": \"1\", \"class\": \"B\"}]}, \"members\": []}]}"
            + "|unknown key 'class' in $.classes[0].subTypes.types[0]",
    })
    void rejectsInvalidModels(String json, String message) {
        assertThatThrownBy(() -> ModelJson.read(json))
            .isInstanceOf(ModelJsonException.class).hasMessage(message);
    }
}
