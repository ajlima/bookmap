package pt.dazz.copymapper.copybook.spec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.ast.Condition;
import pt.dazz.copymapper.copybook.ast.ConditionValue;
import pt.dazz.copymapper.copybook.ast.Copybook;
import pt.dazz.copymapper.copybook.ast.DataItem;
import pt.dazz.copymapper.copybook.ast.Literal;
import pt.dazz.copymapper.copybook.ast.QualifiedName;
import pt.dazz.copymapper.copybook.ast.Usage;
import pt.dazz.copymapper.copybook.map.Names;

/** Drafts a {@link MappingSpec} from the AST of a copybook record. */
public final class MappingSpecs {

    private MappingSpecs() {
    }

    /**
     * Drafts the mapping spec of one record, with the default REST side.
     *
     * @param copybook the parsed copybook
     * @param recordName the name of the record, in any case
     * @param options the options
     * @return the draft
     * @throws IllegalArgumentException if no record, or more than one, has the name
     */
    public static MappingSpec draft(Copybook copybook, String recordName,
            DraftOptions options) {
        DataItem record = select(copybook, recordName);
        Draft draft = new Draft(record);
        return new MappingSpec(MappingSpec.FORMAT_VERSION, copybook.sourceName(),
                draft.sourceHash(), options.charset(), MappingSpec.BIG_ENDIAN,
                draft.build(record, Usage.DISPLAY, Draft.jsonName(record.name())));
    }

    private static DataItem select(Copybook copybook, String recordName) {
        List<DataItem> found = copybook.records().stream()
                .filter(r -> r.name().equalsIgnoreCase(recordName)).toList();
        if (found.size() != 1) {
            String names = copybook.records().stream().map(DataItem::name)
                    .collect(Collectors.joining(", "));
            String problem = found.isEmpty() ? "no record" : "more than one record";
            throw new IllegalArgumentException(problem
                    + " is named " + recordName + ". The records are: " + names);
        }
        return found.get(0);
    }

    /** The state of one draft: the items of the record, their parents, and the counters. */
    private static final class Draft {

        private static final Names NAMES = new Names(List.of());

        private final List<DataItem> items = new ArrayList<>();
        private final Map<DataItem, DataItem> parents = new IdentityHashMap<>();
        private final Map<DataItem, String> paths = new IdentityHashMap<>();
        private final Set<DataItem> derived = Collections.newSetFromMap(
                new IdentityHashMap<>());

        Draft(DataItem record) {
            collect(record, null, record.name());
            for (DataItem item : items) {
                if (item.occurs() != null && item.occurs().dependingOn() != null) {
                    DataItem counter = resolve(item.occurs().dependingOn(), items);
                    if (counter != null) {
                        derived.add(counter);
                    }
                }
            }
        }

        private void collect(DataItem item, DataItem parent, String path) {
            items.add(item);
            paths.put(item, path);
            if (parent != null) {
                parents.put(item, parent);
            }
            Map<String, Integer> seen = new HashMap<>();
            for (DataItem child : item.children()) {
                int count = seen.merge(child.name(), 1, Integer::sum);
                collect(child, item, path + "." + child.name() + (count > 1 ? "#" + count : ""));
            }
        }

