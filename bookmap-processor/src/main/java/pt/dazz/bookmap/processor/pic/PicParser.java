package pt.dazz.bookmap.processor.pic;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.processor.pic.PicSpec.Category;

/**
 * Parses a COBOL {@code PIC} string at compile time.
 * The syntax is in section 5.2.1 of the design spec.
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

    private final String original;
    private String picture;
    private int pos;

    private PicParser(String original) {
        this.original = original;
    }

    /**
     * Parses a {@code PIC} string. Examples: {@code X(10)}, {@code S9(13)V99},
     * {@code PIC S9(7)V9(2) USAGE IS COMP-3.}
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
        String[] words = text.toUpperCase(Locale.ROOT).split("\\s+");
        int index = 0;
        if (words[0].equals("PIC") || words[0].equals("PICTURE")) {
            index++;
        }
        if (index == words.length) {
            throw error("the picture is missing");
        }
        picture = words[index++];
        PicSpec spec = scanPicture();
        Optional<Usage> usage = scanUsage(words, index);
        if (spec.category() == Category.TEXT && usage.filter(u -> u != Usage.DISPLAY).isPresent()) {
            throw error("a text picture must not have a numeric usage");
        }
        return new PicSpec(spec.category(), spec.length(), spec.scale(), spec.signed(), usage);
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
            ? new PicSpec(Category.TEXT, length, 0, false, Optional.empty())
            : new PicSpec(Category.NUMBER, length, (int) decimalDigits, signed, Optional.empty());
    }

    private Optional<Usage> scanUsage(String[] words, int start) {
        int index = start;
        if (index < words.length && words[index].equals("USAGE")) {
            index++;
            if (index < words.length && words[index].equals("IS")) {
                index++;
            }
            if (index == words.length) {
                throw error("the usage is missing after USAGE");
            }
        }
        if (index == words.length) {
            return Optional.empty();
        }
        Usage usage = USAGES.get(words[index]);
        if (usage == null) {
            throw error("unknown or unsupported usage '" + words[index] + "'");
        }
        if (index + 1 < words.length) {
            throw error("unexpected text '" + words[index + 1] + "' after the usage");
        }
        return Optional.of(usage);
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
