package pt.dazz.copymapper.runtime.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.UnaryOperator;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.ast.Usage;
import pt.dazz.copymapper.copybook.parse.CopybookParser;
import pt.dazz.copymapper.copybook.parse.ParseOptions;
import pt.dazz.copymapper.copybook.parse.SourceFormat;
import pt.dazz.copymapper.copybook.spec.DraftOptions;
import pt.dazz.copymapper.copybook.spec.FieldDirection;
import pt.dazz.copymapper.copybook.spec.JsonType;
import pt.dazz.copymapper.copybook.spec.MappingSpec;
import pt.dazz.copymapper.copybook.spec.MappingSpecs;
import pt.dazz.copymapper.copybook.spec.OccursSpec;
import pt.dazz.copymapper.copybook.spec.SpecNode;

/** Test helpers: drafts a spec from copybook lines and changes one node of it. */
final class Specs {

    private Specs() {
    }

    static MappingSpec draft(String record, String... lines) {
        var result = CopybookParser.parse("GET-ACCOUNT.cpy", String.join("\n", lines),
                new ParseOptions(SourceFormat.FREE, StandardCharsets.UTF_8, List.of(), null));
        assertThat(result.copybook()).as(result.diagnostics().toString()).isNotNull();
        return MappingSpecs.draft(result.copybook(), record, DraftOptions.defaults());
    }

    static SpecNode find(MappingSpec spec, String path) {
        return find(spec.root(), path);
    }

    private static SpecNode find(SpecNode node, String path) {
        if (node.path().equals(path)) {
            return node;
        }
        for (SpecNode child : node.children()) {
            SpecNode found = find(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Replaces the node at the path by the result of the change. */
    static MappingSpec edit(MappingSpec spec, String path, UnaryOperator<Change> change) {
        SpecNode root = replace(spec.root(), path, change);
        return new MappingSpec(spec.formatVersion(), spec.sourceName(), spec.sourceHash(),
                spec.charset(), spec.byteOrder(), root);
    }

    private static SpecNode replace(SpecNode node, String path, UnaryOperator<Change> change) {
        List<SpecNode> children = node.children().stream()
                .map(c -> replace(c, path, change)).toList();
        Change c = new Change(node).children(children);
        return node.path().equals(path) ? change.apply(c).build() : c.build();
    }

    /** A copy of a node with some changed components. */
    static final class Change {
        private final SpecNode n;
        private String picture;
        private Usage usage;
        private Sign sign;
        private OccursSpec occurs;
        private String redefines;
        private boolean blankWhenZero;
        private boolean edited;
        private String jsonName;
        private FieldDirection direction;
        private JsonType jsonType;
        private List<SpecNode> children;

        Change(SpecNode n) {
            this.n = n;
            picture = n.picture();
            usage = n.usage();
            sign = n.sign();
            occurs = n.occurs();
            redefines = n.redefines();
            blankWhenZero = n.blankWhenZero();
            edited = n.edited();
            jsonName = n.jsonName();
            direction = n.direction();
            jsonType = n.jsonType();
            children = n.children();
        }

        Change picture(String v) {
            picture = v;
            return this;
        }

        Change usage(Usage v) {
            usage = v;
            return this;
        }

        Change sign(Sign v) {
            sign = v;
            return this;
        }

        Change occurs(OccursSpec v) {
            occurs = v;
            return this;
        }

        Change redefines(String v) {
            redefines = v;
            return this;
        }

        Change blankWhenZero(boolean v) {
            blankWhenZero = v;
            return this;
        }

        Change edited(boolean v) {
            edited = v;
            return this;
        }

        Change jsonName(String v) {
            jsonName = v;
            return this;
        }

        Change direction(FieldDirection v) {
            direction = v;
            return this;
        }

        Change jsonType(JsonType v) {
            jsonType = v;
            return this;
        }

        Change children(List<SpecNode> v) {
            children = v;
            return this;
        }

        SpecNode build() {
            return new SpecNode(n.cobolName(), n.level(), n.path(), picture, usage, sign,
                    occurs, redefines, n.value(), n.conditions(), n.location(), blankWhenZero,
                    edited, jsonName, direction, jsonType, n.format(), n.table(), n.counter(),
                    n.variant(), n.javaName(), children);
        }
    }
}
