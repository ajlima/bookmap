package pt.dazz.bookmap.copybook.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the keys of one JSON object with strict checks. Each message gives the JSON path.
 */
public final class JsonObject {

    private final JsonNode node;
    private final String path;

    private JsonObject(JsonNode node, String path) {
        this.node = node;
        this.path = path;
    }

    /**
     * Wraps a node that must be an object.
     *
     * @param node the node
     * @param path the JSON path of the node, for messages
     * @param keys the permitted keys
     * @return the object
     * @throws ModelJsonException if the node is not an object or has an unknown key
     */
    public static JsonObject of(JsonNode node, String path, Set<String> keys) {
        if (!node.isObject()) {
            throw new ModelJsonException(path + " must be an object");
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!keys.contains(name)) {
                throw new ModelJsonException("unknown key '" + name + "' in " + path);
            }
        }
        return new JsonObject(node, path);
    }

    /**
     * Tells if a key exists.
     *
     * @param key the key
     * @return {@code true} if the key exists
     */
    public boolean has(String key) {
        return node.has(key);
    }

    /**
     * Gives the JSON path of a key.
     *
     * @param key the key
     * @return the path
     */
    public String path(String key) {
        return path + "." + key;
    }

    /**
     * Gives a mandatory node.
     *
     * @param key the key
     * @return the node
     */
    public JsonNode node(String key) {
        if (!node.has(key)) {
            throw new ModelJsonException("missing key '" + key + "' in " + path);
        }
        return node.get(key);
    }

    /**
     * Gives a mandatory text value.
     *
     * @param key the key
     * @return the text
     */
    public String text(String key) {
        JsonNode value = node(key);
        if (!value.isTextual()) {
            throw wrongType(key, "a string");
        }
        return value.asText();
    }

    /**
     * Gives an optional text value.
     *
     * @param key the key
     * @return the text, or {@code null}
     */
    public String optionalText(String key) {
        return has(key) ? text(key) : null;
    }

    /**
     * Gives a mandatory integer value.
     *
     * @param key the key
     * @return the integer
     */
    public int integer(String key) {
        JsonNode value = node(key);
        if (!value.isInt()) {
            throw wrongType(key, "an integer");
        }
        return value.asInt();
    }

    /**
     * Gives a mandatory boolean value.
     *
     * @param key the key
     * @return the boolean
     */
    public boolean bool(String key) {
        JsonNode value = node(key);
        if (!value.isBoolean()) {
            throw wrongType(key, "a boolean");
        }
        return value.asBoolean();
    }

    /**
     * Gives a mandatory enum value.
     *
     * @param key the key
     * @param type the enum class
     * @param <E> the enum type
     * @return the constant
     */
    public <E extends Enum<E>> E enumValue(String key, Class<E> type) {
        String text = text(key);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(text.toUpperCase(Locale.ROOT))) {
                return constant;
            }
        }
        throw new ModelJsonException("key '" + key + "' in " + path + " has the unknown value '"
            + text + "'");
    }

    /**
     * Gives the elements of a mandatory array.
     *
     * @param key the key
     * @return the elements
     */
    public List<JsonNode> array(String key) {
        JsonNode value = node(key);
        if (!value.isArray()) {
            throw wrongType(key, "an array");
        }
        List<JsonNode> elements = new ArrayList<>();
        value.forEach(elements::add);
        return elements;
    }

    /**
     * Gives the text elements of a mandatory array.
     *
     * @param key the key
     * @return the texts
     */
    public List<String> texts(String key) {
        List<String> texts = new ArrayList<>();
        for (JsonNode element : array(key)) {
            if (!element.isTextual()) {
                throw wrongType(key, "an array of strings");
            }
            texts.add(element.asText());
        }
        return texts;
    }

    /**
     * Gives a mandatory object as a map of nodes, in key order.
     *
     * @param key the key
     * @return the entries
     */
    public Map<String, JsonNode> nodeMap(String key) {
        JsonNode value = node(key);
        if (!value.isObject()) {
            throw wrongType(key, "an object");
        }
        Map<String, JsonNode> entries = new LinkedHashMap<>();
        value.fields().forEachRemaining(entry -> entries.put(entry.getKey(), entry.getValue()));
        return entries;
    }

    /**
     * Gives a mandatory object of text values as a map, in key order.
     *
     * @param key the key
     * @return the entries
     */
    public Map<String, String> textMap(String key) {
        Map<String, String> texts = new LinkedHashMap<>();
        nodeMap(key).forEach((name, value) -> {
            if (!value.isTextual()) {
                throw new ModelJsonException("key '" + name + "' in " + path(key)
                    + " must be a string");
            }
            texts.put(name, value.asText());
        });
        return texts;
    }

    private ModelJsonException wrongType(String key, String type) {
        return new ModelJsonException("key '" + key + "' in " + path + " must be " + type);
    }
}
