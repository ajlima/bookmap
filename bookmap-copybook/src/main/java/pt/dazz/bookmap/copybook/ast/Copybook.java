package pt.dazz.bookmap.copybook.ast;

import java.util.List;

/**
 * Gives the data items of a copybook.
 *
 * @param sourceName the file name of the copybook, without a directory
 * @param records the level 01 items, in source order
 */
public record Copybook(String sourceName, List<DataItem> records) {

    /**
     * Creates a copybook.
     *
     * @param sourceName the source name
     * @param records the records
     */
    public Copybook {
        records = List.copyOf(records);
    }
}
