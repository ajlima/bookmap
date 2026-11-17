package pt.dazz.copymapper.copybook.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Selects one alternative of a group that redefines another. */
public sealed interface VariantSpec permits VariantSpec.Discriminator, VariantSpec.FixedSelection {

    /**
     * The alternative depends on the value of a field.
     *
     * @param path the path of the discriminator field
     * @param values the value, and the path of its alternative, in order
     */
    record Discriminator(String path, Map<String, String> values) implements VariantSpec {

        /** Checks the path, the values, and the alternatives, and copies the map in order. */
        public Discriminator {
            Objects.requireNonNull(path, "path");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "a value of the discriminator");
                Objects.requireNonNull(entry.getValue(),
                        "the alternative of the value " + entry.getKey());
            }
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    /**
     * The alternative is fixed for each direction.
     *
     * @param request the path of the alternative of the request, or null
     * @param response the path of the alternative of the response, or null
     */
    record FixedSelection(String request, String response) implements VariantSpec {

        /** Checks that at least one alternative is set. */
        public FixedSelection {
            if (request == null && response == null) {
                throw new IllegalArgumentException(
                        "a fixed selection needs a request or a response");
            }
        }
    }
}
