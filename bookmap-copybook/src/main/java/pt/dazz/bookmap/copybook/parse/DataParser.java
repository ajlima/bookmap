package pt.dazz.bookmap.copybook.parse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pt.dazz.bookmap.annotation.Sign;
import pt.dazz.bookmap.copybook.ast.Condition;
import pt.dazz.bookmap.copybook.ast.ConditionValue;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.Literal;
import pt.dazz.bookmap.copybook.ast.Occurs;
import pt.dazz.bookmap.copybook.ast.QualifiedName;
import pt.dazz.bookmap.copybook.ast.Usage;
import pt.dazz.bookmap.copybook.diag.Diagnostics;

/**
 * Builds the tree of data items from the tokens of a copybook.
 */
public final class DataParser {

    private static final Map<String, Usage> USAGES = Map.ofEntries(
        Map.entry("DISPLAY", Usage.DISPLAY),
        Map.entry("COMP", Usage.COMP), Map.entry("COMPUTATIONAL", Usage.COMP),
        Map.entry("COMP-4", Usage.COMP), Map.entry("COMPUTATIONAL-4", Usage.COMP),
        Map.entry("BINARY", Usage.COMP),
        Map.entry("COMP-3", Usage.COMP3), Map.entry("COMPUTATIONAL-3", Usage.COMP3),
        Map.entry("PACKED-DECIMAL", Usage.COMP3),
        Map.entry("COMP-5", Usage.COMP5), Map.entry("COMPUTATIONAL-5", Usage.COMP5));

    /** The usage word of an index data item. It has no picture. */
    private static final String INDEX = "INDEX";

    /** The picture of an index data item: 4 bytes of signed binary data. */
    private static final String INDEX_PICTURE = "S9(9)";

    /** The unsupported words, with the end of their error message. */
    private static final Map<String, String> UNSUPPORTED = Map.of(
        "COMP-1", ". See issue #46.",
        "COMP-2", ". See issue #46.",
        "COMPUTATIONAL-1", ". See issue #46.",
        "COMPUTATIONAL-2", ". See issue #46.",
        "NATIONAL", ". See issue #49.",
        "POINTER", ": its size depends on the compiler option LP. Replace it with PIC X(4) or"
            + " PIC X(8), for example with the REPLACE statement.");

    private static final Set<String> IGNORED = Set.of("INDEXED", "ASCENDING", "DESCENDING");

    /** The figurative constants, with the singular form of each. */
    private static final Map<String, String> FIGURATIVE = Map.ofEntries(
        Map.entry("SPACE", "SPACE"), Map.entry("SPACES", "SPACE"),
        Map.entry("ZERO", "ZERO"), Map.entry("ZEROS", "ZERO"), Map.entry("ZEROES", "ZERO"),
        Map.entry("HIGH-VALUE", "HIGH-VALUE"), Map.entry("HIGH-VALUES", "HIGH-VALUE"),
        Map.entry("LOW-VALUE", "LOW-VALUE"), Map.entry("LOW-VALUES", "LOW-VALUE"),
        Map.entry("QUOTE", "QUOTE"), Map.entry("QUOTES", "QUOTE"));

    private static final Set<String> CLAUSES = Set.of(
        "PIC", "PICTURE", "USAGE", "OCCURS", "REDEFINES", "VALUE", "VALUES", "SIGN", "LEADING",
        "TRAILING",
        "INDEXED", "ASCENDING", "DESCENDING", "SYNC", "SYNCHRONIZED", "JUSTIFIED", "JUST",
        "BLANK", INDEX);

    private static final Set<String> ZEROS = Set.of("ZERO", "ZEROS", "ZEROES");

    /** The halfword boundary of a binary item with 1 to 4 digits. */
    private static final int HALFWORD = 2;

    /** The fullword boundary of a binary item with 5 to 18 digits. */
    private static final int FULLWORD = 4;

    /** The maximum number of digits of a binary item on a halfword boundary. */
    private static final int HALFWORD_DIGITS = 4;

    /** Mutable form of a data item while the tree grows. */
    private static final class Builder {
        int level;
        String name = DataItem.FILLER;
        Picture picture;
        Usage usage;
        Occurs occurs;
        QualifiedName redefines;
        Literal value;
        /** The own {@code SIGN} clause, or {@code null}. */
        Sign signClause;
        /** The effective sign of an elementary item, or {@code null} for the default. */
        Sign sign;
        final List<Condition> conditions = new ArrayList<>();
        final List<Builder> children = new ArrayList<>();
        String source;
        int line;
        int column;
        final List<String> declaration = new ArrayList<>();
        boolean error;
        boolean sync;
        boolean index;
        boolean synthetic;