        String sourceHash() {
            String text = items.stream().map(DataItem::declaration)
                    .collect(Collectors.joining("\n"));
            MessageDigest digest = MappingSpecJson.digest("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        }

        /** Gives the item that the name designates, or null if the record has none. */
        private DataItem resolve(QualifiedName name, List<DataItem> candidates) {
            for (DataItem item : candidates) {
                if (item.name().equals(name.name()) && qualifiedBy(item, name.parts())) {
                    return item;
                }
            }
            return null;
        }

        private boolean qualifiedBy(DataItem item, List<String> parts) {
            DataItem ancestor = parents.get(item);
            for (String part : parts.subList(1, parts.size())) {
                while (ancestor != null && !ancestor.name().equals(part)) {
                    ancestor = parents.get(ancestor);
                }
                if (ancestor == null) {
                    return false;
                }
                ancestor = parents.get(ancestor);
            }
            return true;
        }

        SpecNode build(DataItem item, Usage inherited, String jsonName) {
            Usage usage = item.usage() != null ? item.usage() : inherited;
            List<SpecNode> children = new ArrayList<>();
            List<String> jsonNames = jsonNames(item.children());
            for (int i = 0; i < jsonNames.size(); i++) {
                children.add(build(item.children().get(i), usage, jsonNames.get(i)));
            }
            var picture = item.picture();
            var occurs = item.occurs();
            String dependingOn = occurs == null || occurs.dependingOn() == null ? null
                    : pathOf(occurs.dependingOn(), items);
            boolean fixedTable = occurs != null && occurs.dependingOn() == null;
            return new SpecNode(item.name(), item.level(), paths.get(item),
                    picture == null ? null : picture.text(), usage, sign(item, usage),
                    occurs == null ? null
                            : new OccursSpec(occurs.min(), occurs.max(), dependingOn),
                    item.redefines() == null ? null
                            : pathOf(item.redefines(), siblings(item)),
                    item.value() == null ? null : value(item.value()),
                    item.conditions().stream().map(Draft::condition).toList(),
                    new SourceLocation(item.source(), item.line(), item.column()),
                    picture != null && picture.edited() != null && !picture.editSymbols(),
                    picture != null && picture.editSymbols(), jsonName, direction(item),
                    jsonType(item), null,
                    fixedTable ? new TableSpec(TableMode.LOW_VALUES, null) : null,
                    dependingOn, null, null, children);
        }

        private List<DataItem> siblings(DataItem item) {
            DataItem parent = parents.get(item);
            return items.stream().filter(other -> parents.get(other) == parent).toList();
        }

        static String jsonName(String cobolName) {
            return NAMES.fieldName(cobolName);
        }

        /** Gives the path of the named item, or the name as written if it is not found. */
        private String pathOf(QualifiedName name, List<DataItem> candidates) {
            DataItem found = resolve(name, candidates);
            return found == null ? name.toString() : paths.get(found);
        }

        /**
         * Gives the JSON names of sibling items: null for a FILLER. The first item with a
         * name keeps it. A later item with the same name gets the number 2, 3, ... , the
         * first that no sibling uses or has as its natural name.
         */
        private static List<String> jsonNames(List<DataItem> siblings) {
            Set<String> taken = new HashSet<>();
            for (DataItem sibling : siblings) {
                if (!sibling.isFiller()) {
                    taken.add(jsonName(sibling.name()).toLowerCase(Locale.ROOT));
                }
            }
            Set<String> used = new HashSet<>();
            List<String> names = new ArrayList<>();
            for (DataItem sibling : siblings) {
                names.add(sibling.isFiller() ? null : unique(jsonName(sibling.name()), taken, used));
            }
            return names;
        }

        private static String unique(String natural, Set<String> taken, Set<String> used) {
            String name = natural;
            for (int n = 2; used.contains(name.toLowerCase(Locale.ROOT)); n++) {
                String candidate = natural + n;
                name = taken.contains(candidate.toLowerCase(Locale.ROOT)) ? name : candidate;
            }
            used.add(name.toLowerCase(Locale.ROOT));
            return name;
        }

        private static Sign sign(DataItem item, Usage usage) {
            if (item.sign() != null) {
                return item.sign();
            }
            boolean signed = item.picture() != null && item.picture().signed();
            return signed && usage == Usage.DISPLAY ? Sign.TRAILING : null;
        }

        private FieldDirection direction(DataItem item) {
            if (item.isFiller()) {
                return FieldDirection.IGNORE;
            }
            return derived.contains(item) ? FieldDirection.DERIVED : FieldDirection.INPUT_OUTPUT;
        }

        private static JsonType jsonType(DataItem item) {
            if (item.picture() == null) {
                return null;
            }
            return item.picture().numeric() ? JsonType.NUMBER : JsonType.TEXT;
        }

        private static ValueSpec value(Literal literal) {
            return new ValueSpec(literal.kind().name(), literal.text());
        }

        private static ConditionSpec condition(Condition condition) {
            return new ConditionSpec(condition.name(),
                    condition.values().stream().map(Draft::range).toList());
        }

        private static ConditionRange range(ConditionValue value) {
            return new ConditionRange(value(value.from()),
                    value.thru() == null ? null : value(value.thru()));
        }
    }
}
