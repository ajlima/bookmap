package pt.dazz.bookmap.processor;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import pt.dazz.bookmap.annotation.Usage;

/**
 * Reads {@code @BookDefaults} of a class and of its package.
 *
 * <p>An explicit value on the class has priority over an explicit value on the package. The
 * resolver does not use the enclosing class of a nested class, or the superclasses.
 */
public final class DefaultsResolver {

    private static final String BOOK_DEFAULTS = "pt.dazz.bookmap.annotation.BookDefaults";

    private final Elements elements;

    /**
     * Makes a resolver.
     *
     * @param elements the element utilities of the compilation
     */
    public DefaultsResolver(Elements elements) {
        this.elements = elements;
    }

    /**
     * Gives the defaults for the fields that a class declares.
     *
     * @param type the class that declares the fields
     * @return the explicit values of the class, then of its package
     */
    public ResolvedDefaults forDeclaringClass(TypeElement type) {
        Map<String, Object> values = merged(type);
        values.values().removeIf(value -> value == Mirrors.UNRESOLVED);
        return new ResolvedDefaults(
            Optional.ofNullable((String) values.get("charset")),
            Optional.ofNullable((String) values.get("usage")).map(Usage::valueOf),
            Optional.ofNullable((Boolean) values.get("signed")),
            Optional.ofNullable((String) values.get("pattern")));
    }

    /**
     * Tells if {@code @BookDefaults} of a class or of its package has a value that javac could
     * not resolve. javac reports that problem.
     *
     * @param type the class
     * @return {@code true} if a value is unresolved
     */
    boolean unresolved(TypeElement type) {
        return merged(type).containsValue(Mirrors.UNRESOLVED);
    }

    /**
     * Gives the charset of {@code @BookDefaults} on the class only, without its package.
     *
     * @param type the class
     * @return the explicit charset of the class, or empty
     */
    Optional<String> classCharset(TypeElement type) {
        return Optional.ofNullable(explicit(type).get("charset"))
            .filter(String.class::isInstance).map(String.class::cast);
    }

    /**
     * Tells what is wrong with a charset name. The codec needs a single-byte charset that can
     * encode text.
     *
     * @param name the charset name, not empty
     * @return the problem, or empty if the charset is correct
     */
    static Optional<String> charsetProblem(String name) {
        Charset charset;
        try {
            charset = Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return Optional.of("charset '" + name + "' is not supported");
        }
        if (!charset.canEncode()) {
            return Optional.of("charset '" + name + "' cannot encode text");
        }
        if (charset.newEncoder().maxBytesPerChar() > 1) {
            return Optional.of("charset '" + name + "' is not a single-byte charset");
        }
        return Optional.empty();
    }

    private Map<String, Object> merged(TypeElement type) {
        Map<String, Object> values = new HashMap<>(explicit(elements.getPackageOf(type)));
        values.putAll(explicit(type));
        return values;
    }

    private static Map<String, Object> explicit(Element element) {
        return Mirrors.find(element, BOOK_DEFAULTS).map(Mirrors::explicitValues).orElse(Map.of());
    }
}
