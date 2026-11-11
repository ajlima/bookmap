package pt.dazz.bookmap.copybook.parse;

import java.util.ArrayList;
import java.util.List;
import pt.dazz.bookmap.copybook.diag.Diagnostics;

/**
 * Reads the code area of each line of a copybook. Comment lines and blank lines are removed.
 */
public final class SourceReader {

    private static final int INDICATOR = 6;
    private static final int CODE_END = 72;

    private SourceReader() {
    }

    /**
     * Reads the lines of a copybook.
     *
     * @param source the source name, for diagnostics
     * @param content the copybook text
     * @param format the reference format
     * @param diagnostics the diagnostics of the step
     * @return the lines with code
     */
    public static List<SourceLine> read(String source, String content, SourceFormat format,
                                        Diagnostics diagnostics) {
        String[] raw = content.split("\r\n|\r|\n", -1);
        List<SourceLine> lines = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            SourceLine line = format == SourceFormat.FIXED
                ? fixed(source, i + 1, raw[i], diagnostics)
                : free(source, i + 1, raw[i]);
            if (line != null && !line.text().isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static SourceLine fixed(String source, int number, String text,
                                    Diagnostics diagnostics) {
        int tab = text.indexOf('\t');
        if (tab >= 0) {
            diagnostics.error(source, number, tab + 1,
                "a tab character makes the columns unclear. Replace tabs with spaces.");
            return null;
        }
        if (text.length() <= INDICATOR) {
            return null;
        }
        char indicator = text.charAt(INDICATOR);
        String code = text.substring(INDICATOR + 1, Math.min(text.length(), CODE_END));
        return switch (indicator) {
            case '*', '/', 'D', 'd' -> null;
            case '-' -> new SourceLine(source, number, INDICATOR + 2, code, true);
            case ' ' -> new SourceLine(source, number, INDICATOR + 2, code, false);
            default -> {
                diagnostics.error(source, number, INDICATOR + 1,
                    "unknown indicator '" + indicator + "' in column 7");
                yield null;
            }
        };
    }

    private static SourceLine free(String source, int number, String text) {
        int comment = text.indexOf("*>");
        String code = comment < 0 ? text : text.substring(0, comment);
        return new SourceLine(source, number, 1, code, false);
    }
}
