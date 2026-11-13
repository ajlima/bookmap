package pt.dazz.bookmap.copybook.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.model.BookModel;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.MemberModel;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;

/**
 * Writes and reads the JSON form of a {@link BookModel}. The writer is deterministic: the same
 * model always gives the same text.
 */
public final class ModelJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> MODEL_KEYS = Set.of("formatVersion", "sourceName", "classes");
    private static final Set<String> CLASS_KEYS = Set.of("name", "kind", "superclass",
        "subTypes", "continuable", "validatable", "members");
    private static final Set<String> SUBTYPES_KEYS = Set.of("discriminator", "types");
    private static final Set<String> TYPE_KEYS = Set.of("value", "className");
    private static final Set<String> FIELD_KEYS = Set.of("kind", "name", "javaType", "order",
        "pic", "direction", "pattern", "cobolName", "declaration");
    private static final Set<String> COLLECTION_KEYS = Set.of("kind", "name", "order",
        "direction", "maxOccurs", "fixed", "dependsOn", "itemClass", "cobolName", "declaration");

    private ModelJson() {
    }

    /**
     * Writes a model as JSON text, with two-space indentation and a final new line.
     *
     * @param model the model
     * @return the JSON text
     */
    public static String write(BookModel model) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("formatVersion", model.formatVersion());
        root.put("sourceName", model.sourceName());
        ArrayNode classes = root.putArray("classes");
        for (ClassModel type : model.classes()) {
            classes.add(writeClass(type));
        }
        // The default pretty printer uses the line separator of the platform.
        return root.toPrettyString().replace("\r\n", "\n") + "\n";
    }

    private static ObjectNode writeClass(ClassModel type) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", type.name());
        node.put("kind", type.kind().name());
        if (type.superclass() != null) {
            node.put("superclass", type.superclass());
        }
        if (type.subTypes() != null) {
            ObjectNode subTypes = node.putObject("subTypes");
            subTypes.put("discriminator", type.subTypes().discriminator());
            ArrayNode types = subTypes.putArray("types");
            for (SubTypeEntry entry : type.subTypes().types()) {
                types.addObject().put("value", entry.value()).put("className",
                    entry.className());
            }
        }
        if (type.continuable() != null) {
            node.put("continuable", type.continuable());
        }
        if (type.validatable() != null) {
            node.put("validatable", type.validatable());
        }
        ArrayNode members = node.putArray("members");
        for (MemberModel member : type.members()) {
            members.add(writeMember(member));
        }
        return node;
    }

    private static ObjectNode writeMember(MemberModel member) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (member) {
            case FieldMember field -> {
                node.put("kind", "FIELD");
                node.put("name", field.name());
                node.put("javaType", field.javaType().name());
                node.put("order", field.order());
                node.put("pic", field.pic());
                node.put("direction", field.direction().name());
                if (field.pattern() != null) {
                    node.put("pattern", field.pattern());
                }
            }
            case CollectionMember collection -> {
                node.put("kind", "COLLECTION");
                node.put("name", collection.name());
                node.put("order", collection.order());
                node.put("direction", collection.direction().name());
                node.put("maxOccurs", collection.maxOccurs());
                node.put("fixed", collection.fixed());
                if (collection.dependsOn() != null) {
                    node.put("dependsOn", collection.dependsOn());
                }
                node.put("itemClass", collection.itemClass());
            }
        }
        node.put("cobolName", member.cobolName());
        node.put("declaration", member.declaration());
        return node;
    }

    /**
     * Reads a model from JSON text.
     *
     * @param json the JSON text
     * @return the model
     * @throws ModelJsonException if the text is not a valid model
     */
    public static BookModel read(String json) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ModelJsonException("the text is not valid JSON: "
                + e.getOriginalMessage());
        }
        JsonObject root = JsonObject.of(tree, "$", MODEL_KEYS);
        int version = root.integer("formatVersion");
        if (version != BookModel.FORMAT_VERSION) {
            throw new ModelJsonException("unsupported formatVersion " + version
                + ". This reader supports " + BookModel.FORMAT_VERSION + ".");
        }
        List<ClassModel> classes = new ArrayList<>();
        List<JsonNode> nodes = root.array("classes");
        for (int i = 0; i < nodes.size(); i++) {
            classes.add(readClass(nodes.get(i), "$.classes[" + i + "]"));
        }
        return new BookModel(version, root.text("sourceName"), classes);
    }

    private static ClassModel readClass(JsonNode node, String path) {
        JsonObject type = JsonObject.of(node, path, CLASS_KEYS);
        SubTypes subTypes = null;
        if (type.has("subTypes")) {
            JsonObject sub = JsonObject.of(type.node("subTypes"), type.path("subTypes"),
                SUBTYPES_KEYS);
            List<SubTypeEntry> entries = new ArrayList<>();
            List<JsonNode> types = sub.array("types");
            for (int i = 0; i < types.size(); i++) {
                JsonObject entry = JsonObject.of(types.get(i),
                    sub.path("types") + "[" + i + "]", TYPE_KEYS);
                entries.add(new SubTypeEntry(entry.text("value"), entry.text("className")));
            }
            subTypes = new SubTypes(sub.text("discriminator"), entries);
        }
        List<MemberModel> members = new ArrayList<>();
        List<JsonNode> nodes = type.array("members");
        for (int i = 0; i < nodes.size(); i++) {
            members.add(readMember(nodes.get(i), type.path("members") + "[" + i + "]"));
        }
        return new ClassModel(type.text("name"), type.enumValue("kind", ClassKind.class),
            type.optionalText("superclass"), subTypes, members, type.optionalText("continuable"),
            type.optionalText("validatable"));
    }

    private static MemberModel readMember(JsonNode node, String path) {
        JsonNode kind = node.get("kind");
        if (kind != null && "COLLECTION".equals(kind.asText())) {
            JsonObject member = JsonObject.of(node, path, COLLECTION_KEYS);
            return new CollectionMember(member.text("name"), member.integer("order"),
                member.enumValue("direction", Direction.class), member.text("cobolName"),
                member.text("declaration"), member.integer("maxOccurs"), member.bool("fixed"),
                member.optionalText("dependsOn"), member.text("itemClass"));
        }
        JsonObject member = JsonObject.of(node, path, FIELD_KEYS);
        if (!"FIELD".equals(member.text("kind"))) {
            throw new ModelJsonException("key 'kind' in " + path
                + " must be FIELD or COLLECTION");
        }
        return new FieldMember(member.text("name"), member.enumValue("javaType", JavaType.class),
            member.integer("order"), member.text("pic"),
            member.enumValue("direction", Direction.class), member.text("cobolName"),
            member.text("declaration"), member.optionalText("pattern"));
    }
}
