package pt.dazz.bookmap.copybook.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.Golden;
import pt.dazz.bookmap.copybook.Models;
import pt.dazz.bookmap.copybook.model.BookModel;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.ConditionMember;
import pt.dazz.bookmap.copybook.model.EnumConstant;
import pt.dazz.bookmap.copybook.model.EnumModel;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;
import pt.dazz.bookmap.copybook.model.ValueRange;

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

    /** A model with an enum, an initial value, and level 88 constants. */
    private static BookModel conditionModel() {
        FieldMember role = new FieldMember("partyRole", JavaType.ENUM, 10, "X(8)",
            Direction.INPUT_OUTPUT, "PARTY-ROLE", "05 PARTY-ROLE PIC X(8).", "PartyRole",
            "SELLER", List.of());
        FieldMember status = new FieldMember("status", JavaType.INT, 20, "9(2)",
            Direction.INPUT_OUTPUT, "STATUS", "05 STATUS PIC 9(2).", null, "1",
            List.of(new ConditionMember("ACTIVE", "ACTIVE", "isActive",
                List.of(new ValueRange("1", "5"), new ValueRange("9", null)))));
        return new BookModel(BookModel.FORMAT_VERSION, "PARTY.cpy",
            List.of(new ClassModel("Party", ClassKind.OPERATION, null, null,
                List.of(role, status))),
            List.of(new EnumModel("PartyRole", "PARTY-ROLE", List.of(
                new EnumConstant("SELLER", "PARTY-IS-SELLER",
                    List.of(new ValueRange("SELLER", null))),
                new EnumConstant("OTHER", "PARTY-IS-OTHER",
                    List.of(new ValueRange("A", "C")))))));
    }

    @Test
    void conditionModelRoundTrip() {
        String json = ModelJson.write(conditionModel());
        assertThat(json).contains("\"enumType\" : \"PartyRole\"",
            "\"initialValue\" : \"1\"", "\"thru\" : \"5\"", "\"enums\" : [");
        assertThat(ModelJson.read(json)).isEqualTo(conditionModel());
        assertThat(ModelJson.write(Models.accountModel())).doesNotContain("enums",
            "initialValue", "conditions", "enumType");
    }

    @Test
    void emptyItemMarkersRoundTrip() {
        BookModel model = Models.markerModel();
        String json = ModelJson.write(model);
        assertThat(json).contains("\"emptyItem\" : \"SPACES\"",
            "\"emptyKey\" : \"invoiceNumber\"");
        assertThat(ModelJson.read(json)).isEqualTo(model);
        assertThat(ModelJson.write(Models.accountModel())).doesNotContain("emptyItem",
            "emptyKey");
    }

    @Test
    void enumTypeAgreesWithJavaType() {
        String json = ModelJson.write(conditionModel());
        assertThatThrownBy(() -> ModelJson.read(json.replace("\"javaType\" : \"ENUM\"",
                "\"javaType\" : \"STRING\"")))
            .isInstanceOf(ModelJsonException.class)
            .hasMessage("key 'enumType' in $.classes[0].members[0] must be present only with"
                + " javaType ENUM");
        assertThatThrownBy(() -> ModelJson.read(json.replace("\"enumType\" : \"PartyRole\",",
                "")))
            .isInstanceOf(ModelJsonException.class)
            .hasMessage("key 'enumType' in $.classes[0].members[0] must be present only with"
                + " javaType ENUM");
        assertThatThrownBy(() -> ModelJson.read(json.replace("\"thru\" : \"5\"",
                "\"to\" : \"5\"")))
            .isInstanceOf(ModelJsonException.class)
            .hasMessage("unknown key 'to' in $.classes[0].members[1].conditions[0].values[0]");
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