        DataItem build() {
            return new DataItem(level, name, picture, usage, occurs, redefines, conditions,
                children.stream().map(Builder::build).toList(), source, line, column,
                String.join(" ", declaration) + ".", value, sign);
        }
    }

    /** A {@code SYNC} item in a table, with its offset in the table entry. */
    private record Slot(Builder item, int boundary, int offset) {
    }

    /** The offset of the {@code OCCURS} item that contains a layout, and its SYNC items. */
    private record Table(int offset, List<Slot> slots) {
    }

    /** Thrown to stop the clauses of one entry after an error. */
    private static final class EntryError extends RuntimeException {
        EntryError() {
            super(null, null, false, false);
        }
    }

    private final String sourceName;
    private final List<Token> tokens;
    private final String recordName;
    private final Diagnostics diagnostics;
    private final List<Builder> records = new ArrayList<>();
    private final Deque<Builder> stack = new ArrayDeque<>();
    private Builder last;
    private int pos;

    private DataParser(String sourceName, List<Token> tokens, String recordName,
                       Diagnostics diagnostics) {
        this.sourceName = sourceName;
        this.tokens = tokens;
        this.recordName = recordName;
        this.diagnostics = diagnostics;
    }

    /**
     * Parses the data description entries of a copybook.
     *
     * @param sourceName the source name
     * @param tokens the tokens, after {@code COPY} expansion
     * @param recordName the name of the synthetic record for a copybook without level 01
     * @param diagnostics the diagnostics of the step
     * @return the copybook
     */
    public static Copybook parse(String sourceName, List<Token> tokens, String recordName,
                                 Diagnostics diagnostics) {
        DataParser parser = new DataParser(sourceName, tokens, recordName, diagnostics);
        parser.entries();
        return new Copybook(sourceName, parser.records.stream().map(Builder::build).toList());
    }

    private void entries() {
        while (pos < tokens.size()) {
            entry();
        }
        if (records.isEmpty()) {
            diagnostics.error(sourceName, 1, 1, "the copybook has no data items");
        }
        records.forEach(record -> check(record, false, Usage.DISPLAY, Sign.TRAILING));
        if (!diagnostics.hasErrors()) {
            records.forEach(record -> layout(record, Usage.DISPLAY, false, 0, null, record));
        }
    }

    private void entry() {
        Token first = tokens.get(pos);
        Integer level = first.type() == TokenType.NUMBER ? levelOf(first.text()) : null;
        if (level == null) {
            error(first, "an entry must start with a level number");
            skipEntry();
            return;
        }
        pos++;
        if (level == 88) {
            condition(first);
        } else if (level == 66) {
            diagnostics.info(first.source(), first.line(), first.column(),
                "level 66 item " + nameAt(pos) + " is ignored. RENAMES adds no bytes.");
            skipEntry();
        } else if (level == 77) {
            diagnostics.warning(first.source(), first.line(), first.column(),
                "level 77 item " + nameAt(pos) + " is not part of a record and is skipped");
            stack.clear();
            // A level 88 after the level 77 item attaches to the skipped item.
            last = new Builder();
            skipEntry();
        } else if (level < 1 || level > 49) {
            error(first, "level " + level + " is not valid");
            skipEntry();
        } else {
            item(first, level);
        }
    }

    /** Gives the text of the token at an index, or an empty text after the last token. */
    private String nameAt(int index) {
        return index < tokens.size() ? tokens.get(index).text() : "";
    }

    private static Integer levelOf(String text) {
        return text.chars().allMatch(Character::isDigit) ? Integer.valueOf(text) : null;
    }

    private void condition(Token level) {
        Token name = peek();
        if (last == null) {
            error(level, "a level 88 condition needs an item before it");
        } else if (name != null) {
            pos++;
            List<ConditionValue> values = conditionValues(name);
            if (values != null) {
                last.conditions.add(new Condition(name.text(), values, level.line(),
                    level.column()));
            }
        }
        skipEntry();
    }

