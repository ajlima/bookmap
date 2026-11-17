package pt.dazz.copymapper.runtime.layout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import pt.dazz.copymapper.copybook.ast.Picture;
import pt.dazz.copymapper.copybook.ast.Usage;
import pt.dazz.copymapper.copybook.spec.FieldDirection;
import pt.dazz.copymapper.copybook.spec.JsonType;
import pt.dazz.copymapper.copybook.spec.MappingSpec;
import pt.dazz.copymapper.copybook.spec.SpecNode;

/**
 * Compiles a mapping spec to a layout. A spec that the runtime cannot use yet has open
 * decisions instead.
 */
public final class LayoutCompiler {

    private LayoutCompiler() {
    }

    /**
     * Compiles a mapping spec.
     *
     * @param spec the spec
     * @return the compilation, with the open decisions or the layout
     */
    public static Compilation compile(MappingSpec spec) {
        List<OpenDecision> decisions = new ArrayList<>();
        new Checker(decisions).visit(spec.root(), null, Set.of(), new HashSet<>());
        if (!decisions.isEmpty()) {
            return new Compilation(spec, decisions, null);
        }
        return new Compilation(spec, decisions, new Layout(spec, place(spec.root(), 0)));
    }

    private static LayoutNode place(SpecNode node, int offset) {
        List<LayoutNode> children = new ArrayList<>();
        int next = offset;
        for (SpecNode child : node.children()) {
            LayoutNode placed = place(child, next);
            children.add(placed);
            next += placed.length();
        }
        int length = node.isGroup() ? next - offset
                : Picture.parse(node.picture()).byteLength(usageOf(node), node.sign());
        return new LayoutNode(node, offset, length, children);
    }

    private static Usage usageOf(SpecNode node) {
        return node.usage() == null ? Usage.DISPLAY : node.usage();
    }

    /** Walks the tree of the spec and adds one decision for each rule that a node breaks. */
    private static final class Checker {

        private final List<OpenDecision> decisions;

        Checker(List<OpenDecision> decisions) {
            this.decisions = decisions;
        }

        void visit(SpecNode node, FieldDirection parentDirection, Set<String> redefined,
                Set<String> jsonNames) {
            Picture picture = cobol(node);
            unsupported(node, picture, redefined.contains(node.path()));
            jsonName(node, jsonNames);
            direction(node, parentDirection);
            type(node, picture);
            Set<String> childRedefined = node.children().stream().map(SpecNode::redefines)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> childNames = new HashSet<>();
            for (SpecNode child : node.children()) {
                visit(child, node.direction(), childRedefined, childNames);
            }
        }

        private void add(OpenDecisionKind kind, SpecNode node, String text) {
            decisions.add(new OpenDecision(kind, node.path(), node.location(),
                    node.cobolName() + " " + text));
        }

        /** Checks the COBOL side and gives the picture, or null if there is none to use. */
        private Picture cobol(SpecNode node) {
            if (node.isGroup()) {
                if (node.picture() != null) {
                    add(OpenDecisionKind.INVALID_COBOL, node, "is a group and has a PICTURE.");
                }
                return null;
            }
            if (node.picture() == null) {
                add(OpenDecisionKind.INVALID_COBOL, node, "is an elementary item and has no PICTURE.");
                return null;
            }
            Picture picture;
            try {
                picture = Picture.parse(node.picture());
            } catch (IllegalArgumentException e) {
                add(OpenDecisionKind.INVALID_COBOL, node, "has an invalid PICTURE: " + e.getMessage());
                return null;
            }
            usageAndSign(node, picture);
            return picture;
        }

        private void usageAndSign(SpecNode node, Picture picture) {
            Usage usage = usageOf(node);
            if (usage == Usage.COMP3 && !picture.numeric()) {
                add(OpenDecisionKind.INVALID_COBOL, node,
                        "has USAGE COMP-3 and a PICTURE that is not numeric.");
            }
            if (node.sign() != null && (usage != Usage.DISPLAY || !picture.signed())) {
                add(OpenDecisionKind.INVALID_COBOL, node,
                        "has a sign, but it is not a signed DISPLAY number.");
            }
        }

