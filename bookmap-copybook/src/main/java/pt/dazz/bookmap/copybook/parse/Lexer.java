package pt.dazz.bookmap.copybook.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import pt.dazz.bookmap.copybook.diag.Diagnostics;

/**
 * Splits the code lines of a copybook into tokens.
 */
public final class Lexer {

    /** The width of the code area in fixed format (columns 8 to 72). */
    private static final int FIXED_WIDTH = 65;
    private static final int FIXED_COLUMN = 8;
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

    private final Diagnostics diagnostics;
    private final List<Token> tokens = new ArrayList<>();
    private boolean expectPicture;
    /** An open literal or pseudo-text that continues on the next line. */
    private StringBuilder open;
    private char quote;
    private Token start;

    private Lexer(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Splits lines into tokens.
     *
     * @param lines the code lines
     * @param diagnostics the diagnostics of the step
     * @return the tokens
     */
    public static List<Token> tokenize(List<SourceLine> lines, Diagnostics diagnostics) {
        Lexer lexer = new Lexer(diagnostics);
        for (SourceLine line : lines) {
            lexer.line(line);
        }
        lexer.closeOpen();
        return lexer.tokens;
    }

    private void line(SourceLine line) {
        String text = line.text();
        int i = 0;
        if (open != null && quote != '=') {
            int resume = line.continuation() ? text.indexOf(quote) : -1;
            if (resume < 0) {
                closeOpen();
            } else {
                i = literal(line, resume + 1);
            }
        } else if (open != null) {
            open.append(' ');
            i = pseudoText(line, 0);
        }
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == ',' || c == ';') {
                i++;
            } else if (text.startsWith("==", i)) {
                start = token(TokenType.PSEUDO_TEXT, "", line, i);
                open = new StringBuilder();
                quote = '=';
                i = pseudoText(line, i + 2);
            } else if (c == '\'' || c == '"') {
                start = token(TokenType.LITERAL, "", line, i);
                open = new StringBuilder();
                quote = c;
                i = literal(line, i + 1);
            } else if (c == '.' && endsWord(text, i + 1)) {
                tokens.add(token(TokenType.PERIOD, ".", line, i));
                expectPicture = false;
                i++;
            } else {
                i = word(line, i);
            }
        }
    }

    private int word(SourceLine line, int from) {
        String text = line.text();
        int end = from;
        while (end < text.length() && text.charAt(end) != ' '
            && !(text.charAt(end) == '.' && endsWord(text, end + 1))
            && text.charAt(end) != ',' && text.charAt(end) != ';') {
            end++;
        }
        String value = text.substring(from, end);
        if (expectPicture && !value.equalsIgnoreCase("IS")) {
            tokens.add(token(TokenType.PICTURE, value.toUpperCase(Locale.ROOT), line, from));
            expectPicture = false;
        } else if (NUMBER.matcher(value).matches()) {
            tokens.add(token(TokenType.NUMBER, value, line, from));
        } else {
            String upper = value.toUpperCase(Locale.ROOT);
            tokens.add(token(TokenType.WORD, upper, line, from));
            expectPicture = expectPicture || upper.equals("PIC") || upper.equals("PICTURE");
        }
        return end;
    }

    /** Reads literal characters from {@code from}. Gives the index after the closing quote. */
    private int literal(SourceLine line, int from) {
        String text = line.text();
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == quote && i + 1 < text.length() && text.charAt(i + 1) == quote) {
                open.append(c);
                i += 2;
            } else if (c == quote) {
                tokens.add(new Token(TokenType.LITERAL, open.toString(), start.source(),
                    start.line(), start.column()));
                open = null;
                return i + 1;
            } else {
                open.append(c);
                i++;
            }
        }
        if (line.column() == FIXED_COLUMN) {
            // In fixed format, a literal that continues on the next line ends in column 72.
            open.append(" ".repeat(FIXED_WIDTH - text.length()));
        }
        return i;
    }

    /** Reads pseudo-text from {@code from}. Gives the index after the closing delimiter. */
    private int pseudoText(SourceLine line, int from) {
        String text = line.text();
        int close = text.indexOf("==", from);
        if (close < 0) {
            open.append(text, from, text.length());
            return text.length();
        }
        open.append(text, from, close);
        tokens.add(new Token(TokenType.PSEUDO_TEXT, open.toString().trim(), start.source(),
            start.line(), start.column()));
        open = null;
        return close + 2;
    }

    private void closeOpen() {
        if (open != null) {
            diagnostics.error(start.source(), start.line(), start.column(), quote == '='
                ? "the pseudo-text has no closing =="
                : "the literal has no closing quote");
            open = null;
        }
    }

    private static boolean endsWord(String text, int index) {
        return index >= text.length() || text.charAt(index) == ' ';
    }

    private static Token token(TokenType type, String text, SourceLine line, int index) {
        return new Token(type, text, line.source(), line.number(), line.column() + index);
    }
}