    /**
     * Reads the {@code VALUE} clause of a condition, up to the period. Gives null and a
     * warning if the clause is missing or has a value that BookMap does not support.
     */
    private List<ConditionValue> conditionValues(Token name) {
        if (!skipWords("VALUE", "VALUES")) {
            warning(name, "condition " + name.text() + " has no VALUE clause and is ignored");
            return null;
        }
        skipWords("IS", "ARE");
        List<ConditionValue> values = new ArrayList<>();
        Token token;
        while ((token = peek()) != null && token.type() != TokenType.PERIOD) {
            Literal from = literal(token);
            if (from == null) {
                return unsupported(name, token);
            }
            pos++;
            Literal thru = null;
            if (skipWords("THRU", "THROUGH")) {
                // At the end of the tokens, the THRU word itself is the value that fails.
                Token bound = tokens.get(Math.min(pos, tokens.size() - 1));
                thru = literal(bound);
                if (thru == null) {
                    return unsupported(name, bound);
                }
                pos++;
            }
            values.add(new ConditionValue(from, thru));
        }
        return values;
    }

    private List<ConditionValue> unsupported(Token name, Token value) {
        warning(value, "condition " + name.text() + ": the value " + value.text()
            + " is not supported. The condition is ignored.");
        return null;
    }

    /** Skips the next token if it is one of the words. */
    private boolean skipWords(String... words) {
        Token token = peek();
        for (String word : words) {
            if (token != null && token.is(word)) {
                pos++;
                return true;
            }
        }
        return false;
    }

    /** Gives the literal of a token, or null if BookMap does not support it. */
    private static Literal literal(Token token) {
        return switch (token.type()) {
            case LITERAL -> new Literal(Literal.Kind.TEXT, token.text());
            case NUMBER -> new Literal(Literal.Kind.NUMBER, token.text());
            case WORD -> {
                String figurative = FIGURATIVE.get(token.text());
                yield figurative == null ? null
                    : new Literal(Literal.Kind.FIGURATIVE, figurative);
            }
            default -> null;
        };
    }

    private void item(Token levelToken, int level) {
        if (level > 1 && !records.isEmpty() && stack.isEmpty()) {
            error(levelToken, "level " + levelToken.text() + " item " + nameAt(pos)
                + " must follow a level 01 item, not a level 77 item");
            last = new Builder();
            skipEntry();
            return;
        }
        Builder item = new Builder();
        item.level = level;
        item.source = levelToken.source();
        item.line = levelToken.line();
        item.column = levelToken.column();
        item.declaration.add(levelToken.text());
        place(item);
        Token next = peek();
        if (next != null && next.type() == TokenType.WORD && !isClause(next.text())) {
            item.name = next.text();
            item.declaration.add(next.text());
            pos++;
        }
        try {
            clauses(item);
        } catch (EntryError e) {
            item.error = true;
            skipEntry();
        }
    }

    /** Adds an item to the tree, with a synthetic record if the first item is not level 01. */
    private void place(Builder item) {
        if (item.level == 1) {
            stack.clear();
            records.add(item);
        } else {
            if (records.isEmpty()) {
                Builder record = new Builder();
                record.level = 1;
                record.name = recordName;
                record.source = item.source;
                record.line = item.line;
                record.column = item.column;
                record.declaration.add("01");
                record.declaration.add(recordName);
                record.synthetic = true;
                records.add(record);
                stack.push(record);
            }
            while (stack.peek().level >= item.level) {
                stack.pop();
            }
            stack.peek().children.add(item);
        }
        stack.push(item);
        last = item;
    }

    private void clauses(Builder item) {
        while (true) {
            Token token = peek();
            if (token == null) {
                error(tokens.get(pos - 1), "the entry has no closing period");
                return;
            }
            if (token.type() == TokenType.PERIOD) {
                pos++;
                return;
            }
            clause(item, token);
        }
    }

