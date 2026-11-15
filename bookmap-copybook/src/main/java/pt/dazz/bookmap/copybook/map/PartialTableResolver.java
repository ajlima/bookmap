package pt.dazz.bookmap.copybook.map;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.model.MemberModel;

/**
 * Finds the fixed tables that a response can end inside, as the option {@code partialTables}
 * tells. The resolver does not guess a table. It checks that each table is at the end of the
 * response.
 */
final class PartialTableResolver {

    private static final String KIND = "partial table option";

    /** A partial table and the class that holds its collection. */
    private record Placed(ModelMapper.ClassBuilder owner, String member, DataItem table) {
    }

    private final ModelMapper mapper;
    /** The tables of the option. */
    private final Map<DataItem, Boolean> tables = new IdentityHashMap<>();
    /** The tables of the option that the mapper found. */
    private final Map<DataItem, Boolean> mapped = new IdentityHashMap<>();
    private final List<Placed> placed = new ArrayList<>();

    PartialTableResolver(ModelMapper mapper) {
        this.mapper = mapper;
    }

    /** Resolves the names in the option and checks that each item is a fixed table. */
    void checkOptions() {
        // Sorted, so that the messages have a stable order.
        new TreeSet<>(mapper.options.partialTables()).forEach(name -> {
            DataItem table = mapper.findNamed(KIND, name);
            if (table == null) {
                return;
            }
            if (table.occurs() == null) {
                mapper.error(table, KIND + ": " + name + " has no OCCURS");
            } else if (table.occurs().dependingOn() != null) {
                mapper.error(table, KIND + ": " + name + " has OCCURS DEPENDING ON. Remove the"
                    + " option: the response has only the used entries.");
            } else {
                tables.put(table, Boolean.TRUE);
            }
        });
    }

    /**
     * Tells if a table is partial, and marks it as mapped.
     *
     * @param table an {@code OCCURS} item
     * @param context the state of the walk at the table
     * @return {@code true} if the option names the table and the table can be partial
     */
    boolean partial(DataItem table, ModelMapper.Context context) {
        if (!tables.containsKey(table)) {
            return false;
        }
        mapped.put(table, Boolean.TRUE);
        if (context.occurs() != null) {
            mapper.error(table, KIND + ": " + table.name() + " is in an entry of "
                + context.occurs() + ". Only a top-level table can end the response.");
            return false;
        }
        if (context.direction() == Direction.INPUT) {
            mapper.error(table, KIND + ": " + table.name() + " has the direction INPUT."
                + " A request always has the full table.");
            return false;
        }
        return true;
    }

    /**
     * Keeps the collection of a partial table, for {@link #checkAtEnd()}.
     *
     * @param owner the class that holds the collection
     * @param member the Java name of the collection
     * @param table the {@code OCCURS} item
     */
    void added(ModelMapper.ClassBuilder owner, String member, DataItem table) {
        placed.add(new Placed(owner, member, table));
    }

    /**
     * Checks that no output member comes after a partial table: in the same class, or in a
     * subclass of it.
     */
    void checkAtEnd() {
        for (Placed table : placed) {
            List<MemberModel> members = table.owner().members;
            int index = 0;
            while (!members.get(index).name().equals(table.member())) {
                index++;
            }
            firstOutput(members.subList(index + 1, members.size()))
                .or(() -> firstOutputOfSubclass(table.owner().name))
                .ifPresent(after -> mapper.error(table.table(), KIND + ": "
                    + table.table().name() + " is not at the end of the response: "
                    + after.cobolName() + " comes after it"));
        }
    }

    private static Optional<MemberModel> firstOutput(List<MemberModel> members) {
        return members.stream().filter(member -> member.direction() != Direction.INPUT)
            .findFirst();
    }

    /** Gives the first output member of a subclass of a class. A subclass has no subclass. */
    private Optional<MemberModel> firstOutputOfSubclass(String className) {
        return mapper.classes.stream().filter(type -> className.equals(type.superclass))
            .flatMap(type -> firstOutput(type.members).stream())
            .findFirst();
    }

    /**
     * Reports a table of the option that is not in a generated class. The method reports
     * nothing if another error exists: that error explains the missing table.
     */
    void checkUnmapped() {
        if (mapper.diagnostics.hasErrors()) {
            return;
        }
        tables.keySet().stream().filter(table -> !mapped.containsKey(table)).forEach(table ->
            mapper.error(table, KIND + ": " + table.name() + " is not in a generated class, so"
                + " the option has no effect"));
    }
}
