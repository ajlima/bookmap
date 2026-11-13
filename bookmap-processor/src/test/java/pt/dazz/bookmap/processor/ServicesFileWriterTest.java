package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.junit.jupiter.api.Test;

class ServicesFileWriterTest {

    private final StringWriter written = new StringWriter();
    private final List<String> messages = new ArrayList<>();
    private final List<Object> origins = new ArrayList<>();
    private int creates;
    private IOException readFailure = new FileNotFoundException(ServicesFileWriter.PATH);

    /** A filer with an existing service file (or none if {@code existing} is null). */
    private Filer filer(String existing, boolean failOnCreate) {
        return (Filer) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {Filer.class}, (proxy, method, args) -> {
                if (method.getName().equals("getResource")) {
                    if (existing == null) {
                        throw readFailure;
                    }
                    return file(existing);
                }
                creates++;
                origins.addAll(List.of((Object[]) args[3]));
                if (failOnCreate) {
                    throw new IOException("disk full");
                }
                return file("");
            });
    }

    private FileObject file(String content) {
        return new SimpleJavaFileObject(URI.create("mem:///services"), JavaFileObject.Kind.OTHER) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }

            @Override
            public Writer openWriter() {
                return written;
            }
        };
    }

    /** Elements that know only the given type names. */
    private static Elements elements(Set<String> known) {
        return (Elements) Proxy.newProxyInstance(ServicesFileWriterTest.class.getClassLoader(),
            new Class<?>[] {Elements.class}, (proxy, method, args) -> {
                String name = args[0].toString();
                return known.contains(name) ? type(name) : null;
            });
    }

    private static TypeElement type(String name) {
        return (TypeElement) Proxy.newProxyInstance(ServicesFileWriterTest.class.getClassLoader(),
            new Class<?>[] {TypeElement.class}, (proxy, method, args) -> switch (method.getName()) {
                case "toString" -> name;
                case "hashCode" -> name.hashCode();
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private Messager messager() {
        return (Messager) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {Messager.class}, (proxy, method, args) -> {
                messages.add(args[0] + ": " + args[1]);
                return null;
            });
    }

    @Test
    void mergesExistingNamesAndRemovesNamesOfMissingClasses() {
        ServicesFileWriter writer = new ServicesFileWriter();
        writer.add("test.Beta_BookCodec", "test.Beta");

        writer.write(filer("# codecs\ntest.Alpha_BookCodec # first\n\n  \ntest.Gone_BookCodec\n",
                false),
            elements(Set.of("test.Alpha_BookCodec", "test.Beta_BookCodec", "test.Beta")),
            messager());

        assertThat(written.toString()).isEqualTo("test.Alpha_BookCodec\ntest.Beta_BookCodec\n");
        assertThat(origins).extracting(Object::toString).containsExactly("test.Beta");
        assertThat(messages).isEmpty();
    }

    @Test
    void missingFileIsIgnored() {
        ServicesFileWriter writer = new ServicesFileWriter();
        writer.add("test.Beta_BookCodec", "test.Beta");

        writer.write(filer(null, false), elements(Set.of("test.Beta_BookCodec", "test.Beta")),
            messager());

        assertThat(written.toString()).isEqualTo("test.Beta_BookCodec\n");
    }

    @Test
    void missingPathIsIgnored() {
        readFailure = new NoSuchFileException(ServicesFileWriter.PATH);
        ServicesFileWriter writer = new ServicesFileWriter();
        writer.add("test.Beta_BookCodec", "test.Beta");

        writer.write(filer(null, false), elements(Set.of("test.Beta_BookCodec", "test.Beta")),
            messager());

        assertThat(written.toString()).isEqualTo("test.Beta_BookCodec\n");
        assertThat(messages).isEmpty();
    }

    @Test
    void readErrorIsAWarning() {
        readFailure = new IOException("access denied");
        ServicesFileWriter writer = new ServicesFileWriter();
        writer.add("test.Beta_BookCodec", "test.Beta");

        writer.write(filer(null, false), elements(Set.of("test.Beta_BookCodec", "test.Beta")),
            messager());

        assertThat(written.toString()).isEqualTo("test.Beta_BookCodec\n");
        assertThat(messages).containsExactly(Diagnostic.Kind.WARNING
            + ": could not read the existing " + ServicesFileWriter.PATH + ": access denied");
    }

    @Test
    void writesNothingWithoutCodecs() {
        new ServicesFileWriter().write(filer("test.Alpha_BookCodec\n", false),
            elements(Set.of("test.Alpha_BookCodec")), messager());

        assertThat(creates).isZero();
        assertThat(written.toString()).isEmpty();
    }

    @Test
    void writeErrorIsReported() {
        ServicesFileWriter writer = new ServicesFileWriter();
        writer.add("test.Beta_BookCodec", "test.Beta");

        writer.write(filer(null, true), elements(Set.of("test.Beta_BookCodec", "test.Beta")),
            messager());

        assertThat(messages).containsExactly(Diagnostic.Kind.ERROR + ": could not write "
            + ServicesFileWriter.PATH + ": disk full");
    }

    @Test
    void originatingElementsAreTheOperationClasses() {
        ServicesFileWriter writer = new ServicesFileWriter();
        writer.add("test.Alpha_BookCodec", "test.Alpha");
        writer.add("test.Beta_BookCodec", "test.Beta");

        writer.write(filer(null, false), elements(Set.of("test.Alpha_BookCodec", "test.Alpha",
            "test.Beta_BookCodec", "test.Beta")), messager());

        assertThat(origins).extracting(Object::toString)
            .containsExactly("test.Alpha", "test.Beta");
        assertThat(origins).allMatch(Element.class::isInstance);
    }
}
