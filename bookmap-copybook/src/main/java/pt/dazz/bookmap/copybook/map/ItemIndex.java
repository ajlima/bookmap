package pt.dazz.bookmap.copybook.map;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.QualifiedName;

/**
 * Finds data items by a bare or a qualified name.
 */
public final class ItemIndex {

    /** The result of a search. */
    public sealed interface Lookup permits Found, NotFound, Ambiguous {
    }

    /**
     * One item matches.
     *
     * @param item the item
     */
    public record Found(DataItem item) implements Lookup {
    }

    /** No item matches. */
    public record NotFound() implements Lookup {
    }

    /**
     * More than one item matches.
     *
     * @param matches the matches, as fully qualified names
     */
    public record Ambiguous(List<String> matches) implements Lookup {
    }

    private final Map<DataItem, List<DataItem>> ancestors = new IdentityHashMap<>();
    private final List<DataItem> items = new ArrayList<>();

    /**
     * Indexes all named items of a copybook.
     *
     * @param copybook the copybook
     */
    public ItemIndex(Copybook copybook) {
        for (DataItem record : copybook.records()) {
            add(record, List.of());
        }
    }

    private void add(DataItem item, List<DataItem> path) {
        ancestors.put(item, path);
        if (!item.isFiller()) {
            items.add(item);
        }
        List<DataItem> inner = new ArrayList<>(path);
        inner.add(item);
        for (DataItem child : item.children()) {
            add(child, List.copyOf(inner));
        }
    }

    /**
     * Gives the ancestors of an item, from the record to the parent.
     *
     * @param item an item of the copybook
     * @return the ancestors; empty for a record
     */
    public List<DataItem> pathTo(DataItem item) {
        return ancestors.get(item);
    }

    /**
     * Finds the items that match a name. The qualifiers must be ancestors of the item, in
     * order from the nearest group outward. They need not be direct parents.
     *
     * @param name the name
     * @return the result
     */
    public Lookup find(QualifiedName name) {
        List<DataItem> matches = items.stream()
            .filter(item -> item.name().equals(name.name()) && qualifies(item, name))
            .toList();
        if (matches.isEmpty()) {
            return new NotFound();
        }
        if (matches.size() == 1) {
            return new Found(matches.get(0));
        }
        return new Ambiguous(matches.stream().map(this::fullName).toList());
    }

    private boolean qualifies(DataItem item, QualifiedName name) {
        List<DataItem> path = ancestors.get(item);
        int next = 1;
        for (int i = path.size() - 1; i >= 0 && next < name.parts().size(); i--) {
            if (path.get(i).name().equals(name.parts().get(next))) {
                next++;
            }
        }
        return next == name.parts().size();
    }

    /**
     * Gives the fully qualified name of an item, for example {@code CITY OF ADDRESS OF ORDER}.
     *
     * @param item an item of the copybook
     * @return the name
     */
    public String fullName(DataItem item) {
        List<String> parts = new ArrayList<>();
        parts.add(item.name());
        List<DataItem> path = ancestors.get(item);
        for (int i = path.size() - 1; i >= 0; i--) {
            parts.add(path.get(i).name());
        }
        return String.join(" OF ", parts);
    }
}
