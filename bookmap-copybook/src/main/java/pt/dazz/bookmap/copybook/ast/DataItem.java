package pt.dazz.bookmap.copybook.ast;

import java.util.List;
import pt.dazz.bookmap.copybook.parse.Picture;

/**
 * Describes one data description entry of a copybook.
 *
 * @param level the level number (1 to 49)
 * @param name the data name, or {@code "FILLER"}
 * @param picture the picture, or {@code null} for a group
 * @param usage the usage clause of this entry, or {@code null}
 * @param occurs the {@code OCCURS} clause, or {@code null}
 * @param redefines the redefined item, or {@code null}
 * @param conditions the level 88 conditions of this item
 * @param children the subordinate items, in source order
 * @param source the source name of the entry (a copybook or a {@code COPY} member)
 * @param line the line of the level number
 * @param column the column of the level number
 * @param declaration the normalized entry text without {@code VALUE} clauses
 */
public record DataItem(int level, String name, Picture picture, Usage usage, Occurs occurs,
                       QualifiedName redefines, List<Condition> conditions,
                       List<DataItem> children, String source, int line, int column,
                       String declaration) {

    /** The name of an item without a name. */
    public static final String FILLER = "FILLER";

    /**
     * Creates an item.
     *
     * @param level the level
     * @param name the name
     * @param picture the picture
     * @param usage the usage
     * @param occurs the occurs clause
     * @param redefines the redefined item
     * @param conditions the conditions
     * @param children the children
     * @param source the source name
     * @param line the line
     * @param column the column
     * @param declaration the declaration
     */
    public DataItem {
        conditions = List.copyOf(conditions);
        children = List.copyOf(children);
    }

    /**
     * Tells if the item is a group.
     *
     * @return {@code true} if the item has subordinate items
     */
    public boolean isGroup() {
        return !children.isEmpty();
    }

    /**
     * Tells if the item is a {@code FILLER}.
     *
     * @return {@code true} for a {@code FILLER} or an item without a name
     */
    public boolean isFiller() {
        return FILLER.equals(name);
    }
}
