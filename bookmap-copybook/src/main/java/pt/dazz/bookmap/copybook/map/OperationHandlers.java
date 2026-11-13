package pt.dazz.bookmap.copybook.map;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.lang.model.SourceVersion;
import pt.dazz.bookmap.copybook.ast.DataItem;

/**
 * Resolves the {@code continuable} and {@code validatable} options. Each option maps a record
 * name to a handler class. The generated operation class implements the interface and calls
 * a static method of the handler class.
 */
final class OperationHandlers {

    private final ModelMapper mapper;

    /** Checks the record names and the class names of both options. */
    OperationHandlers(ModelMapper mapper, List<DataItem> records) {
        this.mapper = mapper;
        check("continuable", mapper.options.continuable(), records);
        check("validatable", mapper.options.validatable(), records);
    }

    private void check(String kind, Map<String, String> handlers, List<DataItem> records) {
        handlers.forEach((record, handler) -> {
            String name = record.trim().toUpperCase(Locale.ROOT);
            if (records.stream().noneMatch(item -> item.name().equals(name))) {
                error(kind + ": " + record + " is not a record. Use the name of a level 01"
                    + " item.");
            }
            if (!SourceVersion.isName(handler)) {
                error(kind + ": '" + handler + "' is not a Java class name");
            }
        });
    }

    private void error(String message) {
        mapper.optionError(message);
    }

    /** Gives the {@code Continuable} handler of a record, or {@code null}. */
    String continuable(DataItem record) {
        return handler(mapper.options.continuable(), record);
    }

    /** Gives the {@code Validatable} handler of a record, or {@code null}. */
    String validatable(DataItem record) {
        return handler(mapper.options.validatable(), record);
    }

    private static String handler(Map<String, String> handlers, DataItem record) {
        return handlers.entrySet().stream()
            .filter(entry -> entry.getKey().trim().toUpperCase(Locale.ROOT)
                .equals(record.name()))
            .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
