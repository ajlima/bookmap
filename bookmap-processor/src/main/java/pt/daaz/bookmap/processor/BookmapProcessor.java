package pt.daaz.bookmap.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import pt.daaz.bookmap.processor.model.OperationModel;

/**
 * Processes the classes with {@code @BookOperation}.
 *
 * <p>The processor builds the model of each class and reports the mapping errors. A class with
 * a field type or a superclass from another generator, or with missing getters or setters, waits
 * for the next round. In the last round, the processor builds the waiting classes again only to
 * report their errors. It never generates sources in the last round.
 */
public final class BookmapProcessor extends AbstractProcessor {

    private final Consumer<ModelSnapshot> listener;
    private final Set<String> deferred = new LinkedHashSet<>();
    private final Map<String, String> operationNames = new HashMap<>();
    private ModelBuilder builder;

    /**
     * Makes the processor. The compiler calls this constructor.
     */
    public BookmapProcessor() {
        this(snapshot -> { });
    }

    /**
     * Makes the processor with a listener that receives a snapshot of each model. Tests use it.
     *
     * @param listener the listener
     */
    BookmapProcessor(Consumer<ModelSnapshot> listener) {
        this.listener = listener;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ModelBuilder.BOOK_OPERATION);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        builder = new ModelBuilder(env);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        Elements elements = processingEnv.getElementUtils();
        List<TypeElement> types = new ArrayList<>();
        deferred.forEach(name -> types.add(elements.getTypeElement(name)));
        deferred.clear();
        if (round.processingOver()) {
            // Only to report the errors. A class cannot become complete in this round, because
            // no new source arrives after the previous round.
            types.forEach(type -> builder.build(type, true));
            return false;
        }
        types.addAll(ElementFilter.typesIn(round.getElementsAnnotatedWith(
            elements.getTypeElement(ModelBuilder.BOOK_OPERATION))));
        for (TypeElement type : types) {
            Optional<OperationModel> model = builder.build(type, false);
            if (model.isPresent()) {
                register(model.get());
            } else if (builder.deferred()) {
                deferred.add(type.getQualifiedName().toString());
            }
        }
        return false;
    }

    private void register(OperationModel model) {
        String name = model.operationName();
        String type = model.type().getQualifiedName().toString();
        String previous = operationNames.putIfAbsent(name, type);
        if (previous != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                model.type().getSimpleName() + ": operation name '" + name
                    + "' is already used by " + previous, model.type());
            return;
        }
        listener.accept(ModelSnapshot.of(model));
    }
}
