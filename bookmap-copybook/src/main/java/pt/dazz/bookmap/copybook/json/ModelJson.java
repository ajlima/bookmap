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
import pt.dazz.bookmap.copybook.model.ConditionMember;
import pt.dazz.bookmap.copybook.model.EnumConstant;
import pt.dazz.bookmap.copybook.model.EnumModel;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.MemberModel;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;
import pt.dazz.bookmap.copybook.model.ValueRange;

/**
 * Writes and reads the JSON form of a {@link BookModel}. The writer is deterministic: the same
 * model always gives the same text.
 */
public final class ModelJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> MODEL_KEYS = Set.of("formatVersion", "sourceName", "classes",
        "enums");
    private static final Set<String> CLASS_KEYS = Set.of("name", "kind", "superclass",
        "subTypes", "members");
    private static final Set<String> SUBTYPES_KEYS = Set.of("discriminator", "types");
    private static final Set<String> TYPE_KEYS = Set.of("value", "className");
    private static final Set<String> FIELD_KEYS = Set.of("kind", "name", "javaType", "order",
        "pic", "direction", "enumType", "initialValue", "conditions", "cobolName",
        "declaration");
    private static final Set<String> CONDITION_KEYS = Set.of("cobolName", "constant", "method",
        "values");
    private static final Set<String> VALUE_KEYS = Set.of("from", "thru");
    private static final Set<String> ENUM_KEYS = Set.of("name", "cobolName", "constants");
    private static final Set<String> CONSTANT_KEYS = Set.of("name", "cobolName", "values");
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
        if (!model.enums().isEmpty()) {
            ArrayNode enums = root.putArray("enums");
            for (EnumModel type : model.enums()) {
                ObjectNode node = enums.addObject().put("name", type.name())
                    .put("cobolName", type.cobolName());
                ArrayNode constants = node.putArray("constants");
                for (EnumConstant constant : type.constants()) {
                    writeValues(constants.addObject().put("name", constant.name())
                        .put("cobolName", constant.cobolName()), constant.values());
                }
            }
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
                if (field.enumType() != null) {
                    node.put("enumType", field.enumType());
                }
                if (field.initialValue() != null) {
                    node.put("initialValue", field.initialValue());
                }
                if (!field.conditions().isEmpty()) {
                    ArrayNode conditions = node.putArray("conditions");
                    for (ConditionMember condition : field.conditions()) {
                        writeValues(conditions.addObject()
                            .put("cobolName", condition.cobolName())
                            .put("constant", condition.constant())
                            .put("method", condition.method()), condition.values());
                    }
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

    private static void writeValues(ObjectNode node, List<ValueRange> values) {
        ArrayNode array = node.putArray("values");
        for (ValueRange value : values) {
            ObjectNode item = array.addObject().put("from", value.from());
            if (value.thru() != null) {
                item.put("thru", value.thru());
            }
        }
    }

    private static List<ValueRange> readValues(JsonObject owner) {
        List<ValueRange> values = new ArrayList<>();
        List<JsonNode> nodes = owner.array("values");
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject value = JsonObject.of(nodes.get(i), owner.path("values") + "[" + i + "]",
                VALUE_KEYS);
            values.add(new ValueRange(value.text("from"), value.optionalText("thru")));
        }
        return values;
    }

    private static EnumModel readEnum(JsonNode node, String path) {
        JsonObject type = JsonObject.of(node, path, ENUM_KEYS);
        List<EnumConstant> constants = new ArrayList<>();
        List<JsonNode> nodes = type.array("constants");
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject constant = JsonObject.of(nodes.get(i),
                type.path("constants") + "[" + i + "]", CONSTANT_KEYS);
            constants.add(new EnumConstant(constant.text("name"), constant.text("cobolName"),
                readValues(constant)));
        }
        return new EnumModel(type.text("name"), type.text("cobolName"), constants);
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
        List<EnumModel> enums = new ArrayList<>();
        List<JsonNode> enumNodes = root.has("enums") ? root.array("enums") : List.of();
        for (int i = 0; i < enumNodes.size(); i++) {
            enums.add(readEnum(enumNodes.get(i), "$.enums[" + i + "]"));
        }
        return new BookModel(version, root.text("sourceName"), classes, enums);
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
            type.optionalText("superclass"), subTypes, members);
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
        List<ConditionMember> conditions = new ArrayList<>();
        List<JsonNode> nodes = member.has("conditions") ? member.array("conditions") : List.of();
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject condition = JsonObject.of(nodes.get(i),
                member.path("conditions") + "[" + i + "]", CONDITION_KEYS);
            conditions.add(new ConditionMember(condition.text("cobolName"),
                condition.text("constant"), condition.text("method"), readValues(condition)));
        }
        JavaType javaType = member.enumValue("javaType", JavaType.class);
        String enumType = member.optionalText("enumType");
        if ((javaType == JavaType.ENUM) != (enumType != null)) {
            throw new ModelJsonException("key 'enumType' in " + path
                + " must be present only with javaType ENUM");
        }
        return new FieldMember(member.text("name"), javaType,
            member.integer("order"), member.text("pic"),
            member.enumValue("direction", Direction.class), member.text("cobolName"),
            member.text("declaration"), enumType, member.optionalText("initialValue"),
            conditions);
    }
}
