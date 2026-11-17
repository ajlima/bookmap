package pt.dazz.copymapper.copybook.spec;

import java.util.List;
import java.util.Objects;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.ast.Usage;

/**
 * A node of the mapping spec: a COBOL item with its REST side.
 *
 * @param cobolName the COBOL name
 * @param level the level number
 * @param path the path from the root
 * @param picture the PICTURE, or null
 * @param usage the usage, or null
 * @param sign the sign, or null
 * @param occurs the OCCURS, or null
 * @param redefines the path of the redefined item, or null
 * @param value the VALUE, or null
 * @param conditions the condition names
 * @param location the place in the source, or null
 * @param blankWhenZero true for BLANK WHEN ZERO
 * @param edited true for an edited picture
 * @param jsonName the JSON name, or null
 * @param direction the direction, or null
 * @param jsonType the JSON type, or null
 * @param format the format, or null
 * @param table the table description, or null
 * @param counter the path of the counter, or null
 * @param variant the variant, or null
 * @param javaName the Java name, or null
 * @param children the children
 */
public record SpecNode(String cobolName, int level, String path, String picture, Usage usage,
        Sign sign, OccursSpec occurs, String redefines, ValueSpec value,
        List<ConditionSpec> conditions, SourceLocation location, boolean blankWhenZero,
        boolean edited, String jsonName, FieldDirection direction, JsonType jsonType,
        String format, TableSpec table, String counter, VariantSpec variant, String javaName,
        List<SpecNode> children) {

    /** Checks the names and copies the lists. */
    public SpecNode {
        Objects.requireNonNull(cobolName, "cobolName");
        conditions = List.copyOf(conditions);
        children = List.copyOf(children);
    }

    /**
     * Tells if the node is a group.
     *
     * @return true if it has children
     */
    public boolean isGroup() {
        return !children.isEmpty();
    }

    /**
     * Tells if the node is a FILLER.
     *
     * @return true for the name FILLER
     */
    public boolean isFiller() {
        return "FILLER".equals(cobolName);
    }
}
