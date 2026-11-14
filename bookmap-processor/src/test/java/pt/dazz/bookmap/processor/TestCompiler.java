package pt.dazz.bookmap.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/**
 * Compiles test sources with the bookmap processor and collects the model snapshots.
 */
final class TestCompiler {

    private static final String HEADER = String.join("\n",
        "package test;",
        "import java.math.BigDecimal;",
        "import java.time.LocalDate;",
        "import java.time.LocalDateTime;",
        "import java.time.LocalTime;",
        "import java.util.List;",
        "import pt.dazz.bookmap.Continuable;",
        "import pt.dazz.bookmap.Validatable;",
        "import pt.dazz.bookmap.annotation.*;",
        "import pt.dazz.bookmap.cobol.*;",
        "import pt.dazz.bookmap.convert.FieldContext;",
        "import pt.dazz.bookmap.convert.FieldConverter;",
        "import pt.dazz.bookmap.convert.NoConverter;");

    /** Reports all lint warnings except the notes about unclaimed annotations. */
    private static final String LINT = "-Xlint:all,-processing";

    final List<ModelSnapshot> snapshots = new ArrayList<>();

    private TestCompiler() {
    }

    static TestCompiler create() {
        return new TestCompiler();
    }

    /**
     * Makes a source file in the package {@code test} with the common imports.
     */
    static JavaFileObject source(String simpleName, String... lines) {
        return JavaFileObjects.forSourceString("test." + simpleName,
            HEADER + "\n" + String.join("\n", lines));
    }

    /**
     * Makes a source file in another package, with the common imports.
     */
    static JavaFileObject sourceIn(String packageName, String simpleName, String... lines) {
        return JavaFileObjects.forSourceString(packageName + "." + simpleName,
            HEADER.replace("package test;", "package " + packageName + ";") + "\n"
                + String.join("\n", lines));
    }

    /**
     * Makes a {@code package-info.java} file with one package annotation.
     */
    static JavaFileObject packageInfo(String packageName, String annotation) {
        return JavaFileObjects.forSourceString(packageName + ".package-info",
            annotation + " package " + packageName + ";\n"
                + "import pt.dazz.bookmap.annotation.BookDefaults;\n"
                + "import pt.dazz.bookmap.cobol.Usage;");
    }

    /**
     * Gives one text field with its accessors. A test uses it when the members of an operation
     * class do not matter, because an operation class needs at least one member.
     */
    static final String ID_FIELD = "@BookField(order = 1, length = 1) private String id; "
        + accessors("String", "id");

    /**
     * Gives a public getter and setter for a field.
     */
    static String accessors(String type, String name) {
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return "public " + type + " get" + cap + "() { return " + name + "; } "
            + "public void set" + cap + "(" + type + " v) { this." + name + " = v; }";
    }

    Compilation compile(JavaFileObject... files) {
        return compile(List.of(), files);
    }

    Compilation compile(List<Processor> others, JavaFileObject... files) {
        List<Processor> processors = new ArrayList<>(others);
        processors.add(new BookmapProcessor(snapshots::add));
        return Compiler.javac().withOptions(LINT).withProcessors(processors).compile(files);
    }

    Compilation compileWithProcessors(List<Processor> processors, JavaFileObject... files) {
        return Compiler.javac().withOptions(LINT).withProcessors(processors).compile(files);
    }

    BookmapProcessor processor() {
        return new BookmapProcessor(snapshots::add);
    }

    ModelSnapshot only() {
        if (snapshots.size() != 1) {
            throw new AssertionError("expected one snapshot, got " + snapshots);
        }
        return snapshots.get(0);
    }

    static Processor lombok() {
        try {
            return (Processor) Class.forName(
                    "lombok.launch.AnnotationProcessorHider$AnnotationProcessor")
                .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A test processor that generates source files in the first round, as another code
     * generator does.
     */
    static final class GeneratingProcessor extends AbstractProcessor {

        private final Map<String, String> sources;
        private boolean done;

        GeneratingProcessor(Map<String, String> sources) {
            this.sources = sources;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (!done) {
                done = true;
                sources.forEach((name, text) -> {
                    try (var writer = processingEnv.getFiler().createSourceFile(name).openWriter()) {
                        writer.write(HEADER + "\n" + text);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
            return false;
        }
    }
}
