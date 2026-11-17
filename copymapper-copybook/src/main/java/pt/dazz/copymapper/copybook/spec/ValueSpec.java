package pt.dazz.copymapper.copybook.spec;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import pt.dazz.copymapper.copybook.ast.Literal;

/**
 * A literal value.
 *
 * @param kind the name of the literal kind: a name of {@link Literal.Kind}
 * @param text the text of the literal
 */
public record ValueSpec(String kind, String text) {

    /** Checks that the kind is a literal kind and that the text is set. */
    public ValueSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        if (Arrays.stream(Literal.Kind.values()).noneMatch(k -> k.name().equals(kind))) {
            throw new IllegalArgumentException("unknown value kind '" + kind + "'. The kinds are "
                    + Arrays.stream(Literal.Kind.values()).map(Enum::name)
                            .collect(Collectors.joining(", ")) + ".");
        }
    }
}
