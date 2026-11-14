package pt.dazz.bookmap.copybook.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.cobol.Sign;
import pt.dazz.bookmap.copybook.ast.Condition;
import pt.dazz.bookmap.copybook.ast.ConditionValue;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.Literal;
import pt.dazz.bookmap.copybook.ast.Occurs;
import pt.dazz.bookmap.copybook.ast.QualifiedName;
import pt.dazz.bookmap.copybook.ast.Usage;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Diagnostics;
import pt.dazz.bookmap.copybook.diag.Severity;

class DataParserTest {

    private final Diagnostics diagnostics = new Diagnostics();

    private Copybook parse(String... lines) {
        List<Token> tokens = Lexer.tokenize(SourceReader.read("ORDER.cpy",
            String.join("\n", lines), SourceFormat.FREE, diagnostics), diagnostics);
        return DataParser.parse("ORDER.cpy", tokens, "ORDER", diagnostics);
    }

    private static DataItem child(DataItem item, int index) {
        return item.children().get(index);
    }

    @Test
    void parsesNestedGroups() {
        Copybook copybook = parse(
            "01 GET-ORDER.",
            "   05 HEADER.",
            "      10 ORDER-ID PIC X(10).",
            "      10 CUSTOMER-ID PIC X(8).",
            "   05 TOTAL PIC 9(7).");
        assertThat(diagnostics.all()).isEmpty();
        assertThat(copybook.sourceName()).isEqualTo("ORDER.cpy");
        DataItem record = copybook.records().get(0);
        assertThat(record.name()).isEqualTo("GET-ORDER");
        assertThat(record.isGroup()).isTrue();
        assertThat(record.children()).extracting(DataItem::name)
            .containsExactly("HEADER", "TOTAL");
        assertThat(child(record, 0).children()).extracting(DataItem::name, DataItem::level)
            .containsExactly(tuple("ORDER-ID", 10), tuple("CUSTOMER-ID", 10));
        assertThat(child(record, 1).isGroup()).isFalse();
        assertThat(child(record, 1).picture().length()).isEqualTo(7);
        assertThat(child(record, 1).line()).isEqualTo(5);
        assertThat(child(record, 1).column()).isEqualTo(4);
    }

    @Test
    void parsesMoreThanOneRecord() {
        Copybook copybook = parse("01 REQUEST-AREA. 05 A PIC X.", "01 RESPONSE-AREA. 05 B PIC X.");
        assertThat(copybook.records()).extracting(DataItem::name)
            .containsExactly("REQUEST-AREA", "RESPONSE-AREA");
    }

    @Test
    void parsesOccursFixedAndDependingOn() {
        Copybook copybook = parse(
            "01 ORDERS.",
            "   05 LINE-COUNT PIC 9(2).",
            "   05 FIXED-LINE OCCURS 20 TIMES PIC X(5).",
            "   05 VAR-LINE OCCURS 1 TO 50 DEPENDING ON LINE-COUNT OF ORDERS PIC X(5).",
            "   05 OTHER-LINE OCCURS 9 DEPENDING LINE-COUNT PIC X.");
        assertThat(diagnostics.all()).isEmpty();
        DataItem record = copybook.records().get(0);
        assertThat(child(record, 1).occurs()).isEqualTo(new Occurs(20, 20, null));
        assertThat(child(record, 2).occurs()).isEqualTo(new Occurs(1, 50,
            new QualifiedName(List.of("LINE-COUNT", "ORDERS"))));
        assertThat(child(record, 2).occurs().dependingOn()).hasToString("LINE-COUNT OF ORDERS");
        assertThat(child(record, 3).occurs()).isEqualTo(new Occurs(9, 9,
            new QualifiedName(List.of("LINE-COUNT"))));
    }

    @Test
    void ignoresIndexAndKeyClausesWithInfo() {
        Copybook copybook = parse(
            "01 ORDERS.",
            "   05 ITEM OCCURS 5 ASCENDING KEY IS ITEM-ID INDEXED BY IX1 IX2.",
            "      10 ITEM-ID PIC X(3).",
            "   05 OTHER OCCURS 2 DESCENDING ITEM-ID PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(tuple(Severity.INFO, "ASCENDING is ignored"),
                tuple(Severity.INFO, "INDEXED is ignored"),
                tuple(Severity.INFO, "DESCENDING is ignored"));
        assertThat(child(copybook.records().get(0), 0).occurs().max()).isEqualTo(5);
    }

