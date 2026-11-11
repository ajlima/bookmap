package pt.dazz.bookmap.copybook;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.JavaFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import pt.dazz.bookmap.processor.BookmapProcessor;
import pt.dazz.bookmap.spi.BookCodec;

/**
 * Compiles generated sources with the BookMap processor and loads the classes. Tests use
 * reflection to call the accessors of the compiled classes.
 */
public final class Compiled {

    private final ClassLoader loader;

    private Compiled(Compilation compilation) {
        this.loader = new GeneratedClassLoader(compilation);
    }

    /**
     * Compiles Java files with the processor. The compilation must have no warning.
     *
     * @param files the generated files
     * @return the compiled classes
     */
    public static Compiled compile(List<JavaFile> files) {
        return compile(files, false);
    }

    /**
     * Compiles Java files with the processor, and Lombok first if requested. The compilation
     * must have no warning.
     *
     * @param files the generated files
     * @param lombok {@code true} to run Lombok before the BookMap processor
     * @return the compiled classes
     */
    public static Compiled compile(List<JavaFile> files, boolean lombok) {
        List<JavaFileObject> sources = files.stream().map(file -> JavaFileObjects
            .forSourceString(file.packageName() + "." + file.typeSpec().name(),
                file.toString())).toList();
        List<Processor> processors = lombok
            ? List.of(lombokProcessor(), new BookmapProcessor())
            : List.of(new BookmapProcessor());
        Compilation compilation = Compiler.javac().withOptions("-Xlint:all,-processing")
            .withProcessors(processors).compile(sources);
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        return new Compiled(compilation);
    }

    private static Processor lombokProcessor() {
        try {
            return (Processor) Class.forName(
                    "lombok.launch.AnnotationProcessorHider$AnnotationProcessor")
                .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Gives the generated codec of an operation class.
     *
     * @param className the qualified name of the operation class
     * @return the codec
     */
    @SuppressWarnings("unchecked")
    public BookCodec<Object> codec(String className) {
        try {
            return (BookCodec<Object>) loader.loadClass(className + "_BookCodec")
                .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Creates an instance of a compiled class.
     *
     * @param className the qualified class name
     * @return the instance
     */
    public Object create(String className) {
        try {
            return loader.loadClass(className).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Calls a getter.
     *
     * @param bean the object
     * @param property the property name
     * @return the value
     */
    public static Object get(Object bean, String property) {
        return invoke(method(bean, "get" + cap(property), 0), bean);
    }

    /**
     * Calls a setter.
     *
     * @param bean the object
     * @param property the property name
     * @param value the value
     */
    public static void set(Object bean, String property, Object value) {
        invoke(method(bean, "set" + cap(property), 1), bean, value);
    }

    private static String cap(String property) {
        return Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }

    private static Method method(Object bean, String name, int parameters) {
        return Arrays.stream(bean.getClass().getMethods())
            .filter(m -> m.getName().equals(name) && m.getParameterCount() == parameters)
            .findFirst().orElseThrow(() -> new IllegalArgumentException("no method " + name));
    }

    private static Object invoke(Method method, Object bean, Object... args) {
        try {
            return method.invoke(bean, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Defines the classes from the class output of a compilation. */
    private static final class GeneratedClassLoader extends ClassLoader {

        private final Compilation compilation;

        GeneratedClassLoader(Compilation compilation) {
            super(Compiled.class.getClassLoader());
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