    private void clause(Builder item, Token token) {
        String word = token.type() == TokenType.WORD ? token.text() : "";
        if (word.equals("PIC") || word.equals("PICTURE")) {
            take(item);
            optional(item, "IS");
            Token picture = expect(TokenType.PICTURE, "PIC needs a picture string");
            try {
                item.picture = Picture.parse(picture.text());
            } catch (IllegalArgumentException e) {
                throw fail(picture, e.getMessage());
            }
            item.declaration.add(picture.text());
        } else if (word.equals("USAGE")) {
            take(item);
            optional(item, "IS");
            Token usage = peek();
            if (usage == null || usage.type() != TokenType.WORD || !isUsage(usage.text())) {
                throw fail(usage, "USAGE needs a usage word");
            }
            usage(item, usage);
        } else if (isUsage(word)) {
            // An unsupported word also comes here and gives an error.
            usage(item, token);
        } else if (word.equals("OCCURS")) {
            occurs(item);
        } else if (word.equals("REDEFINES")) {
            take(item);
            Token name = peek();
            if (name == null || name.type() != TokenType.WORD || isClause(name.text())) {
                throw fail(name, "REDEFINES needs a data name");
            }
            item.redefines = new QualifiedName(List.of(take(item).text()));
        } else if (word.equals("VALUE") || word.equals("VALUES")) {
            pos++;
            skipWords("IS");
            Token value = peek();
            item.value = value == null ? null : literal(value);
            if (item.value == null) {
                warning(value == null ? token : value, "the VALUE clause of " + item.name
                    + " is not supported and is ignored");
            }
            skipUntilClause(null);
        } else if (word.equals("SIGN") || word.equals("LEADING") || word.equals("TRAILING")) {
            sign(item);
        } else if (word.equals("SYNC") || word.equals("SYNCHRONIZED")) {
            take(item);
            item.sync = true;
            if (!optional(item, "LEFT")) {
                optional(item, "RIGHT");
            }
        } else if (word.equals("JUSTIFIED") || word.equals("JUST")) {
            take(item);
            optional(item, "RIGHT");
            diagnostics.warning(token.source(), token.line(), token.column(), "JUSTIFIED on "
                + item.name + " is ignored. BookMap writes the text on the left and keeps the"
                + " spaces on the left when it reads.");
        } else if (word.equals("BLANK")) {
            blankWhenZero(item, token);
        } else if (IGNORED.contains(word)) {
            diagnostics.info(token.source(), token.line(), token.column(), word + " is ignored");
            take(item);
            skipUntilClause(item);
        } else {
            throw fail(token, "unknown clause " + token.text());
        }
    }

    private static boolean isUsage(String word) {
        return USAGES.containsKey(word) || word.equals(INDEX) || UNSUPPORTED.containsKey(word);
    }

    private void usage(Builder item, Token token) {
        if (UNSUPPORTED.containsKey(token.text())) {
            throw unsupported(token);
        }
        if (token.is(INDEX)) {
            item.index = true;
        } else {
            item.usage = USAGES.get(token.text());
        }
        take(item);
    }

    private EntryError unsupported(Token token) {
        return fail(token, token.text() + " is not supported by BookMap"
            + UNSUPPORTED.get(token.text()));
    }

    /** Reads {@code [SIGN [IS]] {LEADING | TRAILING} [SEPARATE [CHARACTER]]}. */
    private void sign(Builder item) {
        if (optional(item, "SIGN")) {
            optional(item, "IS");
        }
        boolean leading = optional(item, "LEADING");
        if (!leading && !optional(item, "TRAILING")) {
            throw fail(peek(), "SIGN needs LEADING or TRAILING");
        }
        boolean separate = optional(item, "SEPARATE");
        if (separate) {
            optional(item, "CHARACTER");
        }
        item.signClause = leading
            ? separate ? Sign.LEADING_SEPARATE : Sign.LEADING
            : separate ? Sign.TRAILING_SEPARATE : Sign.TRAILING;
    }

    private void blankWhenZero(Builder item, Token blank) {
        take(item);
        optional(item, "WHEN");
        Token zero = peek();
        if (zero == null || !ZEROS.contains(zero.text())) {
            throw fail(zero, "BLANK needs WHEN ZERO");
        }
        take(item);
        diagnostics.info(blank.source(), blank.line(), blank.column(), "BLANK WHEN ZERO on "
            + item.name + " is ignored. BookMap reads spaces as no value and writes zero as"
            + " digits.");
    }

    private void occurs(Builder item) {
        take(item);
        int min = number(item, "OCCURS needs a number");
        int max = min;
        if (optional(item, "TO")) {
            max = number(item, "OCCURS needs a number");
        }
        optional(item, "TIMES");
        QualifiedName dependingOn = null;
        if (optional(item, "DEPENDING")) {
            optional(item, "ON");
            List<String> parts = new ArrayList<>();
            parts.add(dataName(item));
            while (optional(item, "OF") || optional(item, "IN")) {
                parts.add(dataName(item));
            }
            dependingOn = new QualifiedName(parts);
        }
        item.occurs = new Occurs(min, max, dependingOn);
    }

