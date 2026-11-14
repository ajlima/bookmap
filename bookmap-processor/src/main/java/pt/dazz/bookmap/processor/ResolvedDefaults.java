package pt.dazz.bookmap.processor;

import java.util.Optional;
import pt.dazz.bookmap.cobol.Usage;

/**
 * Gives the {@code @BookDefaults} values that apply to the fields of one declaring class.
 * A value is present only if the source code sets it explicitly on the class or on its package.
 *
 * @param charset the charset name
 * @param usage the default usage of numeric fields
 * @param signed the default sign setting of numeric fields
 * @param pattern the default date or time pattern
 */
public record ResolvedDefaults(Optional<String> charset, Optional<Usage> usage,
    Optional<Boolean> signed, Optional<String> pattern) {
}
