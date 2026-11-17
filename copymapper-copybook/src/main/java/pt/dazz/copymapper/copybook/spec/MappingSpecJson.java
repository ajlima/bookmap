package pt.dazz.copymapper.copybook.spec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.ast.Usage;
import pt.dazz.copymapper.copybook.json.JsonObject;
import pt.dazz.copymapper.copybook.json.ModelJsonException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes and reads the canonical JSON form of a {@link MappingSpec}, and hashes it. The form is
 * compact and has a fixed key order, so the same spec always gives the same text. A null, a
 * false flag, and an empty list are not written.
 */
public final class MappingSpecJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> SPEC_KEYS = Set.of("formatVersion", "sourceName",
            "sourceHash", "charset", "byteOrder", "root");
    private static final Set<String> NODE_KEYS = Set.of("cobolName", "level", "path", "picture",
            "usage", "sign", "occurs", "redefines", "value", "conditions", "location",
            "blankWhenZero", "edited", "jsonName", "direction", "jsonType", "format", "table",
            "counter", "variant", "javaName", "children");
    private static final Set<String> OCCURS_KEYS = Set.of("min", "max", "dependingOn");
    private static final Set<String> VALUE_KEYS = Set.of("kind", "text");
    private static final Set<String> CONDITION_KEYS = Set.of("name", "values");
    private static final Set<String> RANGE_KEYS = Set.of("from", "thru");
    private static final Set<String> LOCATION_KEYS = Set.of("source", "line", "column");
    private static final Set<String> TABLE_KEYS = Set.of("mode", "path");
    private static final Set<String> DISCRIMINATOR_KEYS = Set.of("discriminator", "values");
    private static final Set<String> FIXED_KEYS = Set.of("request", "response");

    private MappingSpecJson() {
    }

    /**
     * Writes a spec in the canonical form: compact, with no final new line.
     *
     * @param spec the spec
     * @return the JSON text
     */
    public static String write(MappingSpec spec) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("formatVersion", spec.formatVersion());
        root.put("sourceName", spec.sourceName());
        putIf(root, "sourceHash", spec.sourceHash());
        root.put("charset", spec.charset());
        root.put("byteOrder", spec.byteOrder());
        root.set("root", writeNode(spec.root()));
        return MAPPER.writeValueAsString(root);
    }

    /**
     * Computes the hash of a spec.
     *
     * @param spec the spec
     * @return the SHA-256 of the canonical text in UTF-8, as 64 lower-case hexadecimal digits
     */
    public static String sha256(MappingSpec spec) {
        byte[] digest = digest("SHA-256").digest(write(spec).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    /**
     * Gives a message digest. Each Java platform must support SHA-256, so the failure cannot
     * occur for that algorithm.
     *
     * @param algorithm the algorithm name
     * @return the message digest
     * @throws IllegalStateException if the algorithm is not available
     */
    static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        }
    }

    private static void putIf(ObjectNode node, String key, String value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    private static ObjectNode writeNode(SpecNode node) {
        ObjectNode out = MAPPER.createObjectNode();
        out.put("cobolName", node.cobolName());
        out.put("level", node.level());
        putIf(out, "path", node.path());
        putIf(out, "picture", node.picture());
        putIf(out, "usage", node.usage() == null ? null : node.usage().name());
        putIf(out, "sign", node.sign() == null ? null : node.sign().name());
        if (node.occurs() != null) {
            ObjectNode occurs = out.putObject("occurs");
            occurs.put("min", node.occurs().min());
            occurs.put("max", node.occurs().max());
            putIf(occurs, "dependingOn", node.occurs().dependingOn());
        }
        putIf(out, "redefines", node.redefines());
        if (node.value() != null) {
            out.set("value", writeValue(node.value()));
        }
        writeConditions(out, node.conditions());
        if (node.location() != null) {
            out.putObject("location").put("source", node.location().source())
                    .put("line", node.location().line())
                    .put("column", node.location().column());
        }
        putTrue(out, "blankWhenZero", node.blankWhenZero());
        putTrue(out, "edited", node.edited());
        putIf(out, "jsonName", node.jsonName());
        putIf(out, "direction", node.direction() == null ? null : node.direction().name());
        putIf(out, "jsonType", node.jsonType() == null ? null : node.jsonType().name());
        putIf(out, "format", node.format());
        if (node.table() != null) {
            ObjectNode table = out.putObject("table");
            table.put("mode", node.table().mode().name());
            putIf(table, "path", node.table().path());
        }
        putIf(out, "counter", node.counter());
        if (node.variant() != null) {
            out.set("variant", writeVariant(node.variant()));
        }
        putIf(out, "javaName", node.javaName());
        if (!node.children().isEmpty()) {
            ArrayNode children = out.putArray("children");
            node.children().forEach(child -> children.add(writeNode(child)));
        }
        return out;
    }

    private static void putTrue(ObjectNode node, String key, boolean value) {
        if (value) {
            node.put(key, true);
        }
    }

    private static ObjectNode writeValue(ValueSpec value) {
        return MAPPER.createObjectNode().put("kind", value.kind()).put("text", value.text());
    }

    private static void writeConditions(ObjectNode out, List<ConditionSpec> conditions) {
        if (conditions.isEmpty()) {
            return;
        }
        ArrayNode array = out.putArray("conditions");
        for (ConditionSpec condition : conditions) {
            ObjectNode item = array.addObject();
            item.put("name", condition.name());
            ArrayNode values = item.putArray("values");
            for (ConditionRange range : condition.values()) {
                ObjectNode entry = values.addObject();
                entry.set("from", writeValue(range.from()));
                if (range.thru() != null) {
                    entry.set("thru", writeValue(range.thru()));
                }
            }
        }
    }

    private static ObjectNode writeVariant(VariantSpec variant) {
        ObjectNode out = MAPPER.createObjectNode();
        switch (variant) {
            case VariantSpec.Discriminator d -> {
                out.put("discriminator", d.path());
                ObjectNode values = out.putObject("values");
                d.values().forEach(values::put);
            }
            case VariantSpec.FixedSelection f -> {
                putIf(out, "request", f.request());
                putIf(out, "response", f.response());
            }
        }
        return out;
    }

    /**
     * Reads a spec from its JSON text.
     *
     * @param json the JSON text
     * @return the spec
     * @throws MappingSpecFormatException if the text is not valid
     */
    public static MappingSpec read(String json) {
        try {
            return readSpec(json);
        } catch (ModelJsonException | IllegalArgumentException e) {
            throw new MappingSpecFormatException(e.getMessage());
        }
    }

    private static MappingSpec readSpec(String json) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new MappingSpecFormatException("the text is not valid JSON: "
                    + e.getOriginalMessage());
        }
        JsonObject root = object(tree, "$", SPEC_KEYS);
        int version = root.integer("formatVersion");
        if (version != MappingSpec.FORMAT_VERSION) {
            throw new MappingSpecFormatException("unsupported formatVersion " + version
                    + ". This reader supports " + MappingSpec.FORMAT_VERSION + ".");
        }
        String byteOrder = root.text("byteOrder");
        if (!MappingSpec.BIG_ENDIAN.equals(byteOrder)) {
            throw new MappingSpecFormatException("unsupported byteOrder '" + byteOrder
                    + "' in " + root.path("byteOrder") + ". This reader supports "
                    + MappingSpec.BIG_ENDIAN + ".");
        }
        String charset = root.text("charset");
        String sourceName = root.text("sourceName");
        String sourceHash = root.optionalText("sourceHash");
        SpecNode node = readNode(root.node("root"), root.path("root"));
        try {
            return new MappingSpec(version, sourceName, sourceHash, charset, byteOrder, node);
        } catch (IllegalArgumentException e) {
            throw new MappingSpecFormatException(e.getMessage() + " in " + root.path("charset"));
        }
    }

    /** Wraps an object and rejects a null value, naming its path. */
    private static JsonObject object(JsonNode node, String path, Set<String> keys) {
        JsonObject object = JsonObject.of(node, path, keys);
        for (String key : node.propertyNames()) {
            if (node.get(key).isNull()) {
                throw new MappingSpecFormatException(object.path(key) + " must not be null");
            }
        }
        return object;
    }

    private static SpecNode readNode(JsonNode tree, String path) {
        JsonObject o = object(tree, path, NODE_KEYS);
        return new SpecNode(o.text("cobolName"), o.integer("level"), o.text("path"),
                o.optionalText("picture"), enumOrNull(o, "usage", Usage.class),
                enumOrNull(o, "sign", Sign.class), readOccurs(o), o.optionalText("redefines"),
                o.has("value") ? readValue(o.node("value"), o.path("value")) : null,
                readConditions(o), readLocation(o), flag(o, "blankWhenZero"), flag(o, "edited"),
                o.optionalText("jsonName"), enumOrNull(o, "direction", FieldDirection.class),
                enumOrNull(o, "jsonType", JsonType.class), o.optionalText("format"),
                readTable(o), o.optionalText("counter"), readVariant(o),
                o.optionalText("javaName"), readChildren(o));
    }

    private static boolean flag(JsonObject o, String key) {
        return o.has(key) && o.bool(key);
    }

    private static <E extends Enum<E>> E enumOrNull(JsonObject o, String key, Class<E> type) {
        if (!o.has(key)) {
            return null;
        }
        String text = o.text(key);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(text)) {
                return constant;
            }
        }
        throw new MappingSpecFormatException(o.path(key) + " has the unknown value '" + text
                + "'");
    }

    private static OccursSpec readOccurs(JsonObject o) {
        if (!o.has("occurs")) {
            return null;
        }
        JsonObject occurs = object(o.node("occurs"), o.path("occurs"), OCCURS_KEYS);
        return new OccursSpec(occurs.integer("min"), occurs.integer("max"),
                occurs.optionalText("dependingOn"));
    }

    private static ValueSpec readValue(JsonNode tree, String path) {
        JsonObject value = object(tree, path, VALUE_KEYS);
        return new ValueSpec(value.text("kind"), value.text("text"));
    }

    private static List<ConditionSpec> readConditions(JsonObject o) {
        List<ConditionSpec> conditions = new ArrayList<>();
        if (!o.has("conditions")) {
            return conditions;
        }
        List<JsonNode> items = o.array("conditions");
        for (int i = 0; i < items.size(); i++) {
            String path = o.path("conditions") + "[" + i + "]";
            JsonObject item = object(items.get(i), path, CONDITION_KEYS);
            conditions.add(new ConditionSpec(item.text("name"), readRanges(item)));
        }
        return conditions;
    }

    private static List<ConditionRange> readRanges(JsonObject item) {
        List<ConditionRange> ranges = new ArrayList<>();
        List<JsonNode> entries = item.array("values");
        for (int i = 0; i < entries.size(); i++) {
            String path = item.path("values") + "[" + i + "]";
            JsonObject entry = object(entries.get(i), path, RANGE_KEYS);
            ValueSpec thru = entry.has("thru")
                    ? readValue(entry.node("thru"), entry.path("thru")) : null;
            ranges.add(new ConditionRange(
                    readValue(entry.node("from"), entry.path("from")), thru));
        }
        return ranges;
    }

    private static SourceLocation readLocation(JsonObject o) {
        if (!o.has("location")) {
            return null;
        }
        JsonObject location = object(o.node("location"), o.path("location"), LOCATION_KEYS);
        return new SourceLocation(location.text("source"), location.integer("line"),
                location.integer("column"));
    }

    private static TableSpec readTable(JsonObject o) {
        if (!o.has("table")) {
            return null;
        }
        JsonObject table = object(o.node("table"), o.path("table"), TABLE_KEYS);
        table.node("mode");
        try {
            return new TableSpec(enumOrNull(table, "mode", TableMode.class),
                    table.optionalText("path"));
        } catch (IllegalArgumentException e) {
            throw new MappingSpecFormatException(o.path("table") + ": " + e.getMessage());
        }
    }

    private static VariantSpec readVariant(JsonObject o) {
        if (!o.has("variant")) {
            return null;
        }
        JsonNode tree = o.node("variant");
        String path = o.path("variant");
        if (tree.has("discriminator")) {
            JsonObject d = object(tree, path, DISCRIMINATOR_KEYS);
            return new VariantSpec.Discriminator(d.text("discriminator"), d.textMap("values"));
        }
        JsonObject fixed = object(tree, path, FIXED_KEYS);
        return new VariantSpec.FixedSelection(fixed.optionalText("request"),
                fixed.optionalText("response"));
    }

    private static List<SpecNode> readChildren(JsonObject o) {
        List<SpecNode> children = new ArrayList<>();
        if (!o.has("children")) {
            return children;
        }
        List<JsonNode> items = o.array("children");
        for (int i = 0; i < items.size(); i++) {
            children.add(readNode(items.get(i), o.path("children") + "[" + i + "]"));
        }
        return children;
    }
}
