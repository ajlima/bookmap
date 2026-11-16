package pt.dazz.copymapper.copybook.map;

import java.util.List;
import pt.dazz.copymapper.copybook.diag.Diagnostic;
import pt.dazz.copymapper.copybook.diag.Severity;
import pt.dazz.copymapper.copybook.model.BookModel;

/**
 * Gives the result of the map step.
 *
 * @param model the model, or {@code null} if an error exists
 * @param diagnostics all diagnostics of the step
 */
public record MapResult(BookModel model, List<Diagnostic> diagnostics) {

    /**
     * Creates a result.
     *
     * @param model the model
     * @param diagnostics the diagnostics
     */
    public MapResult {
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Tells if an error exists.
     *
     * @return {@code true} if a diagnostic is an error
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
