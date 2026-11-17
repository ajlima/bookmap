package pt.dazz.copymapper.copybook.spec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
                    derived.add(resolve(item.occurs().dependingOn(), items));
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

        private DataItem resolve(QualifiedName name, List<DataItem> candidates) {
            for (DataItem item : candidates) {
                if (item.name().equals(name.name()) && qualifiedBy(item, name.parts())) {
                    return item;
                }
            }
            throw new IllegalArgumentException("the name " + name + " is not in the record");
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
            Map<String, Integer> jsonSeen = new HashMap<>();
            for (DataItem child : item.children()) {
                children.add(build(child, usage, uniqueJson(child, jsonSeen)));
            }
            var picture = item.picture();
            var occurs = item.occurs();
            String dependingOn = occurs == null || occurs.dependingOn() == null ? null
                    : paths.get(resolve(occurs.dependingOn(), items));
            boolean fixedTable = occurs != null && occurs.dependingOn() == null;
            return new SpecNode(item.name(), item.level(), paths.get(item),
                    picture == null ? null : picture.text(), usage, sign(item, usage),
                    occurs == null ? null
                            : new OccursSpec(occurs.min(), occurs.max(), dependingOn),
                    item.redefines() == null ? null
                            : paths.get(resolve(item.redefines(), siblings(item))),
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

        private static String uniqueJson(DataItem child, Map<String, Integer> seen) {
            if (child.isFiller()) {
                return null;
            }
            String name = jsonName(child.name());
            int count = seen.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum);
            return count > 1 ? name + count : name;
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
