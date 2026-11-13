package pt.dazz.bookmap.copybook.parse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import pt.dazz.bookmap.copybook.diag.Diagnostics;

/**
 * Replaces each {@code COPY} statement with the tokens of its member, after the
 * {@code REPLACING} changes. Then applies the {@code REPLACE} statements.
 */
final class CopyExpander {

    private static final List<String> EXTENSIONS = List.of("", ".cpy", ".cbl", ".cob");

    /** One {@code REPLACING} operand pair. */
    private record Replacement(List<Token> from, List<Token> to, String tag) {
    }

    /** The replacements of a {@code REPLACE} statement, and the index after its period. */
    private record ReplaceStatement(List<Replacement> replacements, int next) {
    }

    /** Thrown to stop one {@code COPY} statement after an error. */
    private static final class StatementError extends RuntimeException {
        StatementError() {
            super(null, null, false, false);
        }
    }

    private final ParseOptions options;
    private final Diagnostics diagnostics;

    private CopyExpander(ParseOptions options, Diagnostics diagnostics) {
        this.options = options;
        this.diagnostics = diagnostics;
    }

    /**
     * Expands the {@code COPY} statements, then applies the {@code REPLACE} statements.
     *
     * @param tokens the tokens of the copybook
     * @param rootName the member name of the copybook itself, for cycle detection
     * @param options the parse options
     * @param diagnostics the diagnostics of the step
     * @return the tokens without {@code COPY} and {@code REPLACE} statements
     */
    static List<Token> expand(List<Token> tokens, String rootName, ParseOptions options,
                              Diagnostics diagnostics) {
        List<String> stack = new ArrayList<>();
        stack.add(rootName);
        CopyExpander expander = new CopyExpander(options, diagnostics);
        return expander.replaceStatements(expander.expand(tokens, stack));
    }

