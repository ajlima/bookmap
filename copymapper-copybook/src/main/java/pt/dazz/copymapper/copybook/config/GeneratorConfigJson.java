package pt.dazz.copymapper.copybook.config;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pt.dazz.copymapper.annotation.Direction;
import pt.dazz.copymapper.copybook.json.JsonObject;
import pt.dazz.copymapper.copybook.json.ModelJsonException;
import pt.dazz.copymapper.copybook.map.ConditionMode;
import pt.dazz.copymapper.copybook.map.MappingOptions;
import pt.dazz.copymapper.copybook.map.SubTypeDecision;
import pt.dazz.copymapper.copybook.parse.ParseOptions;
import pt.dazz.copymapper.copybook.parse.SourceFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a generator configuration file (JSON).
 */
public final class GeneratorConfigJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> KEYS = Set.of("format", "charset", "copyDirs",
        "recordName", "stripPrefixes", "inputMarkers", "outputMarkers", "directions",
        "subTypes", "conditions", "countFields", "maxRecordLength", "dates", "continuable",
        "validatable", "emptyItems", "emptyKeys", "partialTables", "views", "package",
        "lombok");
    private static final Set<String> SUBTYPE_KEYS = Set.of("discriminator", "values",
        "classNames");

    private GeneratorConfigJson() {
    }

    /**
     * Reads a configuration. Keys that are not in the text get the default values.
     *
     * @param json the JSON text
     * @param baseDir the directory for relative copy directories, usually the directory of
     *     the configuration file
     * @return the configuration
     * @throws ModelJsonException if the text is not a valid configuration
     */
    public static GeneratorConfig read(String json, Path baseDir) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new ModelJsonException("the text is not valid JSON: "
                + e.getOriginalMessage());
        }
        JsonObject root = JsonObject.of(tree, "$", KEYS);
        ParseOptions defaults = ParseOptions.defaults();
        ParseOptions parse = new ParseOptions(
            root.has("format") ? root.enumValue("format", SourceFormat.class)
                : defaults.format(),
            root.has("charset") ? charset(root) : defaults.charset(),
            root.has("copyDirs") ? root.texts("copyDirs").stream().map(baseDir::resolve)
                .toList() : List.of(),
            root.optionalText("recordName"));
        MappingOptions mapping = new MappingOptions(
            root.has("stripPrefixes") ? root.texts("stripPrefixes") : List.of(),
            root.has("inputMarkers") ? root.texts("inputMarkers")
                : MappingOptions.INPUT_MARKERS,
            root.has("outputMarkers") ? root.texts("outputMarkers")
                : MappingOptions.OUTPUT_MARKERS,
            root.has("directions") ? directions(root) : Map.of(),
            root.has("subTypes") ? subTypes(root) : Map.of(),
            root.has("countFields") ? root.textMap("countFields") : Map.of(),
            root.has("maxRecordLength") ? maxRecordLength(root) : null,
            root.has("dates") ? root.textMap("dates") : Map.of(),
            root.has("continuable") ? root.textMap("continuable") : Map.of(),
            root.has("validatable") ? root.textMap("validatable") : Map.of(),
            root.has("conditions") ? conditions(root) : Map.of(),
            root.has("emptyItems") ? root.textMap("emptyItems") : Map.of(),
            root.has("emptyKeys") ? root.textMap("emptyKeys") : Map.of(),
            root.has("partialTables") ? root.texts("partialTables") : List.of(),
            root.has("views") ? root.texts("views") : List.of());
        return new GeneratorConfig(parse, mapping, root.optionalText("package"),
            root.has("lombok") ? root.bool("lombok") : null);
    }

    private static Charset charset(JsonObject root) {
        String name = root.text("charset");
        try {
            return Charset.forName(name);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new ModelJsonException("key 'charset' in $ has the unknown charset '" + name
                + "'");
        }
    }

    private static int maxRecordLength(JsonObject root) {
        int value = root.integer("maxRecordLength");
        if (value < 1) {
            throw new ModelJsonException("key 'maxRecordLength' in $ must be 1 or more, not "
                + value);
        }
        return value;
    }

    private static Map<String, Direction> directions(JsonObject root) {
        Map<String, Direction> directions = new LinkedHashMap<>();
        root.textMap("directions").forEach((name, value) -> {
            try {
                directions.put(name, Direction.valueOf(value));
            } catch (IllegalArgumentException e) {
                throw new ModelJsonException("key '" + name + "' in $.directions has the"
                    + " unknown direction '" + value + "'. Use INPUT, OUTPUT, or INPUT_OUTPUT.");
            }
        });
        return directions;
    }

    private static Map<String, ConditionMode> conditions(JsonObject root) {
        Map<String, ConditionMode> modes = new LinkedHashMap<>();
        root.textMap("conditions").forEach((name, value) -> {
            try {
                modes.put(name, ConditionMode.of(value));
            } catch (IllegalArgumentException e) {
                throw new ModelJsonException("key '" + name + "' in $.conditions has the "
                    + e.getMessage());
            }
        });
        return modes;
    }

    private static Map<String, SubTypeDecision> subTypes(JsonObject root) {
        Map<String, SubTypeDecision> decisions = new LinkedHashMap<>();
        root.nodeMap("subTypes").forEach((name, node) -> {
            JsonObject decision = JsonObject.of(node, root.path("subTypes") + "." + name,
                SUBTYPE_KEYS);
            decisions.put(name, new SubTypeDecision(decision.text("discriminator"),
                decision.textMap("values"),
                decision.has("classNames") ? decision.textMap("classNames") : Map.of()));
        });
        return decisions;
    }
}
