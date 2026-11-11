package pt.daaz.bookmap.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Writes the service file {@code META-INF/services/pt.daaz.bookmap.spi.BookCodec}.
 *
 * <p>The writer collects the codec names of all rounds. It writes the file one time, in the
 * last round. As AutoService does, it reads the existing file, merges the names, and removes
 * the names of classes that do not exist. Thus, an incremental compilation does not lose codecs.
 */
final class ServicesFileWriter {

    /** The path of the service file in the class output. */
    static final String PATH = "META-INF/services/pt.daaz.bookmap.spi.BookCodec";

    private final Set<String> codecNames = new TreeSet<>();
    private final Set<String> operationClasses = new LinkedHashSet<>();

    /**
     * Adds a generated codec.
     *
     * @param codecName the qualified name of the codec
     * @param operationClass the qualified name of the operation class
     */
    void add(String codecName, String operationClass) {
        codecNames.add(codecName);
        operationClasses.add(operationClass);
    }

    /**
     * Writes the service file. If no codec was generated, the method writes nothing.
     *
     * @param filer the filer
     * @param elements the element utilities, to find the classes that exist
     * @param messager the messager, for a write error
     */
    void write(Filer filer, Elements elements, Messager messager) {
        if (codecNames.isEmpty()) {
            return;
        }
        Set<String> names = new TreeSet<>(codecNames);
        names.addAll(existingNames(filer));
        names.removeIf(name -> elements.getTypeElement(name) == null);
        Element[] origins = operationClasses.stream().map(elements::getTypeElement)
            .toArray(Element[]::new);
        try {
            FileObject file = filer.createResource(StandardLocation.CLASS_OUTPUT, "", PATH,
                origins);
            try (Writer writer = file.openWriter()) {
                for (String name : names) {
                    writer.write(name + "\n");
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "could not write " + PATH + ": " + e.getMessage());
        }
    }

    private static Set<String> existingNames(Filer filer) {
        Set<String> names = new TreeSet<>();
        try {
            FileObject file = filer.getResource(StandardLocation.CLASS_OUTPUT, "", PATH);
            file.getCharContent(true).toString().lines().forEach(line -> {
                int comment = line.indexOf('#');
                String name = (comment < 0 ? line : line.substring(0, comment)).trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            });
        } catch (IOException e) {
            // There is no file yet.
        }
        return names;
    }
}