    @Test
    void parsesRedefines() {
        Copybook copybook = parse(
            "01 ACCOUNT.",
            "   05 CHECKING-DATA.",
            "      10 LIMIT PIC 9(5).",
            "   05 SAVINGS-DATA REDEFINES CHECKING-DATA.",
            "      10 RATE PIC 9V9(4).");
        assertThat(child(copybook.records().get(0), 1).redefines())
            .isEqualTo(new QualifiedName(List.of("CHECKING-DATA")));
    }

    @Test
    void parsesUsageWordsAndGroupUsage() {
        Copybook copybook = parse(
            "01 AMOUNTS USAGE IS COMP-3.",
            "   05 A PIC S9(5).",
            "   05 B PIC 9(4) COMP.",
            "   05 C PIC 9(4) COMPUTATIONAL.",
            "   05 D PIC 9(4) COMP-4.",
            "   05 E PIC 9(4) COMPUTATIONAL-4.",
            "   05 F PIC 9(4) BINARY.",
            "   05 G PIC 9(4) COMPUTATIONAL-3.",
            "   05 H PIC 9(4) PACKED-DECIMAL.",
            "   05 I PIC 9(4) USAGE DISPLAY.");
        assertThat(diagnostics.all()).isEmpty();
        DataItem record = copybook.records().get(0);
        assertThat(record.usage()).isEqualTo(Usage.COMP3);
        assertThat(record.children()).extracting(DataItem::usage).containsExactly(null,
            Usage.COMP, Usage.COMP, Usage.COMP, Usage.COMP, Usage.COMP, Usage.COMP3,
            Usage.COMP3, Usage.DISPLAY);
    }

    @Test
    void keepsConditionsAndIgnoresValues() {
        Copybook copybook = parse(
            "01 ORDER.",
            "   05 STATUS PIC X VALUE 'O'.",
            "      88 STATUS-OPEN VALUE 'O'.",
            "      88 STATUS-CLOSED VALUES ARE 'C' 'X'.",
            "   05 QTY PIC 9(3) VALUE ZEROS.",
            "   05 NOTE PIC X(3) VALUE IS SPACES SYNC.");
        assertThat(diagnostics.all()).isEmpty();
        DataItem status = child(copybook.records().get(0), 0);
        assertThat(status.conditions()).extracting(Condition::name)
            .containsExactly("STATUS-OPEN", "STATUS-CLOSED");
        assertThat(status.conditions().get(1).values()).containsExactly(
            new ConditionValue(text("C"), null), new ConditionValue(text("X"), null));
        assertThat(status.conditions().get(1).line()).isEqualTo(4);
        assertThat(status.conditions().get(1).column()).isEqualTo(7);
        assertThat(status.value()).isEqualTo(text("O"));
        assertThat(child(copybook.records().get(0), 1).value())
            .isEqualTo(new Literal(Literal.Kind.FIGURATIVE, "ZERO"));
        assertThat(child(copybook.records().get(0), 2).value())
            .isEqualTo(new Literal(Literal.Kind.FIGURATIVE, "SPACE"));
        assertThat(child(copybook.records().get(0), 2).declaration())
            .isEqualTo("05 NOTE PIC X(3) SYNC.");
    }

    private static Literal text(String text) {
        return new Literal(Literal.Kind.TEXT, text);
    }

    private static Literal number(String text) {
        return new Literal(Literal.Kind.NUMBER, text);
    }

    private static Literal figurative(String text) {
        return new Literal(Literal.Kind.FIGURATIVE, text);
    }

    @Test
    void keepsRangesNumbersAndFigurativeConstants() {
        Copybook copybook = parse(
            "01 ORDER.",
            "   05 QTY PIC S9(3)V9 VALUE -1.5.",
            "      88 QTY-LOW VALUE IS 0 THRU 9 15 THROUGH 20.",
            "      88 QTY-NONE VALUE ZEROES.",
            "   05 CODE PIC X.",
            "      88 CODE-EMPTY VALUES SPACES LOW-VALUES HIGH-VALUE QUOTES.",
            "      88 CODE-ODD VALUE ZERO SPACE LOW-VALUE HIGH-VALUES QUOTE ZEROS.");
        assertThat(diagnostics.all()).isEmpty();
        DataItem qty = child(copybook.records().get(0), 0);
        assertThat(qty.value()).isEqualTo(number("-1.5"));
        assertThat(qty.conditions().get(0).values()).containsExactly(
            new ConditionValue(number("0"), number("9")),
            new ConditionValue(number("15"), number("20")));
        assertThat(qty.conditions().get(1).values()).containsExactly(
            new ConditionValue(figurative("ZERO"), null));
        DataItem code = child(copybook.records().get(0), 1);
        assertThat(code.conditions().get(0).values()).extracting(v -> v.from().text())
            .containsExactly("SPACE", "LOW-VALUE", "HIGH-VALUE", "QUOTE");
        assertThat(code.conditions().get(1).values()).extracting(v -> v.from().text())
            .containsExactly("ZERO", "SPACE", "LOW-VALUE", "HIGH-VALUE", "QUOTE", "ZERO");
    }