    private List<Token> expand(List<Token> tokens, List<String> stack) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            Token token = tokens.get(i);
            if (token.is("COPY")) {
                i = copy(tokens, i, stack, out);
            } else {
                out.add(token);
                i++;
            }
        }
        return out;
    }

    /** Expands one {@code COPY} statement. Gives the index after its period. */
    private int copy(List<Token> tokens, int start, List<String> stack, List<Token> out) {
        Token copy = tokens.get(start);
        int i = start + 1;
        try {
            Token name = at(tokens, i, copy);
            if (name.type() != TokenType.WORD && name.type() != TokenType.LITERAL) {
                throw fail(name, "COPY needs a member name");
            }
            i++;
            if (at(tokens, i, copy).is("OF") || at(tokens, i, copy).is("IN")) {
                i += 2;
            }
            List<Replacement> replacements = new ArrayList<>();
            if (at(tokens, i, copy).is("REPLACING")) {
                i++;
                while (at(tokens, i, copy).type() != TokenType.PERIOD) {
                    Token from = tokens.get(i);
                    if (!at(tokens, i + 1, copy).is("BY")) {
                        throw fail(tokens.get(i + 1), "REPLACING needs BY");
                    }
                    Token to = at(tokens, i + 2, copy);
                    if (to.type() == TokenType.PERIOD) {
                        throw fail(to, "REPLACING needs a value after BY");
                    }
                    replacements.add(replacement(from, to));
                    i += 3;
                }
            }
            Token end = at(tokens, i, copy);
            if (end.type() != TokenType.PERIOD) {
                throw fail(end, "the COPY statement has an unknown word " + end.text());
            }
            include(copy, name.text().toUpperCase(Locale.ROOT), replacements, stack, out);
            return i + 1;
        } catch (StatementError e) {
            return afterPeriod(tokens, i);
        }
    }

    private Token at(List<Token> tokens, int index, Token statement) {
        if (index >= tokens.size()) {
            throw fail(statement, "the " + statement.text() + " statement has no closing period");
        }
        return tokens.get(index);
    }

    /**
     * Applies the {@code REPLACE} statements. Each statement applies to the tokens after it, up
     * to the next {@code REPLACE} statement.
     */
    private List<Token> replaceStatements(List<Token> tokens) {
        List<Token> out = new ArrayList<>();
        List<Replacement> active = List.of();
        int start = 0;
        int i = 0;
        while (i < tokens.size()) {
            if (tokens.get(i).is("REPLACE")) {
                out.addAll(replace(tokens.subList(start, i), active));
                ReplaceStatement statement = replaceStatement(tokens, i);
                active = statement.replacements();
                i = statement.next();
                start = i;
            } else {
                i++;
            }
        }
        out.addAll(replace(tokens.subList(Math.min(start, tokens.size()), tokens.size()),
            active));
        return out;
    }

    /** Reads one {@code REPLACE} statement. After an error, no replacement is active. */
    private ReplaceStatement replaceStatement(List<Token> tokens, int start) {
        Token replace = tokens.get(start);
        int i = start + 1;
        try {
            if (at(tokens, i, replace).is("OFF")) {
                i++;
            } else {
                List<Replacement> replacements = new ArrayList<>();
                do {
                    Token from = pseudoText(at(tokens, i, replace));
                    if (!at(tokens, i + 1, replace).is("BY")) {
                        throw fail(tokens.get(i + 1), "REPLACE needs BY");
                    }
                    Replacement replacement = replacement(from,
                        pseudoText(at(tokens, i + 2, replace)));
                    if (replacement.from().isEmpty()) {
                        throw fail(from, "REPLACE needs a text to replace");
                    }
                    replacements.add(replacement);
                    i += 3;
                } while (at(tokens, i, replace).type() != TokenType.PERIOD);
                return new ReplaceStatement(replacements, i + 1);
            }
            Token end = at(tokens, i, replace);
            if (end.type() != TokenType.PERIOD) {
                throw fail(end, "the REPLACE statement has an unknown word " + end.text());
            }
            return new ReplaceStatement(List.of(), i + 1);
        } catch (StatementError e) {
            return new ReplaceStatement(List.of(), afterPeriod(tokens, i));
        }
    }

    private Token pseudoText(Token token) {
        if (token.type() != TokenType.PSEUDO_TEXT) {
            throw fail(token, "REPLACE needs pseudo-text, for example REPLACE ==A== BY ==B==.");
        }
        return token;
    }

    private Replacement replacement(Token from, Token to) {
        List<Token> fromTokens = operand(from);
        // A tag such as :PREFIX: also replaces text inside words.
        String tag = fromTokens.size() == 1 && fromTokens.get(0).text().matches(":.+:")
            ? fromTokens.get(0).text() : null;
        return new Replacement(fromTokens, operand(to), tag);
    }

    /** Gives the tokens of an operand: the tokens of pseudo-text, or the token itself. */
    private List<Token> operand(Token token) {
        if (token.type() != TokenType.PSEUDO_TEXT) {
            return List.of(token);
        }
        SourceLine line = new SourceLine(token.source(), token.line(), token.column(),
            token.text(), false);
        return Lexer.tokenize(List.of(line), diagnostics);
    }

    private void include(Token copy, String member, List<Replacement> replacements,
                         List<String> stack, List<Token> out) {
        if (stack.contains(member)) {
            List<String> cycle = new ArrayList<>(stack.subList(stack.indexOf(member),
                stack.size()));
            cycle.add(member);
            throw fail(copy, "COPY cycle: " + String.join(" -> ", cycle));
        }
        Path file = find(member).orElseThrow(() -> fail(copy, options.copyDirs().isEmpty()
            ? "copy member '" + member + "' not found: no copy directory is set"
            : "copy member '" + member + "' not found in " + joinDirs()));
        String content;
        try {
            content = Files.readString(file, options.charset());
        } catch (IOException e) {
            throw fail(copy, "cannot read copy member " + member + ": " + e);
        }
        String source = file.getFileName().toString();
        List<Token> tokens = Lexer.tokenize(
            SourceReader.read(source, content, options.format(), diagnostics), diagnostics);
        List<String> inner = new ArrayList<>(stack);
        inner.add(member);
        out.addAll(expand(replace(tokens, replacements), inner));
    }

    private String joinDirs() {
        return String.join(", ", options.copyDirs().stream().map(Path::toString).toList());
    }

    private Optional<Path> find(String member) {
        for (Path dir : options.copyDirs()) {
            // list() gives null for a missing directory or one that cannot be read.
            String[] names = dir.toFile().list();
            Optional<String> found = names == null ? Optional.empty() : Arrays.stream(names)
                .filter(name -> EXTENSIONS.stream()
                    .anyMatch(extension -> name.equalsIgnoreCase(member + extension)))
                .sorted().findFirst();
            if (found.isPresent()) {
                return Optional.of(dir.resolve(found.get()));
            }
        }
        return Optional.empty();
    }

    /** Applies the replacements in one pass. A replaced text is not scanned again. */
    private static List<Token> replace(List<Token> tokens, List<Replacement> replacements) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            int matched = 0;
            for (Replacement replacement : replacements) {
                matched = apply(tokens, i, replacement, out);
                if (matched > 0) {
                    break;
                }
            }
            if (matched == 0) {
                out.add(tokens.get(i));
                matched = 1;
            }
            i += matched;
        }
        return out;
    }

    /** Applies one replacement at {@code index}. Gives the number of tokens it replaced. */
    private static int apply(List<Token> tokens, int index, Replacement replacement,
                             List<Token> out) {
        Token token = tokens.get(index);
        if (replacement.tag() != null) {
            if (token.type() != TokenType.WORD || !token.text().contains(replacement.tag())) {
                return 0;
            }
            String text = String.join(" ", replacement.to().stream().map(Token::text).toList());
            out.add(new Token(TokenType.WORD, token.text().replace(replacement.tag(), text),
                token.source(), token.line(), token.column()));
            return 1;
        }
        List<Token> from = replacement.from();
        if (index + from.size() > tokens.size()) {
            return 0;
        }
        for (int j = 0; j < from.size(); j++) {
            Token candidate = tokens.get(index + j);
            if (candidate.type() != from.get(j).type()
                || !candidate.text().equals(from.get(j).text())) {
                return 0;
            }
        }
        for (Token to : replacement.to()) {
            out.add(new Token(to.type(), to.text(), token.source(), token.line(),
                token.column()));
        }
        return from.size();
    }

    private static int afterPeriod(List<Token> tokens, int from) {
        int i = from;
        while (i < tokens.size() && tokens.get(i).type() != TokenType.PERIOD) {
            i++;
        }
        return i + 1;
    }

    private StatementError fail(Token token, String message) {
        error(token, message);
        return new StatementError();
    }

    private void error(Token token, String message) {
        diagnostics.error(token.source(), token.line(), token.column(), message);
    }
}
