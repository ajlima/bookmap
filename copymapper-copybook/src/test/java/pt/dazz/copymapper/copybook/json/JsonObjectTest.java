package pt.dazz.copymapper.copybook.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonObjectTest {

    private static JsonObject object(String json) {
        return JsonObject.of(JsonMapper.builder().build().readTree(json), "$", Set.of("names"));
    }

    @Test
    void textsGivesTheStrings() {
        assertThat(object("{\"names\": [\"WS-\", \"LK-\"]}").texts("names"))
            .containsExactly("WS-", "LK-");
    }

    @Test
    void textsRejectsOtherValues() {
        JsonObject object = object("{\"names\": [\"WS-\", 1]}");
        assertThatThrownBy(() -> object.texts("names"))
            .isInstanceOf(ModelJsonException.class)
            .hasMessage("key 'names' in $ must be an array of strings");
    }
}
