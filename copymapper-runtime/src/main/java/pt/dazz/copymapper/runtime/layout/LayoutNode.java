package pt.dazz.copymapper.runtime.layout;

import java.util.List;
import pt.dazz.copymapper.copybook.spec.SpecNode;

/**
 * A node of the layout: a spec node with its place in the record.
 *
 * @param spec the spec node
 * @param offset the offset in bytes from the start of the record
 * @param length the length in bytes
 * @param children the children
 */
public record LayoutNode(SpecNode spec, int offset, int length, List<LayoutNode> children) {

    /** Copies the list of children. */
    public LayoutNode {
        children = List.copyOf(children);
    }
}
