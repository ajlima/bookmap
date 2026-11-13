package pt.dazz.bookmap.copybook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.json.ModelJsonException;
import pt.dazz.bookmap.copybook.map.MappingOptions;
import pt.dazz.bookmap.copybook.map.SubTypeDecision;
import pt.dazz.bookmap.copybook.parse.SourceFormat;

class GeneratorConfigJsonTest {

    private static final Path BASE = Path.of("config");

    private static final String EXAMPLE = """
        {
          "format": "fixed",
          "charset": "ISO-8859-1",
          "copyDirs": ["copylib"],
          "recordName": "ACCOUNT",
          "stripPrefixes": ["WS-"],
          "inputMarkers": ["INPUT", "IN", "REQUEST", "REQ", "ENTRADA"],
          "outputMarkers": ["OUTPUT", "OUT", "RESPONSE", "RESP", "SAIDA"],
          "directions": { "INVOICE-QUERY": "INPUT", "INVOICE-DETAIL": "OUTPUT" },
          "subTypes": {
            "CHECKING-DATA": {
              "discriminator": "ACCOUNT-TYPE",
              "values": { "CHECKING-DATA": "01", "SAVINGS-DATA": "02" },
              "classNames": { "SAVINGS-DATA": "SavingsAccount" }
            }
          },
          "dates": { "OPENED-ON": "yyyyMMdd", "*-TIME": "HHmmss" },
          "continuable": { "GET-ACCOUNT": "com.example.accounts.AccountPaging" },
          "validatable": { "GET-ACCOUNT": "ReturnCodes" },
          "package": "com.example.accounts",
          "lombok": false
        }
        """;

    @Test
    void readsAllKeys() {
        GeneratorConfig config = GeneratorConfigJson.read(EXAMPLE, BASE);
        assertThat(config.parse().format()).isEqualTo(SourceFormat.FIXED);
        assertThat(config.parse().charset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(config.parse().recordName()).isEqualTo("ACCOUNT");
        assertThat(config.mapping().stripPrefixes()).containsExactly("WS-");
        assertThat(config.mapping().inputMarkers()).contains("ENTRADA");
        assertThat(config.mapping().outputMarkers()).contains("SAIDA");
        assertThat(config.mapping().directions()).isEqualTo(Map.of("INVOICE-QUERY",
            Direction.INPUT, "INVOICE-DETAIL", Direction.OUTPUT));
        assertThat(config.mapping().subTypes()).isEqualTo(Map.of("CHECKING-DATA",
            new SubTypeDecision("ACCOUNT-TYPE", Map.of("CHECKING-DATA", "01", "SAVINGS-DATA",
                "02"), Map.of("SAVINGS-DATA", "SavingsAccount"))));
        assertThat(config.mapping().dates()).containsExactly(
            Map.entry("OPENED-ON", "yyyyMMdd"), Map.entry("*-TIME", "HHmmss"));
        assertThat(config.mapping().continuable()).isEqualTo(Map.of("GET-ACCOUNT",
            "com.example.accounts.AccountPaging"));
        assertThat(config.mapping().validatable()).isEqualTo(Map.of("GET-ACCOUNT",
            "ReturnCodes"));
        assertThat(config.packageName()).isEqualTo("com.example.accounts");
        assertThat(config.lombok()).isFalse();
    }

    @Test
    void markersReplaceDefaults() {
        GeneratorConfig config = GeneratorConfigJson.read(
            "{\"inputMarkers\": [\"ENTRADA\"], \"outputMarkers\": [\"SAIDA\"]}", BASE);
        assertThat(config.mapping().inputMarkers()).containsExactly("ENTRADA");
        assertThat(config.mapping().outputMarkers()).containsExactly("SAIDA");
    }

    @Test
    void copyDirsAreRelativeToTheFile() {
        assertThat(GeneratorConfigJson.read(EXAMPLE, BASE).parse().copyDirs())
            .containsExactly(Path.of("config", "copylib"));
    }

    @Test
    void missingKeysUseDefaults() {
        GeneratorConfig config = GeneratorConfigJson.read("{\"format\": \"FREE\"}", BASE);
        assertThat(config.parse().format()).isEqualTo(SourceFormat.FREE);
        assertThat(config.parse().charset()).isEqualTo(Charset.forName("UTF-8"));
        assertThat(config.parse().copyDirs()).isEmpty();
        assertThat(config.parse().recordName()).isNull();
        assertThat(config.mapping()).isEqualTo(MappingOptions.defaults());
        assertThat(config.packageName()).isNull();
        assertThat(config.lombok()).isNull();
        GeneratorConfig subTypes = GeneratorConfigJson.read("{\"subTypes\": {\"A\":"
            + " {\"discriminator\": \"T\", \"values\": {\"A\": \"1\"}}}}", BASE);
        assertThat(subTypes.mapping().subTypes().get("A").classNames()).isEmpty();
        assertThat(subTypes.parse().format()).isEqualTo(SourceFormat.FIXED);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{\"packages\": \"a\"}|unknown key 'packages' in $",
        "{\"lombok\": \"yes\"}|key 'lombok' in $ must be a boolean",
        "{\"format\": \"wide\"}|key 'format' in $ has the unknown value 'wide'",
        "{\"charset\": \"NO-SUCH-CHARSET\"}|key 'charset' in $ has the unknown charset"
            + " 'NO-SUCH-CHARSET'",
        "{\"charset\": \"bad name!\"}|key 'charset' in $ has the unknown charset 'bad name!'",
        "{\"directions\": {\"A\": \"BOTH\"}}|key 'A' in $.directions has the unknown direction"
            + " 'BOTH'. Use INPUT, OUTPUT, or INPUT_OUTPUT.",
        "{\"directions\": {\"A\": 1}}|key 'A' in $.directions must be a string",
        "{\"directions\": []}|key 'directions' in $ must be an object",
        "{\"dates\": {\"A\": 1}}|key 'A' in $.dates must be a string",
        "{\"subTypes\": {\"A\": {\"values\": {}}}}|missing key 'discriminator' in $.subTypes.A",
        "{\"subTypes\": {\"A\": {\"discriminator\": \"T\", \"values\": {}, \"x\": 1}}}"
            + "|unknown key 'x' in $.subTypes.A",
    })
    void rejectsInvalidConfigurations(String json, String message) {
        assertThatThrownBy(() -> GeneratorConfigJson.read(json, BASE))
            .isInstanceOf(ModelJsonException.class).hasMessage(message);
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> GeneratorConfigJson.read("{", BASE))
            .isInstanceOf(ModelJsonException.class)
            .hasMessageStartingWith("the text is not valid JSON");
    }

    @Test
    void markerListsArePlainLists() {
        assertThat(GeneratorConfigJson.read("{\"stripPrefixes\": []}", BASE).mapping()
            .stripPrefixes()).isEqualTo(List.of());
    }
}
