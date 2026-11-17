package pt.dazz.copymapper.runtime.layout;

import java.util.List;
import java.util.stream.Collectors;
import pt.dazz.copymapper.copybook.spec.MappingSpec;
import pt.dazz.copymapper.exception.MappingException;

/** The result of the compilation of a mapping spec: the open decisions, or the layout. */
public final class Compilation {

    private final MappingSpec spec;
    private final List<OpenDecision> openDecisions;
    private final Layout layout;

    Compilation(MappingSpec spec, List<OpenDecision> openDecisions, Layout layout) {
        this.spec = spec;
        this.openDecisions = List.copyOf(openDecisions);
        this.layout = layout;
    }

    /**
     * Gives the open decisions, in the order of the items.
     *
     * @return the decisions; empty if the compilation is complete
     */
    public List<OpenDecision> openDecisions() {
        return openDecisions;
    }

    /**
     * Tells if the spec has no open decision.
     *
     * @return true if the layout exists
     */
    public boolean complete() {
        return openDecisions.isEmpty();
    }

    /**
     * Gives the layout.
     *
     * @return the layout
     * @throws MappingException with all the open decisions if the compilation is not complete
     */
    public Layout layout() {
        if (!complete()) {
            String lines = openDecisions.stream().map(OpenDecision::format)
                    .collect(Collectors.joining("\n"));
            throw new MappingException("the mapping spec of " + spec.root().path() + " has "
                    + openDecisions.size() + " open decisions:\n" + lines);
        }
        return layout;
    }
}
