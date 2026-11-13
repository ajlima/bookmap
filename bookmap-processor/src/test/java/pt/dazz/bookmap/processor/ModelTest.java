package pt.dazz.bookmap.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import java.util.List;
import java.util.ServiceLoader;
import javax.annotation.processing.Processor;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.annotation.Sign;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.ItemModel;
import pt.dazz.bookmap.processor.model.MemberModel;

class ModelTest {

    @Test
    void descriptorIdentifiesClassFieldAndOrder() {
        FieldModel count = new FieldModel("Order", "lineCount", 3, 3, Direction.OUTPUT,
            Usage.DISPLAY, 0, false, Sign.TRAILING, "yyyyMMdd", FieldModel.JavaKind.INT, null, "getLineCount",
            "setLineCount", null, 3, null, null);
        MemberModel lines = new CollectionModel("Order", "lines", 4, Direction.OUTPUT, count, 5,
            false, new ItemModel(null, List.of(), 0, null), "getLines", "setLines",
            EmptyItem.LOW_VALUES, null, 0);
        assertThat(count.descriptor()).isEqualTo("Order.lineCount (order 3)");
        assertThat(lines.descriptor()).isEqualTo("Order.lines (order 4)");
    }

    @Test
    void publicConstructorBuildsModels() {
        Compilation compilation = Compiler.javac().withProcessors(new BookmapProcessor())
            .compile(TestCompiler.source("Order", "@BookOperation public class Order { " + TestCompiler.ID_FIELD + " }"));
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    void serviceFileRegistersTheProcessor() {
        List<Class<?>> types = ServiceLoader.load(Processor.class).stream()
            .<Class<?>>map(ServiceLoader.Provider::type).toList();
        assertThat(types).contains(BookmapProcessor.class);
    }
}
