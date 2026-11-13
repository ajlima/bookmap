package pt.dazz.bookmap.copybook.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.diag.Diagnostic;

class CopybookParserTest {

    @TempDir
    Path dir;

    private Path write(String name, String... lines) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, String.join("\n", lines));
        return file;
    }

    private ParseOptions free() {
        return new ParseOptions(SourceFormat.FREE, StandardCharsets.UTF_8, List.of(dir), null);
    }

    private ParseResult parse(String... lines) {
        return CopybookParser.parse("ORDER.cpy", String.join("\n", lines), free());
    }

    private static List<String> names(ParseResult result) {
        return result.copybook().records().get(0).children().stream().map(DataItem::name)
            .toList();
    }

    @Test
    void defaultsAreFixedFormatAndUtf8() {
        ParseOptions defaults = ParseOptions.defaults();
        assertThat(defaults.format()).isEqualTo(SourceFormat.FIXED);
        assertThat(defaults.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(defaults.copyDirs()).isEmpty();
        assertThat(defaults.recordName()).isNull();
    }

    @Test
    void parseFileUsesFileNameAsRecordName() throws IOException {
        Path file = write("order-item.cpy", "       05 SKU PIC X(8).");
        ParseResult result = CopybookParser.parse(file, ParseOptions.defaults());
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.copybook().sourceName()).isEqualTo("order-item.cpy");
        assertThat(result.copybook().records().get(0).name()).isEqualTo("ORDER-ITEM");
    }

    @Test
    void parseFileWithoutExtensionAndExplicitRecordName() throws IOException {
        Path file = write("ORDITEM", "       05 SKU PIC X(8).");
        assertThat(CopybookParser.parse(file, ParseOptions.defaults()).copybook().records()
            .get(0).name()).isEqualTo("ORDITEM");
        ParseOptions named = new ParseOptions(SourceFormat.FIXED, StandardCharsets.UTF_8,
            List.of(), "LINE-ITEM");
        assertThat(CopybookParser.parse(file, named).copybook().records().get(0).name())
            .isEqualTo("LINE-ITEM");
    }

    @Test
    void parseMissingFileIsAnError() {
        ParseResult result = CopybookParser.parse(dir.resolve("NONE.cpy"),
            ParseOptions.defaults());
        assertThat(result.copybook()).isNull();
        assertThat(result.diagnostics()).extracting(Diagnostic::source, Diagnostic::line)
            .containsExactly(tuple("NONE.cpy", 1));
        assertThat(result.diagnostics().get(0).message()).startsWith("cannot read NONE.cpy");
    }

    @Test
    void warningsKeepTheCopybook() {
        ParseResult result = parse("01 A. 05 X PIC X.", "01 B REDEFINES A. 05 Y PIC X.");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.copybook()).isNotNull();
    }

    @Test
    void parseResultHasNoCopybookOnError() {
        ParseResult result = parse("01 ORDER. 05 A PIC Z*9.");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.copybook()).isNull();
    }

    @Test
    void copyIncludesMember() throws IOException {
        write("ADDRESS.cpy", "05 STREET PIC X(30).", "05 CITY PIC X(20).");
        ParseResult result = parse("01 CUSTOMER.", "05 NAME PIC X(20).", "COPY ADDRESS.",
            "05 PHONE PIC X(15).");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(names(result)).containsExactly("NAME", "STREET", "CITY", "PHONE");
        assertThat(result.copybook().records().get(0).children().get(1).source())
            .isEqualTo("ADDRESS.cpy");
    }

    @Test
    void copyFindsExtensionsCaseInsensitive() throws IOException {
        write("phone.CBL", "05 PHONE PIC X(15).");
        write("fax.cob", "05 FAX PIC X(15).");
        write("EMAIL", "05 EMAIL PIC X(40).");
        assertThat(names(parse("01 CUSTOMER.", "COPY PHONE.", "COPY 'fax'.", "COPY EMAIL.")))
            .containsExactly("PHONE", "FAX", "EMAIL");
    }

    @Test
    void copyOfLibraryIsIgnored() throws IOException {
        write("ADDRESS.cpy", "05 CITY PIC X(20).");
        assertThat(names(parse("01 CUSTOMER.", "COPY ADDRESS OF COMMONLIB.",
            "COPY ADDRESS IN COMMONLIB."))).containsExactly("CITY", "CITY");
    }

    @Test
    void replacingWords() throws IOException {
        write("ADDRESS.cpy", "05 STREET PIC X(30).", "05 CITY PIC X(20).");
        assertThat(names(parse("01 CUSTOMER.", "COPY ADDRESS REPLACING CITY BY TOWN.")))
            .containsExactly("STREET", "TOWN");
    }

    @Test
    void replacingPseudoTextSequence() throws IOException {
        write("AMOUNT.cpy", "05 AMOUNT PIC 9(5).");
        ParseResult result = parse("01 INVOICE.",
            "COPY AMOUNT REPLACING ==PIC 9(5)== BY ==PIC S9(7)V99 COMP-3==.");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.copybook().records().get(0).children().get(0).declaration())
            .isEqualTo("05 AMOUNT PIC S9(7)V99 COMP-3.");
    }

    @Test
    void replacingWithEmptyPseudoTextRemovesTheText() throws IOException {
        write("AMOUNT.cpy", "05 AMOUNT PIC 9(5) COMP-3.");
        ParseResult result = parse("01 INVOICE.", "COPY AMOUNT REPLACING ==COMP-3== BY ====.");
        assertThat(result.copybook().records().get(0).children().get(0).usage()).isNull();
    }

    @Test
    void replacingTagInsideWords() throws IOException {
        write("ADDRESS.cpy", "05 :PFX:-ADDRESS.", "   10 :PFX:-CITY PIC X(20).",
            "   10 :PFX:-ZIP-:PFX: PIC X(9).");
        ParseResult result = parse("01 SHIPMENT.",
            "COPY ADDRESS REPLACING ==:PFX:== BY ==SHIP== ==NONE== BY ==ZERO==.");
        assertThat(result.diagnostics()).isEmpty();
        DataItem address = result.copybook().records().get(0).children().get(0);
        assertThat(address.name()).isEqualTo("SHIP-ADDRESS");
        assertThat(address.children()).extracting(DataItem::name)
            .containsExactly("SHIP-CITY", "SHIP-ZIP-SHIP");
    }

    @Test
    void nestedCopy() throws IOException {
        write("CUSTOMER.cpy", "05 NAME PIC X(20).", "COPY ADDRESS.");
        write("ADDRESS.cpy", "05 CITY PIC X(20).");
        assertThat(names(parse("01 ORDER.", "COPY CUSTOMER."))).containsExactly("NAME", "CITY");
    }

    @Test
    void copyCycleIsAnError() throws IOException {
        write("A.cpy", "COPY B.");
        write("B.cpy", "COPY A.");
        ParseResult result = parse("01 ORDER. 05 X PIC X.", "COPY A.");
        assertThat(result.diagnostics()).extracting(Diagnostic::source, Diagnostic::message)
            .containsExactly(tuple("B.cpy", "COPY cycle: A -> B -> A"));
    }

    @Test
    void missingMemberIsAnError() {
        ParseResult result = parse("01 ORDER. 05 X PIC X.", "COPY NOTHERE.");
        assertThat(result.diagnostics()).extracting(Diagnostic::line, Diagnostic::message)
            .containsExactly(tuple(2, "copy member 'NOTHERE' not found in " + dir));
        ParseResult noDirs = CopybookParser.parse("ORDER.cpy", "01 ORDER. 05 X PIC X. COPY A.",
            new ParseOptions(SourceFormat.FREE, StandardCharsets.UTF_8, List.of(), null));
        assertThat(noDirs.diagnostics()).extracting(Diagnostic::message)
            .containsExactly("copy member 'A' not found: no copy directory is set");
    }

    @Test
    void missingCopyDirectoryIsSkipped() throws IOException {
        write("ADDRESS.cpy", "05 CITY PIC X(20).");
        ParseResult result = CopybookParser.parse("ORDER.cpy", "01 C. COPY ADDRESS.",
            new ParseOptions(SourceFormat.FREE, StandardCharsets.UTF_8,
                List.of(dir.resolve("absent"), Files.createDirectory(dir.resolve("empty")), dir),
                null));
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void unreadableMemberIsAnError() throws IOException {
        Files.createDirectory(dir.resolve("BROKEN.cpy"));
        ParseResult result = parse("01 ORDER. 05 X PIC X.", "COPY BROKEN.");
        assertThat(result.diagnostics()).extracting(Diagnostic::message).singleElement()
            .asString().startsWith("cannot read copy member BROKEN");
    }

    @Test
    void badCopyStatementsAreErrors() {
        ParseResult result = parse("01 ORDER. 05 X PIC X.", "COPY.", "COPY A REPLACING B.",
            "COPY A REPLACING B BY.", "COPY A X.", "COPY A");
        assertThat(result.diagnostics()).extracting(Diagnostic::line, Diagnostic::message)
            .containsExactly(tuple(2, "COPY needs a member name"),
                tuple(3, "REPLACING needs BY"),
                tuple(4, "REPLACING needs a value after BY"),
                tuple(5, "the COPY statement has an unknown word X"),
                tuple(6, "the COPY statement has no closing period"));
    }

    @Test
    void replaceStatementChangesTheTextAfterIt() throws IOException {
        write("PARTY.cpy", "05 :PFX:-NAME PIC X(20).", "05 :PFX:-SIZE PIC 9(4) POINTER.");
        ParseResult result = parse("01 ORDER.", "05 :PFX:-ID PIC X.",
            "REPLACE ==:PFX:== BY ==SENDER== ==POINTER== BY ==COMP==.", "COPY PARTY.",
            "REPLACE ==:PFX:== BY ==RECEIVER== ==POINTER== BY ==COMP==.", "COPY PARTY.",
            "REPLACE OFF.",
            "05 :PFX:-CODE PIC X.");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(names(result)).containsExactly(":PFX:-ID", "SENDER-NAME", "SENDER-SIZE",
            "RECEIVER-NAME", "RECEIVER-SIZE", ":PFX:-CODE");
        assertThat(result.copybook().records().get(0).children().get(2).declaration())
            .isEqualTo("05 SENDER-SIZE PIC 9(4) COMP.");
    }

    @Test
    void replaceStatementAppliesAfterCopyReplacing() throws IOException {
        write("PARTY.cpy", "05 :PFX:-NAME PIC X(20).");
        ParseResult result = parse("REPLACE ==SENDER-NAME== BY ==SHIPPER-NAME==.", "01 ORDER.",
            "COPY PARTY REPLACING ==:PFX:== BY ==SENDER==.", "05 SENDER-NAME PIC X.");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(names(result)).containsExactly("SHIPPER-NAME", "SHIPPER-NAME");
    }

    @Test
    void badReplaceStatementsAreErrors() {
        ParseResult result = parse("01 ORDER. 05 X PIC X.", "REPLACE A BY ==B==.",
            "REPLACE ==A== ==B==.", "REPLACE ==A== BY B.", "REPLACE == == BY ==B==.",
            "REPLACE OFF X.", "REPLACE ALSO ==A== BY ==B==.", "REPLACE ==A== BY ==B==");
        assertThat(result.diagnostics()).extracting(Diagnostic::line, Diagnostic::message)
            .containsExactly(
                tuple(2, "REPLACE needs pseudo-text, for example REPLACE ==A== BY ==B==."),
                tuple(3, "REPLACE needs BY"),
                tuple(4, "REPLACE needs pseudo-text, for example REPLACE ==A== BY ==B==."),
                tuple(5, "REPLACE needs a text to replace"),
                tuple(6, "the REPLACE statement has an unknown word X"),
                tuple(7, "REPLACE needs pseudo-text, for example REPLACE ==A== BY ==B==."),
                tuple(8, "the REPLACE statement has no closing period"));
    }

    @Test
    void replaceStatementWithoutPeriodStopsTheReplacements() {
        ParseResult result = parse("01 ORDER. 05 X PIC X.", "REPLACE OFF");
        assertThat(result.diagnostics()).extracting(Diagnostic::message)
            .containsExactly("the REPLACE statement has no closing period");
    }

    @Test
    void memberDiagnosticsUseMemberName() throws IOException {
        write("BAD.cpy", "05 A PIC Z*9.");
        ParseResult result = parse("01 ORDER.", "COPY BAD.");
        assertThat(result.diagnostics()).extracting(Diagnostic::source, Diagnostic::line)
            .containsExactly(tuple("BAD.cpy", 1));
    }
}