    @Test
    void unsupportedValuesAreWarnings() {
        Copybook copybook = parse(
            "01 ORDER.",
            "   05 CODE PIC X(3) VALUE ALL '-'.",
            "      88 CODE-DASH VALUE ALL '-'.",
            "      88 CODE-NONE.",
            "      88 CODE-HEX VALUE 'A' THRU X'FF'.",
            "      88 CODE-OK VALUE 'A'.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(
                tuple(Severity.WARNING, "the VALUE clause of CODE is not supported and is"
                    + " ignored"),
                tuple(Severity.WARNING, "condition CODE-DASH: the value ALL is not supported."
                    + " The condition is ignored."),
                tuple(Severity.WARNING, "condition CODE-NONE has no VALUE clause and is"
                    + " ignored"),
                tuple(Severity.WARNING, "condition CODE-HEX: the value X'FF' is not supported."
                    + " The condition is ignored."));
        DataItem code = child(copybook.records().get(0), 0);
        assertThat(code.value()).isNull();
        assertThat(code.conditions()).extracting(Condition::name).containsExactly("CODE-OK");
    }

    @Test
    void valuesAtTheEndOfTheTokens() {
        parse("01 ORDER.", "05 CODE PIC X.", "88 CODE-A VALUE 'A' THRU");
        parse("01 ORDER.", "05 CODE PIC X.", "88 CODE-B");
        parse("01 ORDER.", "05 CODE PIC X VALUE");
        parse("01 ORDER.", "05 CODE PIC X.", "88 CODE-D VALUE 'D' THRU .");
        Copybook last = parse("01 ORDER.", "05 CODE PIC X.", "88 CODE-E VALUE 'E'");
        assertThat(diagnostics.all()).extracting(Diagnostic::message).containsExactly(
            "condition CODE-A: the value THRU is not supported. The condition is ignored.",
            "condition CODE-B has no VALUE clause and is ignored",
            "the VALUE clause of CODE is not supported and is ignored",
            "the entry has no closing period",
            "condition CODE-D: the value . is not supported. The condition is ignored.");
        assertThat(child(last.records().get(0), 0).conditions()).extracting(Condition::name)
            .containsExactly("CODE-E");
    }

    @Test
    void normalizedDeclaration() {
        Copybook copybook = parse("01 ORDER.", "   05   ORDER-AMOUNT   PIC S9(7)V99",
            "        COMP-3   VALUE +0.");
        assertThat(child(copybook.records().get(0), 0).declaration())
            .isEqualTo("05 ORDER-AMOUNT PIC S9(7)V99 COMP-3.");
        assertThat(copybook.records().get(0).declaration()).isEqualTo("01 ORDER.");
    }

    @Test
    void lowerCaseAndPicIs() {
        Copybook upper = parse("01 ORDER. 05 ORDER-ID PIC X(10).");
        Copybook lower = new DataParserTest().parse("01 order. 05 order-id pic is x(10).");
        assertThat(child(lower.records().get(0), 0).picture())
            .isEqualTo(child(upper.records().get(0), 0).picture());
        assertThat(child(lower.records().get(0), 0).name()).isEqualTo("ORDER-ID");
    }

    @Test
    void fillerWithoutName() {
        Copybook copybook = parse("01 ORDER. 05 PIC X(3). 05 FILLER PIC X. 05 A PIC X.");
        assertThat(copybook.records().get(0).children())
            .extracting(DataItem::name, DataItem::isFiller)
            .containsExactly(tuple("FILLER", true), tuple("FILLER", true), tuple("A", false));
    }

