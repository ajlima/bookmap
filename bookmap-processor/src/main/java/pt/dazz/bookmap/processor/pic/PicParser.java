package pt.dazz.bookmap.processor.pic;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import pt.dazz.bookmap.annotation.Sign;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.processor.pic.PicSpec.Category;

/**
 * Parses a COBOL {@code PIC} string at compile time.
 * The syntax is in the Javadoc of {@link pt.dazz.bookmap.annotation.BookField#pic()}.
 * The parser uses a small hand-written scanner.
 */
public final class PicParser {

    private static final int MAX_COUNT_DIGITS = 9;

    private static final Map<String, Usage> USAGES = Map.of(
        "DISPLAY", Usage.DISPLAY,
        "COMP-3", Usage.COMP3,
        "COMPUTATIONAL-3", Usage.COMP3,
        "PACKED-DECIMAL", Usage.COMP3,
        "COMP", Usage.COMP,
        "COMPUTATIONAL", Usage.COMP,
        "COMP-4", Usage.COMP,
        "COMPUTATIONAL-4", Usage.COMP,
        "BINARY", Usage.COMP);

    private static final Set<String> SIGN_WORDS = Set.of("SIGN", "LEADING", "TRAILING");

    private final String original;
    private String picture;
    private int pos;
    private String[] words;
    private int index;
    private Optional<Usage> usage = Optional.empty();
    private Optional<Sign> sign = Optional.empty();

    private PicParser(String original) {
        this.original = original;
    }

    /**
     * Parses a {@code PIC} string. Examples: {@code X(10)}, {@code S9(13)V99},
     * {@code PIC S9(7)V9(2) USAGE IS COMP-3.}, {@code S9(5)V99 SIGN LEADING SEPARATE}
     *
     * @param pic the {@code PIC} string, with an optional usage clause
     * @return the parsed specification
     * @throws PicSyntaxException if the string is not correct or is not supported
     */
    public static PicSpec parse(String pic) {
        return new PicParser(pic).parse();
    }

    private PicSpec parse() {
        String text = original.strip();
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1).strip();
        }
        if (text.isEmpty()) {
            throw error("the string is empty");
        }
        words = text.toUpperCase(Locale.ROOT).split("\\s+");
        if (words[0].equals("PIC") || words[0].equals("PICTURE")) {
            index++;
        }
        if (index == words.length) {
            throw error("the picture is missing");
        }
        picture = words[index++];
        PicSpec spec = scanPicture();
        scanClauses();
        boolean numericUsage = usage.filter(u -> u != Usage.DISPLAY).isPresent();
        if (spec.category() == Category.TEXT && numericUsage) {
            throw error("a text picture must not have a numeric usage");
        }
        if (sign.isPresent() && !spec.signed()) {
            throw error("a SIGN clause needs a signed numeric picture");
        }
        if (sign.isPresent() && numericUsage) {
            throw error("a SIGN clause is permitted only with usage DISPLAY");
        }
        return new PicSpec(spec.category(), spec.length(), spec.scale(), spec.signed(), usage,
            sign);
    }

    /**
     * Reads the usage clause and the sign clause after the picture. They can come in the two
     * orders. Each clause can occur one time only.
     */
    private void scanClauses() {
        String last = null;
        while (index < words.length) {
            String word = words[index];
            if (sign.isEmpty() && SIGN_WORDS.contains(word)) {
                scanSign();
                last = "the sign clause";
            } else if (usage.isEmpty() && (last == null || word.equals("USAGE")
                || USAGES.containsKey(word))) {
                scanUsage();
                last = "the usage";
            } else {
                throw error("unexpected text '" + word + "' after " + last);
            }
        }
    }

    private void scanUsage() {
        if (words[index].equals("USAGE")) {
            index++;
            acceptWord("IS");
            if (index == words.length) {
                throw error("the usage is missing after USAGE");
            }
        }
        Usage found = USAGES.get(words[index]);
        if (found == null) {
            throw error("unknown or unsupported usage '" + words[index] + "'");
        }
        index++;
        usage = Optional.of(found);
    }

    /** Reads {@code [SIGN [IS]] {LEADING | TRAILING} [SEPARATE [CHARACTER]]}. */
    private void scanSign() {
        if (acceptWord("SIGN")) {
            acceptWord("IS");
        }
        boolean leading = acceptWord("LEADING");
        if (!leading && !acceptWord("TRAILING")) {
            throw error("SIGN needs LEADING or TRAILING");
        }
        boolean separate = acceptWord("SEPARATE");
        if (separate) {
            acceptWord("CHARACTER");
        }
        sign = Optional.of(leading
            ? separate ? Sign.LEADING_SEPARATE : Sign.LEADING
            : separate ? Sign.TRAILING_SEPARATE : Sign.TRAILING);
    }

    private boolean acceptWord(String word) {
        if (index < words.length && words[index].equals(word)) {
            index++;
            return true;
        }
        return false;
    }

    private PicSpec scanPicture() {
        boolean signed = accept('S');
        boolean text = !signed && peek('X');
        long digits = run(text ? 'X' : '9');
        boolean hasPoint = !text && accept('V');
        long decimalDigits = hasPoint ? run('9') : 0;
        // Check the leftover character first. Thus, the message names an unsupported symbol.
        if (pos < picture.length()) {
            throw error("unexpected or unsupported character '" + picture.charAt(pos)
                + "' in the picture");
        }
        if (hasPoint && decimalDigits == 0) {
            throw error("a 'V' must have decimal digits after it");
        }
        if (digits + decimalDigits == 0) {
            throw error("the picture has no digits");
        }
        int length = toLength(digits + decimalDigits);
        return text
            ? new PicSpec(Category.TEXT, length, 0, false, Optional.empty(), Optional.empty())
            : new PicSpec(Category.NUMBER, length, (int) decimalDigits, signed, Optional.empty(),
                Optional.empty());
    }

    /** Reads a sequence of symbols, each with an optional repeat count, and gives the total. */
    private long run(char symbol) {
        long total = 0;
        while (accept(symbol)) {
            total += accept('(') ? count() : 1;
        }
        return total;
    }

    private long count() {
        int close = picture.indexOf(')', pos);
        if (close < 0) {
            throw error("a '(' must have a ')' after it");
        }
        String digits = picture.substring(pos, close);
        pos = close + 1;
        if (digits.isEmpty() || digits.length() > MAX_COUNT_DIGITS
            || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw error("the repeat count '" + digits + "' must be a number from 1 to 999999999");
        }
        long value = Long.parseLong(digits);
        if (value == 0) {
            throw error("the repeat count must be greater than 0");
        }
        return value;
    }

    private int toLength(long length) {
        if (length > Integer.MAX_VALUE) {
            throw error("the length is too large");
        }
        return (int) length;
    }

    private boolean peek(char c) {
        return pos < picture.length() && picture.charAt(pos) == c;
    }

    private boolean accept(char c) {
        if (peek(c)) {
            pos++;
            return true;
        }
        return false;
    }

    private PicSyntaxException error(String problem) {
        return new PicSyntaxException(original, problem);
    }
}
