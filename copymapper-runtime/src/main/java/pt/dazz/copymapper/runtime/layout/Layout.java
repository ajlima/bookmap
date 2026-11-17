package pt.dazz.copymapper.runtime.layout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import pt.dazz.copymapper.copybook.spec.MappingSpec;

/** The byte layout of the record of a mapping spec. */
public final class Layout {

    private final MappingSpec spec;
    private final LayoutNode root;
    private final Map<String, LayoutNode> byPath = new LinkedHashMap<>();

    Layout(MappingSpec spec, LayoutNode root) {
        this.spec = spec;
        this.root = root;
        index(root);
    }

    private void index(LayoutNode node) {
        byPath.put(node.spec().path(), node);
        node.children().forEach(this::index);
    }

    /**
     * Gives the mapping spec.
     *
     * @return the spec
     */
    public MappingSpec spec() {
        return spec;
    }

    /**
     * Gives the root node.
     *
     * @return the root
     */
    public LayoutNode root() {
        return root;
    }

    /**
     * Gives the largest request: the record length.
     *
     * @return the length in bytes
     */
    public int maxRequestLength() {
        return root.length();
    }

    /**
     * Gives the largest response: the record length.
     *
     * @return the length in bytes
     */
    public int maxResponseLength() {
        return root.length();
    }

    /**
     * Finds a node by its path.
     *
     * @param path the path from the root, for example {@code REC.GROUP.ITEM}
     * @return the node, or empty
     */
    public Optional<LayoutNode> node(String path) {
        return Optional.ofNullable(byPath.get(path));
    }

    /**
     * Gives the charset of the record.
     *
     * @return the canonical name of the charset
     */
    public String charset() {
        return spec.charset();
    }
}
