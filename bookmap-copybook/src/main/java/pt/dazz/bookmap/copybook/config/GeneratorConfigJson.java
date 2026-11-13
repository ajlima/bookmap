package pt.dazz.bookmap.copybook.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.json.JsonObject;
import pt.dazz.bookmap.copybook.json.ModelJsonException;
import pt.dazz.bookmap.copybook.map.ConditionMode;
import pt.dazz.bookmap.copybook.map.MappingOptions;
import pt.dazz.bookmap.copybook.map.SubTypeDecision;
import pt.dazz.bookmap.copybook.parse.ParseOptions;
import pt.dazz.bookmap.copybook.parse.SourceFormat;

/**
 * Reads a generator configuration file (JSON).
 */
public final class GeneratorConfigJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> KEYS = Set.of("format", "charset", "copyDirs",
        "recordName", "stripPrefixes", "inputMarkers", "outputMarkers", "directions",
        "subTypes", "conditions", "package", "lombok");
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
        } catch (JsonProcessingException e) {
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
            root.has("conditions") ? conditions(root) : Map.of());
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
