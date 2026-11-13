package pt.dazz.bookmap.codec;

import java.util.Locale;

/**
 * Describes a numeric edited picture, for example {@code Z,ZZ9.99CR} or {@code $$$,$$9.99}.
 * The processor and the copybook generator parse the picture at build time. A generated codec
 * keeps each picture in a {@code private static final} field.
 *
 * <p>The symbols are {@code 9 Z * . V , B 0 / + - CR DB $}. A floating string is two or more
 * {@code +}, {@code -}, or {@code $}. Each one-character symbol can have a repeat count, for
 * example {@code Z(5)}.
 */
public final class EditedPicture {

    /** The maximum number of symbols after the expansion of the repeat counts. */
    static final int MAX_LENGTH = 1000;

    private static final String EDIT_SYMBOLS = "Z*.,B0/+-$";
    private static final String INSERTION_SYMBOLS = ",B0/";
    private static final String FLOATING_SYMBOLS = "+-$";
    private static final int MAX_COUNT_DIGITS = 9;

    /** A digit position of the symbol {@code 9}. */
    static final char DIGIT = '9';
    /** A digit position of {@code Z}, {@code *}, or a floating string after its first symbol. */
    static final char SUPPRESS = 'Z';
    /** The first symbol of a floating string. It holds no digit. */
    static final char FLOAT_LEAD = 'F';
    /** An insertion position: {@code ,}, {@code B}, {@code 0}, or {@code /}. */
    static final char INSERT = 'I';
    /** The actual decimal point {@code .}. */
    static final char POINT = '.';
    /** A fixed currency sign {@code $}. */
    static final char CURRENCY = '$';
    /** A fixed {@code +}. */
    static final char PLUS = '+';
    /** A fixed {@code -}. */
    static final char MINUS = '-';
    /** A letter of {@code CR} or {@code DB}. */
    static final char LETTER = 'L';

    private final String text;
    private final boolean blankWhenZero;
    /** The kind of each character position. */
    final char[] kinds;
    /** The character of each insertion position and each letter of {@code CR} or {@code DB}. */
    final char[] chars;
    /** The symbol of the zero suppression: {@code Z}, {@code *}, or 0 for none. */
    final char suppress;
    /** The floating symbol: {@code +}, {@code -}, {@code $}, or 0 for none. */
    final char floating;
    private final int digits;
    private final int scale;
    private final boolean signed;

    private EditedPicture(String text, boolean blankWhenZero, Parser parser) {
        this.text = text;
        this.blankWhenZero = blankWhenZero;
        this.kinds = parser.kinds.toString().toCharArray();
        this.chars = parser.chars.toString().toCharArray();
        this.suppress = parser.suppress;
        this.floating = parser.floating;
        this.digits = parser.digits;
        this.scale = parser.scale;
        this.signed = parser.signs > 0;
    }

    /**
     * Parses an edited picture.
     *
     * @param picture the picture string, without {@code PIC} and without clauses. Letters are
     *     not case-sensitive.
     * @param blankWhenZero {@code true} if the item has the clause {@code BLANK WHEN ZERO}
     * @return the picture
     * @throws IllegalArgumentException if BookMap does not support the picture
     */
    public static EditedPicture parse(String picture, boolean blankWhenZero) {
        String upper = picture.toUpperCase(Locale.ROOT);
        Parser parser = new Parser(Parser.expand(upper));
        parser.classify();
        if (blankWhenZero && parser.suppress == '*') {
            throw new IllegalArgumentException("BLANK WHEN ZERO is not permitted with S or '*'");
        }
        return new EditedPicture(upper, blankWhenZero, parser);
    }

    /**
     * Tells if a picture string has an edit symbol. A repeat count, for example {@code (10)},
     * is not a symbol.
     *
     * @param picture the picture string, without {@code PIC} and without clauses
     * @return {@code true} if the picture has {@code Z * . , B 0 / + - $ CR DB}
     */
    public static boolean isEdited(String picture) {
        String symbols = picture.toUpperCase(Locale.ROOT).replaceAll("\\([^)]*\\)", "");
        for (char c : symbols.toCharArray()) {
            if (EDIT_SYMBOLS.indexOf(c) >= 0) {
                return true;
            }
        }
        // DB is found through B.
        return symbols.contains("CR");
    }

    /**
     * Gives the picture string, in upper case.
     *
     * @return the picture string
     */
    public String text() {
        return text;
    }

