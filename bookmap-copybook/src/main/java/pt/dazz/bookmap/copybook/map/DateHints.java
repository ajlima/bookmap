package pt.dazz.bookmap.copybook.map;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import pt.dazz.bookmap.codec.Formats;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.Usage;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.parse.Picture;

/**
 * Applies the date hints of the mapping options. A hint maps an item, or the items that match
 * a name pattern, to a date or time pattern. The pattern gives the Java type.
 */
final class DateHints {

    /**
     * The date or time type of a field.
     *
     * @param type the Java type
     * @param pattern the date or time pattern
     */
    record Applied(JavaType type, String pattern) {
    }

    /** A Java type that a pattern can fit, with a sample value and a parse query. */
    private record Candidate(JavaType type, Function<LocalDateTime, TemporalAccessor> sample,
                             TemporalQuery<?> query) {
    }

    // The same order and the same samples as the processor. The first sample has two-digit
    // values. The second sample has one-digit values. A pattern of variable length gives
    // text of a different length for them.
    private static final List<Candidate> CANDIDATES = List.of(
        new Candidate(JavaType.LOCAL_DATE_TIME, value -> value, LocalDateTime::from),
        new Candidate(JavaType.LOCAL_DATE, LocalDateTime::toLocalDate, LocalDate::from),
        new Candidate(JavaType.LOCAL_TIME, LocalDateTime::toLocalTime, LocalTime::from));
    private static final List<LocalDateTime> SAMPLES = List.of(
        LocalDateTime.of(2026, 12, 31, 23, 59, 58),
        LocalDateTime.of(2026, 1, 1, 1, 1, 1));
    private static final String KIND = "date hint";

    private final ModelMapper mapper;
    private final Map<DataItem, String> byItem = new IdentityHashMap<>();
    private final Map<String, Pattern> globs = new LinkedHashMap<>();
    private final Set<String> active = new HashSet<>();
    private final Set<String> used = new HashSet<>();

    /** Resolves the names of the hints. Reports an error for each name that is not correct. */
    DateHints(ModelMapper mapper, RedefinesResolver resolver) {
        this.mapper = mapper;
        mapper.options.dates().forEach((key, pattern) -> {
            if (key.contains("*")) {
                glob(key);
                return;
            }
            DataItem item = resolver.find(KIND, key);
            if (item == null) {
                return;
            }
            if (item.isGroup()) {
                resolver.decisionError(KIND + ": " + key + " is a group. A date hint applies to"
                    + " an elementary item.");
                return;
            }
            byItem.put(item, key);
            active.add(key);
        });
    }

    private void glob(String key) {
        String word = key.trim().toUpperCase(Locale.ROOT);
        if (word.chars().anyMatch(Character::isWhitespace)) {
            mapper.diagnostics.error(mapper.sourceName, 1, 1, KIND + ": the name pattern '"
                + key + "' must be one word, without OF or IN");
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String part : word.split("\\*", -1)) {
            parts.add(Pattern.quote(part));
        }
        globs.put(key, Pattern.compile(String.join(".*", parts)));
        active.add(key);
    }

    /**
     * Gives the date or time type of an elementary item, or {@code null} if no hint applies.
     * Reports an error and gives {@code null} if the hint does not fit the item.
     */
    Applied apply(DataItem item, Usage usage) {
        String pattern = pattern(item);
        return pattern == null ? null : check(item, usage, pattern);
    }

    /** Finds the pattern of an item. A name hint has priority over the name patterns. */
    private String pattern(DataItem item) {
        String key = byItem.get(item);
        if (key != null) {
            used.add(key);
            return mapper.options.dates().get(key);
        }
        if (item.isFiller()) {
            return null;
        }
        Map<String, String> matches = new LinkedHashMap<>();
        globs.forEach((glob, regex) -> {
            if (regex.matcher(item.name()).matches()) {
                used.add(glob);
                matches.put(glob, mapper.options.dates().get(glob));
            }
        });
        if (matches.isEmpty()) {
            return null;
        }
        if (new HashSet<>(matches.values()).size() > 1) {
            mapper.error(item, item.name() + " matches the date hints "
                + String.join(", ", matches.keySet()) + " with different patterns. Add a date"
                + " hint for " + item.name() + ".");
            return null;
        }
        return matches.values().iterator().next();
    }

    private Applied check(DataItem item, Usage usage, String pattern) {
        Picture picture = item.picture();
        if (usage != Usage.DISPLAY) {
            mapper.error(item, item.name() + ": a date or time field must have the usage"
                + " DISPLAY");
            return null;
        }
        if (picture.signed() || picture.scale() > 0) {
            mapper.error(item, item.name() + ": a date or time field must not have S or V in"
                + " the picture");
            return null;
        }
        DateTimeFormatter formatter;
        try {
            formatter = Formats.of(pattern);
        } catch (IllegalArgumentException e) {
            mapper.error(item, item.name() + ": the date pattern '" + pattern
                + "' is not valid: " + e.getMessage());
            return null;
        }
        for (Candidate candidate : CANDIDATES) {
            String text;
            try {
                text = formatter.format(candidate.sample().apply(SAMPLES.get(0)));
                formatter.parse(text, candidate.query());
            } catch (DateTimeException e) {
                continue;
            }
            if (formatter.format(candidate.sample().apply(SAMPLES.get(1))).length()
                != text.length()) {
                mapper.error(item, item.name() + ": the date pattern '" + pattern
                    + "' gives text of variable length");
                return null;
            }
            if (text.length() != picture.length()) {
                mapper.error(item, item.name() + ": the date pattern '" + pattern + "' gives "
                    + text.length() + " characters, but the picture " + picture.text()
                    + " has " + picture.length());
                return null;
            }
            return new Applied(candidate.type(), pattern);
        }
        mapper.error(item, item.name() + ": the date pattern '" + pattern
            + "' does not fit LocalDateTime, LocalDate, or LocalTime");
        return null;
    }

    /** Reports each correct hint that gave no field. */
    void checkUnused() {
        for (String key : mapper.options.dates().keySet()) {
            if (!active.contains(key) || used.contains(key)) {
                continue;
            }
            if (globs.containsKey(key)) {
                mapper.diagnostics.warning(mapper.sourceName, 1, 1, KIND + ": the name pattern "
                    + key + " matches no field");
            } else {
                mapper.diagnostics.error(mapper.sourceName, 1, 1, KIND + ": " + key
                    + " gives no field. The generator skips this item.");
            }
        }
    }
}
