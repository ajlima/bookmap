package pt.dazz.bookmap.copybook.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonObjectTest {

    private static JsonObject object(String json) throws Exception {
        return JsonObject.of(new ObjectMapper().readTree(json), "$", Set.of("names"));
    }

    @Test
    void textsGivesTheStrings() throws Exception {
        assertThat(object("{\"names\": [\"WS-\", \"LK-\"]}").texts("names"))
            .containsExactly("WS-", "LK-");
    }

    @Test
    void textsRejectsOtherValues() throws Exception {
        JsonObject object = object("{\"names\": [\"WS-\", 1]}");
        assertThatThrownBy(() -> object.texts("names"))
            .isInstanceOf(ModelJsonException.class)
            .hasMessage("key 'names' in $ must be an array of strings");
    }
}
