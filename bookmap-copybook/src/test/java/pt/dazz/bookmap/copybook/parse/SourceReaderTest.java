package pt.dazz.bookmap.copybook.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Diagnostics;
import pt.dazz.bookmap.copybook.diag.Severity;

class SourceReaderTest {

    private final Diagnostics diagnostics = new Diagnostics();

    private List<SourceLine> fixed(String content) {
        return SourceReader.read("ORDER.cpy", content, SourceFormat.FIXED, diagnostics);
    }

    @Test
    void fixedFormatKeepsColumns8To72() {
        String line = "000100 05  ORDER-ID PIC X(10)." + " ".repeat(42) + "SEQ00001";
        List<SourceLine> lines = fixed(line);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).text()).isEqualTo("05  ORDER-ID PIC X(10)." + " ".repeat(42));
        assertThat(lines.get(0).column()).isEqualTo(8);
        assertThat(lines.get(0).number()).isEqualTo(1);
        assertThat(lines.get(0).continuation()).isFalse();
        assertThat(lines.get(0).source()).isEqualTo("ORDER.cpy");
    }

    @Test
    void fixedFormatSkipsCommentAndDebugLines() {
        List<SourceLine> lines = fixed(String.join("\n",
            "      * A comment",
            "      / A page break",
            "      D 05 DEBUG-ITEM PIC X.",
            "      d 05 DEBUG-ITEM PIC X.",
            "       05 ORDER-ID PIC X(10)."));
        assertThat(lines).extracting(SourceLine::number).containsExactly(5);
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    void fixedFormatMarksContinuation() {
        List<SourceLine> lines = fixed(String.join("\n",
            "       05 NOTE PIC X(20) VALUE 'ABC",
            "      -    'DEF'."));
        assertThat(lines).extracting(SourceLine::continuation).containsExactly(false, true);
    }

    @Test
    void fixedFormatShortAndEmptyLinesAreBlank() {
        List<SourceLine> lines = fixed("\r\n123\r\n      \r\n       \r\n       05 A PIC X.\r\n");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).text()).isEqualTo("05 A PIC X.");
        assertThat(lines.get(0).number()).isEqualTo(5);
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    void fixedFormatAcceptsOldMacLineEnds() {
        assertThat(fixed("       05 A PIC X.\r       05 B PIC X.")).hasSize(2);
    }

    @Test
    void fixedFormatRejectsUnknownIndicator() {
        fixed("      X 05 A PIC X.");
        assertThat(diagnostics.all()).containsExactly(new Diagnostic(Severity.ERROR, "ORDER.cpy",
            1, 7, "unknown indicator 'X' in column 7"));
    }

    @Test
    void fixedFormatRejectsTab() {
        List<SourceLine> lines = fixed("\t05 A PIC X.");
        assertThat(lines).isEmpty();
        assertThat(diagnostics.all()).containsExactly(new Diagnostic(Severity.ERROR, "ORDER.cpy",
            1, 1, "a tab character makes the columns unclear. Replace tabs with spaces."));
    }

    @Test
    void freeFormatStripsInlineComments() {
        List<SourceLine> lines = SourceReader.read("ORDER.cpy", String.join("\n",
            "*> A comment line",
            "   *> An indented comment line",
            "05 ORDER-ID PIC X(10). *> the order",
            "\t05 TABBED PIC X.",
            ""), SourceFormat.FREE, diagnostics);
        assertThat(lines).extracting(SourceLine::text)
            .containsExactly("05 ORDER-ID PIC X(10). ", "\t05 TABBED PIC X.");
        assertThat(lines).extracting(SourceLine::column).containsOnly(1);
        assertThat(lines).extracting(SourceLine::continuation).containsOnly(false);
    }

    @Test
    void diagnosticFormat() {
        Diagnostic diagnostic = new Diagnostic(Severity.ERROR, "order.cpy", 3, 8, "a message");
        assertThat(diagnostic.format()).isEqualTo("order.cpy:3:8: error: a message");
    }

    @Test
    void diagnosticsCollectAllSeverities() {
        Diagnostics all = new Diagnostics();
        assertThat(all.hasErrors()).isFalse();
        all.warning("a", 1, 1, "w");
        all.info("a", 1, 1, "i");
        assertThat(all.hasErrors()).isFalse();
        all.error("a", 1, 1, "e");
        assertThat(all.hasErrors()).isTrue();
        assertThat(all.all()).extracting(Diagnostic::severity)
            .containsExactly(Severity.WARNING, Severity.INFO, Severity.ERROR);
    }
}
