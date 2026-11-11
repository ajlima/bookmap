package pt.dazz.bookmap.copybook.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.copybook.Golden;
import pt.dazz.bookmap.copybook.Models;
import pt.dazz.bookmap.copybook.model.BookModel;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;

class ModelJsonTest {

    @Test
    void writesTheGoldenFile() {
        Golden.assertMatches("json/model.json", ModelJson.write(Models.accountModel()));
    }

    @Test
    void roundTripGivesAnEqualModel() {
        BookModel model = Models.accountModel();
        assertThat(ModelJson.read(ModelJson.write(model))).isEqualTo(model);
    }

    @Test
    void writeIsDeterministic() {
        assertThat(ModelJson.write(Models.accountModel())).isEqualTo(ModelJson.write(Models.accountModel()));
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
