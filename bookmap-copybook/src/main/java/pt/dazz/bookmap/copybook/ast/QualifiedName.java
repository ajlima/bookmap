package pt.dazz.bookmap.copybook.ast;

import java.util.List;

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

    @Override
    public String toString() {
        return String.join(" OF ", parts);
    }
}