    private String dataName(Builder item) {
        Token name = peek();
        if (name == null || name.type() != TokenType.WORD || isClause(name.text())) {
            throw fail(name, "DEPENDING ON needs a data name");
        }
        return take(item).text();
    }

    private int number(Builder item, String message) {
        Token token = expect(TokenType.NUMBER, message);
        item.declaration.add(token.text());
        return Integer.parseInt(token.text());
    }

    // ---- token helpers ----

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private Token take(Builder item) {
        Token token = tokens.get(pos++);
        item.declaration.add(token.text());
        return token;
    }

    private boolean optional(Builder item, String word) {
        Token token = peek();
        if (token != null && token.is(word)) {
            take(item);
            return true;
        }
        return false;
    }

    private Token expect(TokenType type, String message) {
        Token token = peek();
        if (token == null || token.type() != type) {
            throw fail(token, message);
        }
        pos++;
        return token;
    }

    /** Skips tokens up to the next clause word or period. Adds them to the declaration. */
    private void skipUntilClause(Builder item) {
        Token token = peek();
        while (token != null && token.type() != TokenType.PERIOD
            && !(token.type() == TokenType.WORD && isClause(token.text()))) {
            if (item != null) {
                item.declaration.add(token.text());
            }
            pos++;
            token = peek();
        }
    }

    private void skipEntry() {
        while (pos < tokens.size() && tokens.get(pos++).type() != TokenType.PERIOD) {
            // skip
        }
    }

    private static boolean isClause(String word) {
        return CLAUSES.contains(word) || isUsage(word);
    }

    private EntryError fail(Token token, String message) {
        error(token == null ? tokens.get(tokens.size() - 1) : token, message);
        return new EntryError();
    }

    private void error(Token token, String message) {
        diagnostics.error(token.source(), token.line(), token.column(), message);
    }

    private void warning(Token token, String message) {
        diagnostics.warning(token.source(), token.line(), token.column(), message);
    }

    // ---- checks after the tree is complete ----

    private void check(Builder item, boolean inheritedIndex, Usage inheritedUsage,
                       Sign inheritedSign) {
        boolean index = item.index || inheritedIndex && item.usage == null;
        if (index && item.children.isEmpty() && !item.error) {
            if (item.picture != null) {
                diagnostics.error(item.source, item.line, item.column,
                    "the INDEX item " + item.name + " must not have a PIC");
            } else {
                item.picture = Picture.parse(INDEX_PICTURE);
                item.usage = Usage.COMP;
                diagnostics.info(item.source, item.line, item.column, "INDEX item " + item.name
                    + " is mapped as PIC " + INDEX_PICTURE + " COMP. Its value is a byte offset"
                    + " in a table.");
            }
        }
        if (item.level == 1 && item.redefines != null) {
            diagnostics.warning(item.source, item.line, item.column, "record " + item.name
                + " redefines record " + item.redefines + ". Both records are kept.");
            item.redefines = null;
        }
        if (!item.children.isEmpty() && item.picture != null) {
            diagnostics.error(item.source, item.line, item.column,
                item.name + " is a group and must not have a PIC");
        } else if (item.children.isEmpty() && item.picture == null && !item.error) {
            diagnostics.error(item.source, item.line, item.column, item.name + " has no PIC");
        }
        Usage usage = item.usage != null ? item.usage : inheritedUsage;
        Sign sign = item.signClause != null ? item.signClause : inheritedSign;
        if (item.children.isEmpty() && item.picture != null) {
            resolveSign(item, usage, sign);
        }
        item.children.forEach(child -> check(child, index, usage, sign));
    }

    /**
     * Sets the effective sign of an elementary item. A sign applies only to a signed numeric
     * {@code DISPLAY} item. A group clause does not change the other items.
     */
    private void resolveSign(Builder item, Usage usage, Sign sign) {
        boolean applies = item.picture.signed() && usage == Usage.DISPLAY;
        if (item.signClause != null && !applies) {
            diagnostics.error(item.source, item.line, item.column,
                "SIGN on " + item.name + " needs a signed numeric DISPLAY item");
        }
        item.sign = applies && sign != Sign.TRAILING ? sign : null;
    }