    /**
     * Tells if the item has the clause {@code BLANK WHEN ZERO}.
     *
     * @return {@code true} if a zero writes as spaces
     */
    public boolean blankWhenZero() {
        return blankWhenZero;
    }

    /**
     * Gives the number of digit positions. A floating string gives one position less than its
     * symbols.
     *
     * @return the number of digits
     */
    public int digits() {
        return digits;
    }

    /**
     * Gives the number of digit positions after the decimal point.
     *
     * @return the scale
     */
    public int scale() {
        return scale;
    }

    /**
     * Tells if the picture has a sign: {@code +}, {@code -}, {@code CR}, or {@code DB}.
     *
     * @return {@code true} for a signed picture
     */
    public boolean signed() {
        return signed;
    }

    /**
     * Gives the number of bytes: one for each character position. {@code CR} and {@code DB}
     * give two. {@code V} gives none.
     *
     * @return the number of bytes
     */
    public int byteLength() {
        return kinds.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EditedPicture picture && picture.text.equals(text)
            && picture.blankWhenZero == blankWhenZero;
    }

    @Override
    public int hashCode() {
        return text.hashCode() * 2 + (blankWhenZero ? 1 : 0);
    }

    /**
     * Gives the picture string, with {@code BLANK WHEN ZERO} if the item has the clause.
     *
     * @return the text
     */
    @Override
    public String toString() {
        return blankWhenZero ? text + " BLANK WHEN ZERO" : text;
    }

    /** Reads the symbols of a picture and checks the grammar. */
    private static final class Parser {

        private final String symbols;
        private final StringBuilder kinds = new StringBuilder();
        private final StringBuilder chars = new StringBuilder();
        private char suppress;
        private char floating;
        private int digits;
        private int scale;
        private int signs;
        private int nines;
        private boolean point;
        private boolean suppressAfterPoint;

        private Parser(String symbols) {
            this.symbols = symbols;
        }

        /**
         * Expands the repeat counts. {@code CR} becomes {@code C} and {@code DB} becomes
         * {@code D}.
         */
        static String expand(String picture) {
            StringBuilder out = new StringBuilder();
            int i = 0;
            while (i < picture.length()) {
                char c = picture.charAt(i++);
                boolean letters = (c == 'C' || c == 'D') && i < picture.length()
                    && picture.charAt(i) == (c == 'C' ? 'R' : 'B');
                if (letters) {
                    i++;
                } else {
                    checkSymbol(c);
                }
                long count = 1;
                if (i < picture.length() && picture.charAt(i) == '(') {
                    if (letters) {
                        throw new IllegalArgumentException(
                            "a repeat count must come after a symbol");
                    }
                    int close = picture.indexOf(')', i);
                    if (close < 0) {
                        throw new IllegalArgumentException("a '(' must have a ')' after it");
                    }
                    count = count(picture.substring(i + 1, close));
                    i = close + 1;
                }
                if (out.length() + count * (letters ? 2 : 1) > MAX_LENGTH) {
                    throw new IllegalArgumentException(
                        "the edited picture has more than " + MAX_LENGTH + " characters");
                }
                out.append(String.valueOf(c).repeat((int) count));
                if (letters) {
                    // The second position of CR or DB.
                    out.append(c);
                }
            }
            return out.toString();
        }

        private static void checkSymbol(char c) {
            if (c == '(') {
                throw new IllegalArgumentException("a repeat count must come after a symbol");
            }
            if (c == 'S') {
                throw new IllegalArgumentException(
                    "S is not permitted in an edited picture. Use '+', '-', CR, or DB");
            }
            if ("9V".indexOf(c) < 0 && EDIT_SYMBOLS.indexOf(c) < 0) {
                throw new IllegalArgumentException("unexpected or unsupported character '" + c
                    + "' in the edited picture");
            }
        }

        private static long count(String digits) {
            if (digits.isEmpty() || digits.length() > MAX_COUNT_DIGITS
                || !digits.chars().allMatch(c -> c >= '0' && c <= '9')
                || Long.parseLong(digits) == 0) {
                throw new IllegalArgumentException("the repeat count '" + digits
                    + "' must be a number from 1 to 999999999");
            }
            return Long.parseLong(digits);
        }

