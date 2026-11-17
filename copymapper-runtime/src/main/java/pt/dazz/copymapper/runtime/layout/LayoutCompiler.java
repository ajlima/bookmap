package pt.dazz.copymapper.runtime.layout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

    private static final Set<JsonType> DATE_TYPES =
            EnumSet.of(JsonType.DATE, JsonType.TIME, JsonType.DATE_TIME);

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
        new Checker(decisions).visit(spec.root(), null, Map.of(), new HashSet<>(), false);
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
                : Picture.parse(node.picture()).byteLength(node.usage(), node.sign());
        return new LayoutNode(node, offset, length, children);
    }

    /** Walks the tree of the spec and adds one decision for each rule that a node breaks. */
    private static final class Checker {

        /** An index after the name in a path: #2, #3, and so on. */
        private static final String INDEX = "(#([2-9]|[1-9][0-9]+))?";

        private final List<OpenDecision> decisions;
        private final Set<String> paths = new HashSet<>();

        Checker(List<OpenDecision> decisions) {
            this.decisions = decisions;
        }

        /**
         * Checks a node and its children.
         *
         * @param ignored true if an ancestor has the direction IGNORE
         * @return the length of the node in bytes, or 0 if it is not known or too long
         */
        long visit(SpecNode node, SpecNode parent, Map<String, String> redefined,
                Set<String> jsonNames, boolean ignored) {
            structure(node, parent);
            Picture picture = cobol(node);
            String redefinedBy = redefined.get(node.path());
            unsupported(node, picture, redefinedBy);
            rest(node, redefinedBy != null);
            boolean hidden = ignored || node.isFiller()
                    || node.direction() == FieldDirection.IGNORE;
            if (!hidden) {
                jsonName(node, jsonNames);
            }
            direction(node, parent == null || ignored ? null : parent.direction());
            type(node, picture, hidden);
            Map<String, String> childRedefined = node.children().stream()
                    .filter(c -> c.redefines() != null).collect(Collectors.groupingBy(
                            SpecNode::redefines,
                            Collectors.mapping(SpecNode::cobolName, Collectors.joining(", "))));
            Set<String> childNames = new HashSet<>();
            long length = 0;
            for (SpecNode child : node.children()) {
                length += visit(child, node, childRedefined, childNames,
                        ignored || node.direction() == FieldDirection.IGNORE);
            }
            if (!node.isGroup() && picture != null && node.usage() != null) {
                length = picture.byteLength(node.usage(), node.sign());
            }
            if (length < 0 || length > Integer.MAX_VALUE) {
                add(OpenDecisionKind.INVALID_COBOL, node, "is longer than " + Integer.MAX_VALUE
                        + " bytes.");
                return 0;
            }
            return length;
        }

        /** Checks the path and the level against the parent. */
        private void structure(SpecNode node, SpecNode parent) {
            String path = node.path();
            if (!paths.add(path)) {
                add(OpenDecisionKind.INVALID_COBOL, node, "has the path " + path
                        + ", which another item has too.");
            }
            if (parent == null) {
                if (!path.equals(node.cobolName())) {
                    add(OpenDecisionKind.INVALID_COBOL, node, "has the path " + path
                            + ". The path of a record must be its name.");
                }
                if (node.level() != 1) {
                    add(OpenDecisionKind.INVALID_COBOL, node, "has the level " + node.level()
                            + ". A record must have the level 1.");
                }
                return;
            }
            String expected = parent.path() + "." + node.cobolName();
            if (!path.matches(Pattern.quote(expected) + INDEX)) {
                add(OpenDecisionKind.INVALID_COBOL, node, "has the path " + path
                        + ". Its path must be " + expected + ", or " + expected
                        + "#n with n from 2.");
            }
            int level = node.level();
            if (level < 2 || level > 49 || level <= parent.level()) {
                add(OpenDecisionKind.INVALID_COBOL, node, "has the level " + level
                        + ". An item of a group must have a level from 2 to 49 that is greater"
                        + " than " + parent.level() + ", the level of its group.");
            }
        }

        /** Checks that the variant, the table, the counter, and the format fit the item. */
        private void rest(SpecNode node, boolean redefined) {
            if (node.variant() != null && node.redefines() == null && !redefined) {
                add(OpenDecisionKind.INVALID_VARIANT, node,
                        "has a variant, but it is not part of a REDEFINES area.");
            }
            boolean odo = node.occurs() != null && node.occurs().dependingOn() != null;
            if (node.table() != null && (node.occurs() == null || odo)) {
                add(OpenDecisionKind.INVALID_TABLE, node,
                        "has a table, but it has no fixed OCCURS.");
            }
            if (node.counter() != null && !odo) {
                add(OpenDecisionKind.INVALID_TABLE, node,
                        "has a counter, but it has no OCCURS DEPENDING ON.");
            }
            if (node.format() != null && !DATE_TYPES.contains(node.jsonType())) {
                add(OpenDecisionKind.INVALID_TYPE, node,
                        "has a format, but its JSON type is not DATE, TIME, or DATE_TIME.");
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
                // The message of the parser can name an issue of the generator; omit it here.
                add(OpenDecisionKind.INVALID_COBOL, node, "has an invalid PICTURE: "
                        + e.getMessage().replaceFirst(" See issue #\\d+\\.", ""));
                return null;
            }
            usageAndSign(node, picture);
            return picture;
        }

        private void usageAndSign(SpecNode node, Picture picture) {
            Usage usage = node.usage();
            if (usage == null) {
                add(OpenDecisionKind.INVALID_COBOL, node, "has no USAGE.");
                return;
            }
            if (usage == Usage.COMP3 && !picture.numeric()) {
                add(OpenDecisionKind.INVALID_COBOL, node,
                        "has USAGE COMP-3 and a PICTURE that is not numeric.");
            }
            if (node.sign() != null && (usage != Usage.DISPLAY || !picture.signed())) {
                add(OpenDecisionKind.INVALID_COBOL, node,
                        "has a sign, but it is not a signed DISPLAY number.");
            }
        }

        private void unsupported(SpecNode node, Picture picture, String redefinedBy) {
            List<Feature> features = features(node, picture);
            boolean redefinedSide = redefinedBy != null;
            int count = features.size() + (redefinedSide ? 1 : 0);
            if (count == 0) {
                return;
            }
            StringBuilder text = new StringBuilder();
            if (!features.isEmpty()) {
                text.append("has ").append(features.stream().map(Feature::has)
                        .collect(Collectors.joining(" and ")));
            }
            if (redefinedSide) {
                text.append(features.isEmpty() ? "is" : " and is").append(" redefined by ")
                        .append(redefinedBy);
            }
            String unsupported = count > 1 ? "them"
                    : redefinedSide ? "REDEFINES" : features.get(0).unsupported();
            add(OpenDecisionKind.UNSUPPORTED_TYPE, node,
                    text + ". The runtime does not support " + unsupported + ".");
        }

        private static List<Feature> features(SpecNode node, Picture picture) {
            List<Feature> found = new ArrayList<>();
            boolean edited = node.edited() || picture != null && picture.editSymbols();
            if (edited) {
                found.add(new Feature("an edited picture", "edited pictures"));
            }
            if (node.blankWhenZero()) {
                found.add(new Feature("BLANK WHEN ZERO"));
            }
            if (node.occurs() != null) {
                found.add(new Feature("OCCURS"));
            }
            if (node.redefines() != null) {
                found.add(new Feature("REDEFINES"));
            }
            // An edited picture has its sign symbols in the picture, and the edited feature
            // covers them. Only a picture with S is a signed number.
            if (!edited && picture != null && picture.signed()) {
                found.add(new Feature("a signed picture", "signed pictures"));
            }
            if (EnumSet.of(Usage.COMP, Usage.COMP5).contains(node.usage())) {
                found.add(new Feature(node.usage() == Usage.COMP ? "USAGE COMP" : "USAGE COMP-5"));
            }
            if (DATE_TYPES.contains(node.jsonType())) {
                found.add(new Feature("the JSON type " + node.jsonType()));
            }
            return found;
        }

        /** A feature that the runtime does not support, as the text says it. */
        private record Feature(String has, String unsupported) {
            Feature(String name) {
                this(name, name);
            }
        }

        private void jsonName(SpecNode node, Set<String> jsonNames) {
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
                        "has the direction DERIVED. This version does not support DERIVED.");
            } else if (parent != null && !within(direction, parent)) {
                add(OpenDecisionKind.INVALID_DIRECTION, node, "has the direction " + direction
                        + ", which is outside the direction " + parent + " of its group.");
            }
        }

        private static boolean within(FieldDirection child, FieldDirection parent) {
            return (!child.inRequest() || parent.inRequest())
                    && (!child.inResponse() || parent.inResponse());
        }

        /** Checks the JSON type. A hidden item is not in the JSON, so it needs no type. */
        private void type(SpecNode node, Picture picture, boolean hidden) {
            if (node.isGroup()) {
                if (node.jsonType() != null) {
                    add(OpenDecisionKind.INVALID_TYPE, node, "is a group and has a JSON type.");
                }
            } else if (node.jsonType() == null) {
                if (!hidden) {
                    add(OpenDecisionKind.INVALID_TYPE, node, "has no JSON type.");
                }
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
