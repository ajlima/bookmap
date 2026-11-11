package pt.daaz.bookmap.processor;

import com.google.testing.compile.Compilation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import pt.daaz.bookmap.spi.BookCodec;

/**
 * Loads the classes of a compilation and gives the generated codec of an operation class.
 * Tests use reflection to call the getters and setters of the compiled test classes.
 */
final class CompiledCodec {

    private CompiledCodec() {
    }

    /**
     * Gives a new instance of the generated codec of an operation class.
     *
     * @param compilation the successful compilation
     * @param className the binary name of the operation class, for example {@code test.Outer$Inner}
     * @return the codec
     */
    @SuppressWarnings("unchecked")
    static BookCodec<Object> load(Compilation compilation, String className) {
        String codecName = className.replace('$', '_') + "_BookCodec";
        try {
            Class<?> codec = new GeneratedClassLoader(compilation).loadClass(codecName);
            return (BookCodec<Object>) codec.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Calls the getter {@code get<Property>} or {@code is<Property>} of a bean. */
    static Object get(Object bean, String property) {
        String cap = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        Method method = Arrays.stream(bean.getClass().getMethods())
            .filter(m -> m.getParameterCount() == 0
                && (m.getName().equals("get" + cap) || m.getName().equals("is" + cap)))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("no getter " + property));
        return invoke(method, bean);
    }

    /** Calls the setter {@code set<Property>} of a bean. */
    static void set(Object bean, String property, Object value) {
        String name = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        Method method = Arrays.stream(bean.getClass().getMethods())
            .filter(m -> m.getParameterCount() == 1 && m.getName().equals(name))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("no setter " + property));
        invoke(method, bean, value);
    }

    private static Object invoke(Method method, Object bean, Object... args) {
        try {
            return method.invoke(bean, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Defines the classes from the class output of a compilation. */
    private static final class GeneratedClassLoader extends ClassLoader {

        private final Compilation compilation;

        GeneratedClassLoader(Compilation compilation) {
            super(CompiledCodec.class.getClassLoader());
            this.compilation = compilation;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            JavaFileObject file = compilation.generatedFile(StandardLocation.CLASS_OUTPUT,
                    name.replace('.', '/') + ".class")
                .orElseThrow(() -> new ClassNotFoundException(name));
            try (InputStream in = file.openInputStream()) {
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