        private void unsupported(SpecNode node, Picture picture, boolean redefinedBySibling) {
            Feature feature = feature(node, picture, redefinedBySibling);
            if (feature != null) {
                add(OpenDecisionKind.UNSUPPORTED_TYPE, node, "has " + feature.has()
                        + ". The runtime does not support " + feature.unsupported() + ".");
            }
        }

        private static Feature feature(SpecNode node, Picture picture, boolean redefinedBySibling) {
            if (node.edited()) {
                return new Feature("an edited picture", "edited pictures");
            }
            if (node.blankWhenZero()) {
                return new Feature("BLANK WHEN ZERO");
            }
            if (node.occurs() != null) {
                return new Feature("OCCURS");
            }
            if (node.redefines() != null || redefinedBySibling) {
                return new Feature("REDEFINES");
            }
            if (picture != null && picture.signed()) {
                return new Feature("a signed picture", "signed pictures");
            }
            if (EnumSet.of(Usage.COMP, Usage.COMP5).contains(node.usage())) {
                return new Feature("USAGE " + node.usage());
            }
            if (EnumSet.of(JsonType.DATE, JsonType.TIME, JsonType.DATE_TIME)
                    .contains(node.jsonType())) {
                return new Feature("the JSON type " + node.jsonType());
            }
            return null;
        }

        /** A feature that the runtime does not support, as the text says it. */
        private record Feature(String has, String unsupported) {
            Feature(String name) {
                this(name, name);
            }
        }

        private void jsonName(SpecNode node, Set<String> jsonNames) {
            if (node.isFiller() || node.direction() == FieldDirection.IGNORE) {
                return;
            }
            if (node.jsonName() == null || node.jsonName().isBlank()) {
                add(OpenDecisionKind.INVALID_JSON_NAME, node, "has no JSON name.");
            } else if (!jsonNames.add(node.jsonName())) {
                add(OpenDecisionKind.INVALID_JSON_NAME, node, "has the JSON name "
                        + node.jsonName() + ", which a sibling item has too.");
            }
        }

        private void direction(SpecNode node, FieldDirection parent) {
            FieldDirection direction = node.direction();
            if (direction == null) {
                add(OpenDecisionKind.INVALID_DIRECTION, node, "has no direction.");
            } else if (node.isFiller() && direction != FieldDirection.IGNORE) {
                add(OpenDecisionKind.INVALID_DIRECTION, node,
                        "is a FILLER. Its direction must be IGNORE.");
            } else if (direction == FieldDirection.DERIVED) {
                add(OpenDecisionKind.INVALID_DIRECTION, node,
                        "has the direction DERIVED. The runtime has no counter or discriminator to derive it from.");
            } else if (parent != null && !within(direction, parent)) {
                add(OpenDecisionKind.INVALID_DIRECTION, node, "has the direction " + direction
                        + ", which is outside the direction " + parent + " of its group.");
            }
        }

        private static boolean within(FieldDirection child, FieldDirection parent) {
            return (!child.inRequest() || parent.inRequest())
                    && (!child.inResponse() || parent.inResponse());
        }

        private void type(SpecNode node, Picture picture) {
            if (node.isGroup()) {
                if (node.jsonType() != null) {
                    add(OpenDecisionKind.INVALID_TYPE, node, "is a group and has a JSON type.");
                }
            } else if (node.isFiller()) {
                return;
            } else if (node.jsonType() == null) {
                add(OpenDecisionKind.INVALID_TYPE, node, "has no JSON type.");
            } else if (picture != null && mismatch(node.jsonType(), picture)) {
                add(OpenDecisionKind.INVALID_TYPE, node, "has the JSON type " + node.jsonType()
                        + ", which does not fit the PICTURE " + node.picture() + ".");
            }
        }

        private static boolean mismatch(JsonType type, Picture picture) {
            return switch (type) {
                case TEXT -> picture.numeric();
                case NUMBER, DECIMAL_STRING -> !picture.numeric();
                default -> false;
            };
        }
    }
}
