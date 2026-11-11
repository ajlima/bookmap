package pt.dazz.bookmap.copybook.parse;

import java.util.List;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;

/**
 * Gives the result of the parse step.
 *
 * @param copybook the copybook, or {@code null} if an error exists
 * @param diagnostics all diagnostics of the step
 */
public record ParseResult(Copybook copybook, List<Diagnostic> diagnostics) {

    /**
     * Creates a result.
     *
     * @param copybook the copybook
     * @param diagnostics the diagnostics
     */
    public ParseResult {
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