        /** Gives the kind of each position, and checks the rules of the grammar. */
        void classify() {
            findFloating();
            int last = symbols.length() - 1;
            boolean lead = false;
            for (int k = 0; k <= last; k++) {
                char c = symbols.charAt(k);
                if (c == floating) {
                    floatingSymbol(lead);
                    lead = true;
                } else if (c == 'C' || c == 'D') {
                    creditOrDebit(c, k, last);
                    k++;
                } else {
                    symbol(c, k, last);
                }
            }
            if (floating == '+' || floating == '-') {
                signs++;
            }
            if (signs > 1) {
                throw new IllegalArgumentException("the picture has more than one sign");
            }
            if (digits == 0) {
                throw new IllegalArgumentException("the edited picture has no digit position");
            }
            if (suppressAfterPoint && nines > 0) {
                char symbol = floating != 0 ? floating : suppress;
                throw new IllegalArgumentException("'" + symbol + "' after the decimal point needs '"
                    + symbol + "' in all digit positions");
            }
        }

        /**
         * Finds the floating symbol: a symbol of {@code + - $} that occurs two times or more.
         * Between its first and its last occurrence, only insertion symbols and a decimal point
         * are permitted.
         */
        private void findFloating() {
            for (char c : FLOATING_SYMBOLS.toCharArray()) {
                int first = symbols.indexOf(c);
                if (first >= 0 && symbols.indexOf(c, first + 1) > 0) {
                    if (floating != 0) {
                        throw moreThanOneFloatingString();
                    }
                    floating = c;
                    for (int k = first; k <= symbols.lastIndexOf(c); k++) {
                        char inside = symbols.charAt(k);
                        if (inside != c && (INSERTION_SYMBOLS + ".V").indexOf(inside) < 0) {
                            throw moreThanOneFloatingString();
                        }
                    }
                }
            }
        }

        private static IllegalArgumentException moreThanOneFloatingString() {
            return new IllegalArgumentException("the picture has more than one floating string");
        }

        /**
         * Reads a floating symbol. A {@code Z} or {@code *} cannot come before it, because
         * {@link #symbol} rejects {@code Z} and {@code *} in a picture with a floating string.
         */
        private void floatingSymbol(boolean lead) {
            if (lead) {
                suppressible();
            } else {
                position(FLOAT_LEAD, ' ');
            }
        }

        private void symbol(char c, int k, int last) {
            switch (c) {
                case '9' -> {
                    nines++;
                    digit(DIGIT);
                }
                case 'Z', '*' -> {
                    if (floating != 0 || suppress != 0 && suppress != c) {
                        throw exclusive();
                    }
                    suppress = c;
                    suppressible();
                }
                case ',', '0', '/' -> position(INSERT, c);
                case 'B' -> position(INSERT, ' ');
                case '.' -> {
                    decimalPoint();
                    position(POINT, '.');
                }
                case 'V' -> decimalPoint();
                case '+', '-' -> {
                    if (k != 0 && k != last) {
                        throw new IllegalArgumentException(
                            "a fixed '+' or '-' must be the first or the last symbol");
                    }
                    signs++;
                    position(c, c);
                }
                default -> {
                    // Only '$' is left: expand accepts no other symbol.
                    boolean afterSign = k == 1 && "+-".indexOf(symbols.charAt(0)) >= 0;
                    if (k != 0 && !afterSign) {
                        throw new IllegalArgumentException(
                            "a fixed '$' must be the first symbol, or the second after a sign");
                    }
                    position(CURRENCY, '$');
                }
            }
        }

        /** Reads the first position of {@code CR} (as {@code C}) or {@code DB} (as {@code D}). */
        private void creditOrDebit(char c, int k, int last) {
            if (k != last - 1) {
                throw new IllegalArgumentException("CR or DB must be the last symbol");
            }
            signs++;
            boolean credit = c == 'C';
            position(LETTER, credit ? 'C' : 'D');
            position(LETTER, credit ? 'R' : 'B');
        }

        private void suppressible() {
            if (nines > 0 && !point) {
                throw new IllegalArgumentException(
                    "a '9' must not come before 'Z', '*', or a floating symbol");
            }
            suppressAfterPoint |= point;
            digit(SUPPRESS);
        }

        private void digit(char kind) {
            digits++;
            if (point) {
                scale++;
            }
            position(kind, ' ');
        }

        private void decimalPoint() {
            if (point) {
                throw new IllegalArgumentException("the picture has more than one decimal point");
            }
            point = true;
        }

        private void position(char kind, char c) {
            kinds.append(kind);
            chars.append(c);
        }

        private static IllegalArgumentException exclusive() {
            return new IllegalArgumentException(
                "Z, *, and a floating string are mutually exclusive");
        }
    }
}
