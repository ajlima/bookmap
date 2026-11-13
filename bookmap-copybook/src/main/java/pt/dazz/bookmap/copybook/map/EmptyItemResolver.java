package pt.dazz.bookmap.copybook.map;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;

/**
 * Links fixed tables to their empty-item markers, as the options {@code emptyItems} and
 * {@code emptyKeys} tell. The resolver does not guess a marker.
 */
final class EmptyItemResolver {

    private static final String ITEM_KIND = "empty item option";
    private static final String KEY_KIND = "empty key option";
    private static final Map<String, EmptyItem> VALUES = Map.of("SPACES", EmptyItem.SPACES,
        "LOW-VALUES", EmptyItem.LOW_VALUES);
    private static final Set<JavaType> KEY_TYPES = Set.of(JavaType.STRING, JavaType.INT,
        JavaType.LONG, JavaType.BIG_DECIMAL);

    private final ModelMapper mapper;
    /** The fill of each table with the option {@code emptyItems}. */
    private final Map<DataItem, EmptyItem> fills = new IdentityHashMap<>();
    /** The key item of each table with the option {@code emptyKeys}. */
    private final Map<DataItem, DataItem> keys = new IdentityHashMap<>();
    /** The tables with an option that the mapper found. */
    private final Map<DataItem, Boolean> mapped = new IdentityHashMap<>();

    EmptyItemResolver(ModelMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Resolves the names in the options and checks the items.
     *
     * @param countFields the count fields, to reject a key on a table with a count field
     */
    void checkOptions(CountFieldResolver countFields) {
        // Sorted, so that the messages have a stable order.
        new TreeMap<>(mapper.options.emptyItems()).forEach((tableName, value) -> {
            DataItem table = fixedTable(ITEM_KIND, tableName);
            if (table == null) {
                return;
            }
            EmptyItem fill = VALUES.get(value);
            if (fill == null) {
                mapper.error(table, ITEM_KIND + ": " + tableName + " has the unknown value "
                    + value + ". Use SPACES or LOW-VALUES.");
            } else {
                fills.put(table, fill);
            }
        });
        new TreeMap<>(mapper.options.emptyKeys()).forEach((tableName, keyName) -> {
            DataItem table = fixedTable(KEY_KIND, tableName);
            DataItem key = mapper.findNamed(KEY_KIND, keyName);
            if (table == null || key == null) {
                return;
            }
            if (countFields.linked(table)) {
                mapper.error(table, KEY_KIND + ": " + tableName + " has a count field. Remove"
                    + " one of the two options.");
            } else {
                keys.put(table, key);
            }
        });
    }

    /** Finds a table of an option, and checks that it is a fixed table. */
    private DataItem fixedTable(String kind, String name) {
        DataItem table = mapper.findNamed(kind, name);
        if (table == null) {
            return null;
        }
        if (table.occurs() == null) {
            mapper.error(table, kind + ": " + name + " has no OCCURS");
            return null;
        }
        if (table.occurs().dependingOn() != null) {
            mapper.error(table, kind + ": " + name + " has OCCURS DEPENDING ON. Remove the"
                + " option: the DEPENDING ON item gives the number of items.");
            return null;
        }
        return table;
    }

    /**
     * Gives the fill of a table, and marks the table as mapped.
     *
     * @param table an {@code OCCURS} item
     * @return the fill, {@link EmptyItem#LOW_VALUES} without the option
     */
    EmptyItem fillOf(DataItem table) {
        EmptyItem fill = fills.get(table);
        if (fill == null) {
            return EmptyItem.LOW_VALUES;
        }
        mapped.put(table, Boolean.TRUE);
        return fill;
    }

    /**
     * Tells if the option {@code emptyKeys} gives a key for a table.
     *
     * @param table an {@code OCCURS} item
     * @return {@code true} if the table has a key
     */
    boolean hasKey(DataItem table) {
        return keys.containsKey(table);
    }

    /**
     * Gives the Java name of the key field of a table, and marks the table as mapped. Call
     * this method after the mapper made the item class.
     *
     * @param table an {@code OCCURS} item
     * @param itemClass the item class of the table
     * @return the field name, or {@code null} without a key or after an error
     */
    String keyOf(DataItem table, ModelMapper.ClassBuilder itemClass) {
        DataItem key = keys.get(table);
        if (key == null) {
            return null;
        }
        mapped.put(table, Boolean.TRUE);
        ModelMapper.Placed placed = mapper.placed.get(key);
        if (placed == null || placed.owner() != itemClass) {
            mapper.error(table, KEY_KIND + ": " + key.name()
                + " is not an elementary item of the entry of " + table.name());
            return null;
        }
        FieldMember field = placed.field();
        if (field.pattern() != null || !KEY_TYPES.contains(field.javaType())) {
            mapper.error(table, KEY_KIND + ": " + key.name()
                + " must map to a text or a number field");
            return null;
        }
        return field.name();
    }

    /**
     * Reports a table with an option that is not in a generated class. The method reports
     * nothing if another error exists: that error explains the missing table.
     */
    void checkUnmapped() {
        if (mapper.diagnostics.hasErrors()) {
            return;
        }
        Map<DataItem, Boolean> tables = new IdentityHashMap<>(fills.size() + keys.size());
        fills.keySet().forEach(table -> tables.put(table, Boolean.TRUE));
        keys.keySet().forEach(table -> tables.put(table, Boolean.TRUE));
        tables.keySet().stream().filter(table -> !mapped.containsKey(table)).forEach(table ->
            mapper.error(table, "empty item options: " + table.name() + " is not in a"
                + " generated class, so the options have no effect"));
    }
}
