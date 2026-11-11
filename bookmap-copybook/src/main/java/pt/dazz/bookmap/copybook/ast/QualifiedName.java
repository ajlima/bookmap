package pt.dazz.bookmap.copybook.ast;

import java.util.List;
import java.util.Locale;

/**
 * Gives a COBOL data name with its qualifiers, for example {@code CITY OF BILLING-ADDRESS}.
 *
 * @param parts the item name first, then the qualifiers from the nearest group outward
 */
public record QualifiedName(List<String> parts) {

    /**
     * Creates a qualified name.
     *
     * @param parts the item name first, then the qualifiers
     */
    public QualifiedName {
        parts = List.copyOf(parts);
    }

    /**
     * Gives the item name without qualifiers.
     *
     * @return the first part
     */
    public String name() {
        return parts.get(0);
    }

    /**
     * Parses a name such as {@code CITY OF BILLING-ADDRESS}. {@code IN} is a synonym of
     * {@code OF}. Letters are not case-sensitive.
     *
     * @param text the name
     * @return the qualified name
     * @throws IllegalArgumentException if the text is blank or has an empty part
     */
    public static QualifiedName parse(String text) {
        String[] parts = text.trim().toUpperCase(Locale.ROOT).split("\\s+(OF|IN)\\s+");
        for (String part : parts) {
            if (part.isBlank() || part.contains(" ")) {
                throw new IllegalArgumentException("'" + text + "' is not a valid data name");
            }
        }
        return new QualifiedName(List.of(parts));
    }

    @Override
    public String toString() {
        return String.join(" OF ", parts);
    }
}
