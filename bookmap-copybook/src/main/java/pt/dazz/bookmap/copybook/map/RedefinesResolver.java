package pt.dazz.bookmap.copybook.map;

import java.util.List;
import pt.dazz.bookmap.copybook.ast.DataItem;

/**
 * Maps the {@code REDEFINES} items of a copybook.
 */
final class RedefinesResolver {

    private final ModelMapper mapper;

    RedefinesResolver(ModelMapper mapper) {
        this.mapper = mapper;
    }

    /** Checks the names in the direction and subtype decisions. */
    void checkDecisions() {
        // Task 8 adds the checks.
    }

    /** Gives the later siblings that redefine an item. */
    static List<DataItem> alternatives(DataItem item, List<DataItem> siblings) {
        return siblings.stream().filter(sibling -> sibling.redefines() != null
            && sibling.redefines().name().equals(item.name())).toList();
    }

    /**
     * Handles an item with {@code REDEFINES}. An elementary item is skipped. A group is part of
     * the area of the item that it redefines.
     */
    void redefiningItem(DataItem item, List<DataItem> siblings) {
        DataItem base = siblings.stream()
            .filter(sibling -> sibling.name().equals(item.redefines().name()))
            .findFirst().orElse(null);
        if (!item.isGroup()) {
            mapper.warning(item, item.name() + " redefines " + item.redefines()
                + " and is skipped");
        } else if (base == null || !base.isGroup()) {
            mapper.error(item, item.name() + " redefines " + item.redefines()
                + ", which is not a group with the same parent");
        }
    }

    /** Maps a group {@code REDEFINES} area. */
    void area(DataItem base, List<DataItem> alternatives, List<DataItem> following,
              ModelMapper.ClassBuilder owner, ModelMapper.Context context) {
        mapper.error(alternatives.get(0),
            "REDEFINES of a group needs a direction or a subtype decision");
    }
}
