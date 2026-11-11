package pt.dazz.bookmap.codec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.spi.BookCodec;

/**
 * Finds the codec of a book class.
 * The registry finds the codec by the exact class. It does not search the superclasses.
 */
public final class BookCodecRegistry {

    private final Map<Class<?>, BookCodec<?>> codecs;

    private BookCodecRegistry(Map<Class<?>, BookCodec<?>> codecs) {
        this.codecs = codecs;
    }

    /**
     * Loads the codecs with the {@link ServiceLoader} of the thread context class loader.
     *
     * @return the registry
     * @throws IllegalStateException if two codecs have the same book class
     */
    public static BookCodecRegistry loadDefault() {
        return index(ServiceLoader.load(BookCodec.class));
    }

    /**
     * Loads the codecs with the {@link ServiceLoader} of a class loader.
     *
     * @param loader the class loader
     * @return the registry
     * @throws IllegalStateException if two codecs have the same book class
     */
    public static BookCodecRegistry loadDefault(ClassLoader loader) {
        return index(ServiceLoader.load(BookCodec.class, loader));
    }

    /**
     * Creates a registry from codecs.
     *
     * @param codecs the codecs
     * @return the registry
     * @throws IllegalStateException if two codecs have the same book class
     */
    public static BookCodecRegistry of(BookCodec<?>... codecs) {
        return index(Arrays.asList(codecs));
    }

    @SuppressWarnings("rawtypes")
    private static BookCodecRegistry index(Iterable<? extends BookCodec> codecs) {
        Map<Class<?>, BookCodec<?>> index = new HashMap<>();
        for (BookCodec<?> codec : codecs) {
            if (index.putIfAbsent(codec.type(), codec) != null) {
                throw new IllegalStateException(
                    "More than one codec for " + codec.type().getName());
            }
        }
        return new BookCodecRegistry(index);
    }

    /**
     * Gives the codec of a book class.
     *
     * @param type the exact book class
     * @param <T> the book class
     * @return the codec
     * @throws MappingException if the registry has no codec for the class
     */
    @SuppressWarnings("unchecked")
    public <T> BookCodec<T> codecFor(Class<T> type) {
        BookCodec<?> codec = codecs.get(type);
        if (codec == null) {
            throw new MappingException("No codec for " + type.getName()
                + ". Make sure that bookmap-processor is on the annotationProcessor path.");
        }
        return (BookCodec<T>) codec;
    }
}