    @Test
    void syntheticRecordFromRecordName() {
        Copybook copybook = parse("05 ORDER-ID PIC X(10).", "05 TOTAL PIC 9(5).");
        assertThat(diagnostics.all()).isEmpty();
        DataItem record = copybook.records().get(0);
        assertThat(record.name()).isEqualTo("ORDER");
        assertThat(record.level()).isEqualTo(1);
        assertThat(record.declaration()).isEqualTo("01 ORDER.");
        assertThat(record.children()).extracting(DataItem::name)
            .containsExactly("ORDER-ID", "TOTAL");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "05 A PIC S9 SIGN IS SEPARATE.|21|SIGN needs LEADING or TRAILING",
        "05 A PIC S9 SIGN.|17|SIGN needs LEADING or TRAILING",
        "05 A PIC S9 SEPARATE.|13|unknown clause SEPARATE",
        "05 A PIC X SIGN LEADING.|1|SIGN on A needs a signed numeric DISPLAY item",
        "05 A PIC 9 SIGN IS TRAILING SEPARATE CHARACTER.|1|SIGN on A needs a signed numeric"
            + " DISPLAY item",
        "05 A PIC S9 COMP-3 LEADING.|1|SIGN on A needs a signed numeric DISPLAY item",
        "05 A COMP-1.|6|COMP-1 is not supported by BookMap. See issue #46.",
        "05 A COMP-2.|6|COMP-2 is not supported by BookMap. See issue #46.",
        "05 A USAGE COMPUTATIONAL-2.|12|COMPUTATIONAL-2 is not supported by BookMap."
            + " See issue #46.",
        "05 A POINTER.|6|POINTER is not supported by BookMap: its size depends on the compiler option LP. Replace it with PIC X(4) or PIC X(8), for example with the REPLACE statement.",
        "05 A PIC X NATIONAL.|12|NATIONAL is not supported by BookMap. See issue #49.",
        "05 A PIC N(3) NATIONAL.|10|picture symbol N is not supported by BookMap."
            + " See issue #49.",
        "05 A PIC X GLOBAL.|12|unknown clause GLOBAL",
        "05 A PIC Z*9.|10|edited picture Z*9: Z, *, and a floating string are mutually exclusive",
        "05 A PIC 9 BLANK WHEN 7.|23|BLANK needs WHEN ZERO",
        "05 A.|1|A has no PIC",
        "05 A OCCURS X.|13|OCCURS needs a number",
        "05 A OCCURS 1 TO X.|18|OCCURS needs a number",
        "05 A OCCURS 3 DEPENDING ON 7.|28|DEPENDING ON needs a data name",
        "05 A REDEFINES 'B'.|16|REDEFINES needs a data name",
        "05 A PIC.|9|PIC needs a picture string",
        "05 A USAGE 'X'.|12|USAGE needs a usage word",
    })
    void rejectsUnsupportedClauses(String entry, int column, String message) {
        parse("01 ORDER.", entry);
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::column, Diagnostic::message)
            .containsExactly(tuple(Severity.ERROR, 2, column, message));
    }

    @Test
    void groupWithPicIsAnError() {
        parse("01 ORDER PIC X(5).", "   05 A PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::message)
            .containsExactly("ORDER is a group and must not have a PIC");
    }

    @Test
    void rejectsBadLevels() {
        parse("01 ORDER. 05 A PIC X.", "50 D PIC X.", "ORDER-ID PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::line, Diagnostic::message)
            .containsExactly(tuple(2, "level 50 is not valid"),
                tuple(3, "an entry must start with a level number"));
    }

    @Test
    void level66IsIgnoredWithInfo() {
        Copybook copybook = parse("01 ORDER. 05 A PIC X. 05 B PIC X.",
            "66 AB RENAMES A THRU B.", "88 B-YES VALUE 'Y'.", "66");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(
                tuple(Severity.INFO, 2, "level 66 item AB is ignored. RENAMES adds no bytes."),
                tuple(Severity.INFO, 4, "level 66 item  is ignored. RENAMES adds no bytes."));
        DataItem record = copybook.records().get(0);
        assertThat(record.children()).extracting(DataItem::name).containsExactly("A", "B");
        assertThat(child(record, 1).conditions()).extracting(Condition::name)
            .containsExactly("B-YES");
    }

    @Test
    void level77IsSkippedWithWarning() {
        Copybook copybook = parse("77 COUNTER PIC 9(4) COMP.", "88 DONE VALUE 0.",
            "05 A PIC X.", "77 OTHER PIC X.", "01 NEXT-REC. 05 B PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(
                tuple(Severity.WARNING, 1, "level 77 item COUNTER is not part of a record and"
                    + " is skipped"),
                tuple(Severity.WARNING, 4, "level 77 item OTHER is not part of a record and"
                    + " is skipped"));
        assertThat(copybook.records()).extracting(DataItem::name)
            .containsExactly("ORDER", "NEXT-REC");
        assertThat(child(copybook.records().get(0), 0).name()).isEqualTo("A");
        assertThat(child(copybook.records().get(0), 0).conditions()).isEmpty();
    }

    @Test
    void itemAfterLevel77NeedsALevel01() {
        Copybook copybook = parse("01 ORDER. 05 A PIC X.", "77 C PIC X.", "05 D PIC X.",
            "88 D-YES VALUE 'Y'.", "05");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(
                tuple(Severity.WARNING, 2, "level 77 item C is not part of a record and"
                    + " is skipped"),
                tuple(Severity.ERROR, 3, "level 05 item D must follow a level 01 item, not a"
                    + " level 77 item"),
                tuple(Severity.ERROR, 5, "level 05 item  must follow a level 01 item, not a"
                    + " level 77 item"));
        assertThat(copybook.records().get(0).children()).extracting(DataItem::name)
            .containsExactly("A");
    }

    @Test
    void onlyLevel77IsAnEmptyCopybook() {
        parse("77 C PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity)
            .containsExactly(Severity.WARNING, Severity.ERROR);
    }

    @Test
    void acceptsSyncJustifiedBlankAndComp5() {
        Copybook copybook = parse("01 ORDER.",
            "05 A PIC X(3) SYNC LEFT.", "05 B PIC X(3) SYNCHRONIZED RIGHT.", "05 C PIC X SYNC.",
            "05 D PIC X(5) JUSTIFIED RIGHT.", "05 E PIC X(5) JUST.",
            "05 F PIC 9(3) BLANK WHEN ZERO.", "05 G PIC 9(3) BLANK ZEROS.",
            "05 H PIC 9(3) BLANK ZEROES.",
            "05 I PIC 9(4) COMP-5.", "05 J PIC S9(4) USAGE IS COMPUTATIONAL-5.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::column, Diagnostic::message)
            .containsExactly(
                tuple(Severity.WARNING, 5, 15, "JUSTIFIED on D is ignored. BookMap writes the"
                    + " text on the left and keeps the spaces on the left when it reads."),
                tuple(Severity.WARNING, 6, 15, "JUSTIFIED on E is ignored. BookMap writes the"
                    + " text on the left and keeps the spaces on the left when it reads."));
        DataItem record = copybook.records().get(0);
        assertThat(record.children()).extracting(DataItem::name)
            .containsExactly("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");
        assertThat(child(record, 0).declaration()).isEqualTo("05 A PIC X(3) SYNC LEFT.");
        assertThat(child(record, 1).declaration())
            .isEqualTo("05 B PIC X(3) SYNCHRONIZED RIGHT.");
        assertThat(child(record, 3).declaration()).isEqualTo("05 D PIC X(5) JUSTIFIED RIGHT.");
        assertThat(child(record, 5).declaration()).isEqualTo("05 F PIC 9(3) BLANK WHEN ZERO.");
        assertThat(child(record, 5).picture().edited()).hasToString("9(3) BLANK WHEN ZERO");
        assertThat(child(record, 6).picture().edited().blankWhenZero()).isTrue();
        assertThat(child(record, 4).picture().edited()).isNull();
        assertThat(child(record, 8).usage()).isEqualTo(Usage.COMP5);
        assertThat(child(record, 9).usage()).isEqualTo(Usage.COMP5);
    }

    @Test
    void indexItemIsAnIntegerWithInfo() {
        Copybook copybook = parse("01 ORDER.", "05 IDX INDEX.", "05 POSITIONS USAGE IS INDEX.",
            "10 FIRST-POS.", "10 LAST-POS.", "10 LABEL PIC X(4) DISPLAY.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(
                tuple(Severity.INFO, 2, "INDEX item IDX is mapped as PIC S9(9) COMP. Its value"
                    + " is a byte offset in a table."),
                tuple(Severity.INFO, 4, "INDEX item FIRST-POS is mapped as PIC S9(9) COMP."
                    + " Its value is a byte offset in a table."),
                tuple(Severity.INFO, 5, "INDEX item LAST-POS is mapped as PIC S9(9) COMP."
                    + " Its value is a byte offset in a table."));
        DataItem index = child(copybook.records().get(0), 0);
        assertThat(index.picture().text()).isEqualTo("S9(9)");
        assertThat(index.usage()).isEqualTo(Usage.COMP);
        assertThat(index.declaration()).isEqualTo("05 IDX INDEX.");
        assertThat(child(child(copybook.records().get(0), 1), 2).picture().text())
            .isEqualTo("X(4)");
    }

    @Test
    void indexItemErrors() {
        parse("01 ORDER.", "05 A INDEX PIC X.", "05 B INDEX OCCURS X.",
            "05 C INDEX.", "10 D COMP.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(
                tuple(Severity.ERROR, 3, "OCCURS needs a number"),
                tuple(Severity.ERROR, 2, "the INDEX item A must not have a PIC"),
                tuple(Severity.ERROR, 5, "D has no PIC"));
    }

    @Test
    void conditionWithoutItemIsAnError() {
        parse("88 LONELY VALUE 'Y'.");
        assertThat(diagnostics.all()).extracting(Diagnostic::message)
            .containsExactly("a level 88 condition needs an item before it",
                "the copybook has no data items");
    }

    @Test
    void entryWithoutPeriodIsAnError() {
        Copybook copybook = parse("01 ORDER. 05 A PIC X");
        assertThat(diagnostics.all()).extracting(Diagnostic::message)
            .containsExactly("the entry has no closing period");
        assertThat(child(copybook.records().get(0), 0).name()).isEqualTo("A");
    }

    @Test
    void recordRedefinesIsAWarning() {
        Copybook copybook = parse("01 REQUEST-AREA. 05 A PIC X.",
            "01 RESPONSE-AREA REDEFINES REQUEST-AREA. 05 B PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(tuple(Severity.WARNING,
                "record RESPONSE-AREA redefines record REQUEST-AREA. Both records are kept."));
        assertThat(copybook.records().get(1).redefines()).isNull();
    }

    @Test
    void emptyCopybookIsAnError() {
        Copybook copybook = parse("");
        assertThat(copybook.records()).isEmpty();
        assertThat(diagnostics.all()).extracting(Diagnostic::line, Diagnostic::message)
            .containsExactly(tuple(1, "the copybook has no data items"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "00 B PIC X.|level 0 is not valid",
        "1.5 B PIC X.|an entry must start with a level number",
        "05 'X' PIC X.|unknown clause X",
        "05 B USAGE FOO.|USAGE needs a usage word",
        "05 POINTER.|POINTER is not supported by BookMap: its size depends on the compiler option LP. Replace it with PIC X(4) or PIC X(8), for example with the REPLACE statement.",
        "05 B REDEFINES PIC X.|REDEFINES needs a data name",
        "05 B OCCURS 2 DEPENDING ON PIC X.|DEPENDING ON needs a data name",
    })
    void rejectsIncompleteClauses(String entry, String message) {
        parse("01 A. 05 Z PIC X.", entry);
        assertThat(diagnostics.all()).extracting(Diagnostic::message).containsExactly(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "01 A. 05|the entry has no closing period;FILLER has no PIC",
        "01 A. 05 B USAGE|USAGE needs a usage word",
        "01 A. 05 B REDEFINES|REDEFINES needs a data name",
        "01 A. 05 B PIC|PIC needs a picture string",
        "01 A. 05 B OCCURS 2 DEPENDING ON|DEPENDING ON needs a data name",
        "01 A. 05 B PIC X VALUE 'A'|the entry has no closing period",
        "01 A. 05 B PIC X SYNC|the entry has no closing period",
        "01 A. 05 B PIC 9 BLANK|BLANK needs WHEN ZERO",
        "01 A PIC X. 88|",
    })
    void reportsEntriesThatEndEarly(String text, String messages) {
        parse(text);
        assertThat(diagnostics.all()).extracting(Diagnostic::message).containsExactly(
            messages == null ? new String[0] : messages.split(";"));
    }

    @Test
    void acceptsLessCommonClauseForms() {
        Copybook copybook = parse("01 A.", "05 B PICTURE S9 VALUES 1 TRAILING.",
            "05 C OCCURS 3 DEPENDING ON N IN A PIC X.", "05 COMP-3 PIC 9.");
        assertThat(diagnostics.all()).isEmpty();
        QualifiedName count = child(copybook.records().get(0), 1).occurs().dependingOn();
        assertThat(count.name()).isEqualTo("N");
        assertThat(count.parts()).containsExactly("N", "A");
    }

    private List<String> messages() {
        return diagnostics.all().stream().map(Diagnostic::message).toList();
    }

    @Test
    void syncAddsSlackBytesBeforeABinaryItem() {
        Copybook copybook = parse("01 ORDER.", "05 A PIC X.", "05 B PIC S9(4) COMP SYNC.",
            "05 C PIC X.", "05 D PIC 9(5) BINARY SYNC.", "05 E PIC 9(18) COMP-5 SYNC.",
            "05 F PIC 9(4) COMP SYNC.", "05 G PIC S9(7)V99 COMP-3 SYNC.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(
                tuple(Severity.INFO, 3, "SYNC adds 1 slack byte before B. The generator adds"
                    + " a FILLER."),
                tuple(Severity.INFO, 5, "SYNC adds 3 slack bytes before D. The generator adds"
                    + " a FILLER."));
        DataItem record = copybook.records().get(0);
        assertThat(record.children()).extracting(DataItem::name)
            .containsExactly("A", "FILLER", "B", "C", "FILLER", "D", "E", "F", "G");
        DataItem slack = child(record, 1);
        assertThat(slack.picture().text()).isEqualTo("X(1)");
        assertThat(slack.usage()).isEqualTo(Usage.DISPLAY);
        assertThat(slack.level()).isEqualTo(5);
        assertThat(slack.line()).isEqualTo(3);
        assertThat(slack.declaration())
            .isEqualTo("05 FILLER PIC X(1). Slack bytes before B for SYNC.");
        assertThat(child(record, 4).picture().text()).isEqualTo("X(3)");
    }

    @Test
    void syncOnAGroupAppliesToItsItems() {
        Copybook copybook = parse("01 ORDER.", "05 A PIC X.", "05 G SYNC.", "10 B PIC X.",
            "10 C PIC 9(4) COMP.", "05 H COMP SYNC.", "10 D PIC 9(4).", "10 E PIC X DISPLAY.",
            "10 F PIC 9(9).");
        assertThat(messages()).containsExactly(
            "SYNC adds 1 slack byte before F. The generator adds a FILLER.");
        DataItem record = copybook.records().get(0);
        assertThat(child(record, 1).children()).extracting(DataItem::name)
            .containsExactly("B", "C");
        assertThat(child(record, 2).children()).extracting(DataItem::name)
            .containsExactly("D", "E", "FILLER", "F");
    }

    @Test
    void syncFollowsTheRedefinesOffsets() {
        Copybook copybook = parse("01 ORDER.", "05 X PIC X.", "05 G1.", "10 A PIC X(3).",
            "05 G2 REDEFINES G1.", "10 B PIC 9(4) COMP SYNC.", "05 G3 REDEFINES G1.",
            "10 C PIC X(5).", "05 D PIC 9(9) COMP SYNC.", "05 E PIC X(2).",
            "05 F REDEFINES E PIC 9(4) COMP SYNC.", "05 H REDEFINES MISSING PIC X.");
        assertThat(messages()).containsExactly(
            "SYNC adds 1 slack byte before B. The generator adds a FILLER.",
            "SYNC adds 2 slack bytes before D. The generator adds a FILLER.");
        DataItem record = copybook.records().get(0);
        assertThat(child(record, 2).children()).extracting(DataItem::name)
            .containsExactly("FILLER", "B");
        assertThat(record.children()).extracting(DataItem::name)
            .containsExactly("X", "G1", "G2", "G3", "FILLER", "D", "E", "F", "H");
    }

    @Test
    void syncOnARedefinesItemThatNeedsSlackBytesIsAnError() {
        parse("01 ORDER.", "05 A PIC X.", "05 B PIC X(2).",
            "05 C REDEFINES B PIC 9(4) COMP SYNC.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(tuple(Severity.ERROR, 4, "SYNC on C needs slack bytes, but C has"
                + " REDEFINES. Align the item by hand."));
    }

    @Test
    void syncInATableNeedsNoSlackBytes() {
        Copybook copybook = parse("01 ORDER.", "05 X PIC X.",
            "05 T PIC 9(4) COMP SYNC OCCURS 3.", "05 U OCCURS 2.", "10 A PIC 9(4) COMP SYNC.",
            "10 B PIC X(2).", "10 V OCCURS 2.", "15 C PIC X(2).");
        assertThat(messages()).containsExactly(
            "SYNC adds 1 slack byte before T. The generator adds a FILLER.");
        assertThat(copybook.records().get(0).children()).extracting(DataItem::name)
            .containsExactly("X", "FILLER", "T", "U");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "05 T OCCURS 3. 10 A PIC X. 10 B PIC 9(4) COMP SYNC.|B",
        "05 T OCCURS 3. 10 B PIC 9(4) COMP SYNC. 10 A PIC X.|B",
        "05 X PIC X. 05 T OCCURS 3. 10 B PIC 9(9) COMP SYNC.|B",
    })
    void syncInATableThatNeedsSlackBytesIsAnError(String text, String item) {
        parse("01 ORDER.", text);
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(tuple(Severity.ERROR, "SYNC on " + item + " in OCCURS T needs"
                + " slack bytes. BookMap cannot add them in a table. Align the item by hand."));
    }

    @Test
    void syncWithoutLevel01AssumesADoublewordBoundary() {
        Copybook copybook = parse("05 A PIC X.", "05 B PIC 9(4) COMP SYNC.",
            "05 C PIC 9(4) COMP SYNC.");
        assertThat(diagnostics.all()).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(
                tuple(Severity.WARNING, "the copybook has no level 01. SYNC assumes that ORDER"
                    + " starts on a doubleword boundary."),
                tuple(Severity.INFO, "SYNC adds 1 slack byte before B. The generator adds a"
                    + " FILLER."));
        assertThat(copybook.records().get(0).children()).hasSize(4);
    }

    @Test
    void syncIsIgnoredForTextAndForAnElementaryRecord() {
        Copybook copybook = parse("01 ORDER.", "05 A PIC X.", "05 B PIC X(4) COMP SYNC.",
            "01 COUNTER PIC 9(4) COMP SYNC.");
        assertThat(diagnostics.all()).isEmpty();
        assertThat(copybook.records().get(0).children()).hasSize(2);
    }

    @Test
    void blankWhenZeroOnASignedOrTextItemIsAnError() {
        parse("01 INVOICE.", "05 TOTAL PIC S9(3) BLANK WHEN ZERO.",
            "05 CODE BLANK ZERO PIC X(3).", "05 GROUP-A BLANK WHEN ZERO.", "10 B PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::line, Diagnostic::column,
                Diagnostic::message)
            .containsExactly(
                tuple(2, 20, "BLANK WHEN ZERO on TOTAL: edited picture S9(3): BLANK WHEN ZERO"
                    + " is not permitted with S or '*'"),
                tuple(3, 9, "BLANK WHEN ZERO on CODE: BLANK WHEN ZERO needs a numeric"
                    + " picture"));
    }

    @Test
    void layoutIsSkippedAfterAnError() {
        Copybook copybook = parse("01 ORDER.", "05 A PIC X.", "05 B PIC 9(4) COMP SYNC.",
            "05 C PIC Z*9.");
        assertThat(messages()).containsExactly(
            "edited picture Z*9: Z, *, and a floating string are mutually exclusive");
        assertThat(copybook.records().get(0).children()).hasSize(3);
    }

    @Test
    void signClauseOfAGroupGoesToItsSignedDisplayNumbers() {
        Copybook copybook = parse(
            "01 ORDER.",
            "   05 AMOUNTS SIGN IS LEADING SEPARATE CHARACTER.",
            "      10 NET PIC S9(5)V99.",
            "      10 TAX PIC S9(3) SIGN TRAILING.",
            "      10 QTY PIC 9(3).",
            "      10 PACKED PIC S9(5) COMP-3.",
            "      10 CODE PIC X(2).",
            "      10 RATES.",
            "         15 RATE PIC S9V99.",
            "   05 TOTAL PIC S9(5) TRAILING SEPARATE.",
            "   05 DISCOUNT PIC S9(3) LEADING.",
            "   05 PLAIN PIC S9(3).");
        assertThat(diagnostics.all()).isEmpty();
        DataItem record = copybook.records().get(0);
        DataItem amounts = child(record, 0);
        assertThat(amounts.sign()).isNull();
        assertThat(amounts.declaration())
            .isEqualTo("05 AMOUNTS SIGN IS LEADING SEPARATE CHARACTER.");
        assertThat(amounts.children()).extracting(DataItem::sign).containsExactly(
            Sign.LEADING_SEPARATE, null, null, null, null, null);
        assertThat(child(child(amounts, 5), 0).sign()).isEqualTo(Sign.LEADING_SEPARATE);
        assertThat(record.children()).extracting(DataItem::sign).containsExactly(
            null, Sign.TRAILING_SEPARATE, Sign.LEADING, null);
        assertThat(child(record, 1).declaration())
            .isEqualTo("05 TOTAL PIC S9(5) TRAILING SEPARATE.");
    }

    @Test
    void separateSignMovesTheSlackBytesOfSync() {
        Copybook copybook = parse("01 ORDER.", "05 A PIC S9(2) SIGN LEADING SEPARATE.",
            "05 B PIC S9(4) COMP SYNC.");
        assertThat(diagnostics.all()).extracting(Diagnostic::message).containsExactly(
            "SYNC adds 1 slack byte before B. The generator adds a FILLER.");
    }
}
