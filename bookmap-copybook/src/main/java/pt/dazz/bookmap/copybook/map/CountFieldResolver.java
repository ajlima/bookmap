package pt.dazz.bookmap.copybook.map;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import pt.dazz.bookmap.copybook.ast.DataItem;

/**
 * Links fixed tables to their count fields, as the option {@code countFields} tells. The
 * resolver does not guess a link. It only suggests the option for a {@code -COUNT} item just
 * before a fixed table.
 */
final class CountFieldResolver {

    private static final String KIND = "count field option";
    private static final String COUNT_SUFFIX = "-COUNT";

    private final ModelMapper mapper;
    /** The count item of each linked table. */
    private final Map<DataItem, DataItem> counts = new IdentityHashMap<>();
    /** The linked tables that the mapper found. */
    private final Map<DataItem, Boolean> mapped = new IdentityHashMap<>();

    CountFieldResolver(ModelMapper mapper) {
        this.mapper = mapper;
    }

    /** Resolves the names in the option and checks the items. */
    void checkOptions() {
        Map<DataItem, String> users = new IdentityHashMap<>();
        // Sorted, so that the messages have a stable order.
        new TreeMap<>(mapper.options.countFields()).forEach((tableName, countName) -> {
            DataItem table = mapper.findNamed(KIND, tableName);
            DataItem count = mapper.findNamed(KIND, countName);
            if (table == null || count == null) {
                return;
            }
            if (table.occurs() == null) {
                mapper.error(table, KIND + ": " + tableName + " has no OCCURS");
            } else if (table.occurs().dependingOn() != null) {
                mapper.error(table, KIND + ": " + tableName + " has OCCURS DEPENDING ON. Remove"
                    + " the option: the DEPENDING ON item is the count field.");
            } else if (count.isGroup() || !count.picture().numeric()
                || count.picture().scale() > 0) {
                mapper.error(table, KIND + ": " + countName + " must be an elementary numeric"
                    + " item without decimals");
            } else if (users.containsKey(count)) {
                mapper.error(table, KIND + ": " + countName + " is already the count field of "
                    + users.get(count));
            } else {
                users.put(count, tableName);
                counts.put(table, count);
            }
        });
    }

    /**
     * Tells if the option links a count field to a table.
     *
     * @param table an {@code OCCURS} item
     * @return {@code true} if the table has a count field
     */
    boolean linked(DataItem table) {
        return counts.containsKey(table);
    }

    /**
     * Gives the count item of a table, and marks the table as mapped.
     *
     * @param table an {@code OCCURS} item
     * @return the count item, or {@code null} if the option does not link the table
     */
    DataItem countOf(DataItem table) {
        DataItem count = counts.get(table);
        if (count != null) {
            mapped.put(table, Boolean.TRUE);
        }
        return count;
    }

    /**
     * Suggests the option if a numeric {@code -COUNT} item without decimals comes just before
     * a fixed table.
     *
     * @param table a fixed table without a count field
     * @param previous the item just before the table in the same group, or {@code null}
     */
    void suggest(DataItem table, DataItem previous) {
        if (previous == null || table.isFiller() || previous.isGroup()
            || !previous.name().endsWith(COUNT_SUFFIX) || !previous.picture().numeric()
            || previous.picture().scale() > 0) {
            return;
        }
        mapper.diagnostics.info(table.source(), table.line(), table.column(), table.name()
            + ": " + previous.name() + " can be the count field of this fixed table. To link"
            + " them, set \"countFields\": {\"" + table.name() + "\": \"" + previous.name()
            + "\"} in the options file, or --count-field " + table.name() + "="
            + previous.name() + ".");
    }

    /**
     * Reports a linked table that is not in a generated class, for example a skipped
     * {@code REDEFINES} item. The method reports nothing if another error exists: that error
     * explains the missing table.
     */
    void checkUnmapped() {
        if (mapper.diagnostics.hasErrors()) {
            return;
        }
        counts.keySet().stream().filter(table -> !mapped.containsKey(table)).forEach(table ->
            mapper.error(table, KIND + ": " + table.name() + " is not in a generated class,"
                + " so the count field has no effect"));
    }
}
