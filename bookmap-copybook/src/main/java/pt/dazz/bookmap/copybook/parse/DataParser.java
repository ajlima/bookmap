package pt.dazz.bookmap.copybook.parse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Map<String, Usage> USAGES = Map.of(
        "DISPLAY", Usage.DISPLAY,
        "COMP", Usage.COMP, "COMPUTATIONAL", Usage.COMP,
        "COMP-4", Usage.COMP, "COMPUTATIONAL-4", Usage.COMP, "BINARY", Usage.COMP,
        "COMP-3", Usage.COMP3, "COMPUTATIONAL-3", Usage.COMP3, "PACKED-DECIMAL", Usage.COMP3);

    private static final Set<String> UNSUPPORTED = Set.of(
        "LEADING", "SEPARATE", "SYNC", "SYNCHRONIZED", "JUSTIFIED", "JUST", "BLANK",
        "COMP-1", "COMP-2", "COMP-5", "COMPUTATIONAL-1", "COMPUTATIONAL-2", "COMPUTATIONAL-5",
        "INDEX", "POINTER", "NATIONAL");

    private static final Set<String> IGNORED = Set.of("INDEXED", "ASCENDING", "DESCENDING");

    /** The figurative constants, with the singular form of each. */
    private static final Map<String, String> FIGURATIVE = Map.ofEntries(
        Map.entry("SPACE", "SPACE"), Map.entry("SPACES", "SPACE"),
        Map.entry("ZERO", "ZERO"), Map.entry("ZEROS", "ZERO"), Map.entry("ZEROES", "ZERO"),
        Map.entry("HIGH-VALUE", "HIGH-VALUE"), Map.entry("HIGH-VALUES", "HIGH-VALUE"),
        Map.entry("LOW-VALUE", "LOW-VALUE"), Map.entry("LOW-VALUES", "LOW-VALUE"),
        Map.entry("QUOTE", "QUOTE"), Map.entry("QUOTES", "QUOTE"));

    private static final Set<String> CLAUSES = Set.of(
        "PIC", "PICTURE", "USAGE", "OCCURS", "REDEFINES", "VALUE", "VALUES", "SIGN", "TRAILING",
        "INDEXED", "ASCENDING", "DESCENDING");

    /** Mutable form of a data item while the tree grows. */
    private static final class Builder {
        int level;
        String name = DataItem.FILLER;
        Picture picture;
        Usage usage;
        Occurs occurs;
        QualifiedName redefines;
        Literal value;
        final List<Condition> conditions = new ArrayList<>();
        final List<Builder> children = new ArrayList<>();
        String source;
        int line;
        int column;
        final List<String> declaration = new ArrayList<>();
        boolean error;

        DataItem build() {
            return new DataItem(level, name, picture, usage, occurs, redefines, conditions,
                children.stream().map(Builder::build).toList(), source, line, column,
                String.join(" ", declaration) + ".", value);
        }
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
        records.forEach(this::check);
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
        } else if (level == 66 || level == 77) {
            error(first, "level " + level + " is not supported by BookMap");
            skipEntry();
        } else if (level < 1 || level > 49) {
            error(first, "level " + level + " is not valid");
            skipEntry();
        } else {
            item(first, level);
        }
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
            if (usage == null || usage.type() != TokenType.WORD
                || !USAGES.containsKey(usage.text()) && !UNSUPPORTED.contains(usage.text())) {
                throw fail(usage, "USAGE needs a usage word");
            }
            usage(item, usage);
        } else if (USAGES.containsKey(word)) {
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
        } else if (word.equals("SIGN") || word.equals("TRAILING")) {
            take(item);
            optional(item, "IS");
            optional(item, "TRAILING");
        } else if (IGNORED.contains(word)) {
            diagnostics.info(token.source(), token.line(), token.column(), word + " is ignored");
            take(item);
            skipUntilClause(item);
        } else if (UNSUPPORTED.contains(word)) {
            throw fail(token, word + " is not supported by BookMap");
        } else {
            throw fail(token, "unknown clause " + token.text());
        }
    }

    private void usage(Builder item, Token token) {
        if (UNSUPPORTED.contains(token.text())) {
            throw fail(token, token.text() + " is not supported by BookMap");
        }
        item.usage = USAGES.get(token.text());
        take(item);
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
        return CLAUSES.contains(word) || USAGES.containsKey(word) || UNSUPPORTED.contains(word);
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

    private void check(Builder item) {
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
        item.children.forEach(this::check);
    }
}