    // ---- SYNC: slack bytes ----

    /**
     * Calculates the offsets of the items, from the start of the record, and gives the length
     * of an item. Adds a slack {@code FILLER} before each {@code SYNC} binary item that is not
     * on its boundary, as the compiler does.
     */
    private int layout(Builder item, Usage inherited, boolean inheritedSync, int offset,
                       Table table, Builder record) {
        Usage usage = item.usage != null ? item.usage : inherited;
        boolean sync = inheritedSync || item.sync;
        if (item.children.isEmpty()) {
            int single = item.picture.byteLength(usage, item.sign);
            return item.occurs == null ? single : single * item.occurs.max();
        }
        Table inner = table == null && item.occurs != null
            ? new Table(offset, new ArrayList<>()) : table;
        int single = layoutChildren(item, usage, sync, offset, inner, record);
        if (inner != table) {
            for (Slot slot : inner.slots()) {
                if ((offset + slot.offset()) % slot.boundary() != 0
                    || single % slot.boundary() != 0) {
                    diagnostics.error(slot.item().source, slot.item().line, slot.item().column,
                        "SYNC on " + slot.item().name + " in OCCURS " + item.name + " needs"
                            + " slack bytes. BookMap cannot add them in a table. Align the item"
                            + " by hand.");
                }
            }
        }
        return item.occurs == null ? single : single * item.occurs.max();
    }

    private int layoutChildren(Builder group, Usage usage, boolean sync, int offset,
                               Table table, Builder record) {
        Map<String, Integer> starts = new HashMap<>();
        int end = 0;
        for (int i = 0; i < group.children.size(); i++) {
            Builder child = group.children.get(i);
            Integer redefined = child.redefines == null ? null
                : starts.get(child.redefines.name());
            int start = redefined != null ? redefined : end;
            int boundary = boundary(child, usage, sync);
            if (boundary > 0 && record.synthetic) {
                diagnostics.warning(record.source, record.line, record.column, "the copybook"
                    + " has no level 01. SYNC assumes that " + record.name + " starts on a"
                    + " doubleword boundary.");
                record.synthetic = false;
            }
            if (boundary > 0 && table != null) {
                table.slots().add(new Slot(child, boundary, offset + start - table.offset()));
            } else if (boundary > 0) {
                int slack = (boundary - (offset + start) % boundary) % boundary;
                if (slack > 0 && child.redefines != null) {
                    diagnostics.error(child.source, child.line, child.column, "SYNC on "
                        + child.name + " needs slack bytes, but " + child.name
                        + " has REDEFINES. Align the item by hand.");
                } else if (slack > 0) {
                    group.children.add(i++, slackFiller(child, slack));
                    start += slack;
                }
            }
            int length = layout(child, usage, sync, offset + start, table, record);
            if (child.redefines == null) {
                starts.put(child.name, start);
                end = start + length;
            } else {
                end = Math.max(end, start + length);
            }
        }
        return end;
    }

    /** Gives the boundary of a {@code SYNC} binary item, or 0 if {@code SYNC} has no effect. */
    private static int boundary(Builder item, Usage inherited, boolean inheritedSync) {
        Usage usage = item.usage != null ? item.usage : inherited;
        if (!(inheritedSync || item.sync) || !item.children.isEmpty()
            || usage != Usage.COMP && usage != Usage.COMP5 || !item.picture.numeric()) {
            return 0;
        }
        return item.picture.length() <= HALFWORD_DIGITS ? HALFWORD : FULLWORD;
    }

    private Builder slackFiller(Builder item, int slack) {
        Builder filler = new Builder();
        filler.level = item.level;
        filler.picture = Picture.parse("X(" + slack + ")");
        filler.usage = Usage.DISPLAY;
        filler.source = item.source;
        filler.line = item.line;
        filler.column = item.column;
        filler.declaration.add(item.declaration.get(0));
        filler.declaration.add(DataItem.FILLER);
        filler.declaration.add("PIC");
        filler.declaration.add("X(" + slack + "). Slack bytes before " + item.name + " for SYNC");
        diagnostics.info(item.source, item.line, item.column, "SYNC adds " + slack
            + (slack == 1 ? " slack byte" : " slack bytes") + " before " + item.name
            + ". The generator adds a FILLER.");
        return filler;
    }
}
