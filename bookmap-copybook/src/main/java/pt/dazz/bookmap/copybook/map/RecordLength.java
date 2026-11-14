package pt.dazz.bookmap.copybook.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.Usage;
import pt.dazz.bookmap.copybook.diag.Diagnostics;

/**
 * Reports the length of a record, and compares it with the maximum record length of the
 * mapping options. A transport can have a size limit, for example the COMMAREA of CICS.
 */
final class RecordLength {

    /** The number of tables that a warning names. */
    static final int LARGEST_TABLES = 3;

    /** A table ({@code OCCURS} item) and its length in bytes. */
    private record Table(DataItem item, int entryLength, int length) {
    }

    private RecordLength() {
    }

    /**
     * Adds an info diagnostic with the length of a record. Adds a warning if the length is
     * more than the maximum record length.
     *
     * @param record the level {@code 01} item
     * @param maxRecordLength the maximum record length, or {@code null} for no check
     * @param diagnostics the diagnostics of the map step
     */
    static void check(DataItem record, Integer maxRecordLength, Diagnostics diagnostics) {
        int length = RedefinesResolver.length(record, Usage.DISPLAY);
        List<Table> tables = new ArrayList<>();
        boolean variable = tables(record.children(), usageOf(record, Usage.DISPLAY), tables);
        String size = (variable ? "at most " : "") + length + " bytes";
        diagnostics.info(record.source(), record.line(), record.column(),
            "record " + record.name() + " has " + size);
        if (maxRecordLength == null || length <= maxRecordLength) {
            return;
        }
        StringBuilder message = new StringBuilder("record ").append(record.name())
            .append(" has ").append(size).append(", more than the maximum record length of ")
            .append(maxRecordLength).append(" bytes.");
        if (!tables.isEmpty()) {
            message.append(" The largest tables: ").append(tables.stream()
                .sorted(Comparator.comparingInt(Table::length).reversed())
                .limit(LARGEST_TABLES)
                .map(t -> t.item().name() + " (" + t.item().occurs().max() + " x "
                    + t.entryLength() + " = " + t.length() + " bytes)")
                .collect(Collectors.joining(", "))).append('.');
        }
        diagnostics.warning(record.source(), record.line(), record.column(),
            message.toString());
    }

    /**
     * Collects the tables of a group. The method does not look into a table: the entry length
     * of a table contains its inner tables. Gives {@code true} if a table has
     * {@code DEPENDING ON}.
     */
    private static boolean tables(List<DataItem> items, Usage usage, List<Table> tables) {
        boolean variable = false;
        for (DataItem item : items) {
            if (item.occurs() != null) {
                int entry = RedefinesResolver.entryLength(item, usage);
                tables.add(new Table(item, entry, entry * item.occurs().max()));
                variable |= item.occurs().dependingOn() != null;
            } else {
                variable |= tables(item.children(), usageOf(item, usage), tables);
            }
        }
        return variable;
    }

    private static Usage usageOf(DataItem item, Usage inherited) {
        return item.usage() != null ? item.usage() : inherited;
    }
}
