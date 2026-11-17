package pt.dazz.copymapper.copybook.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.ast.Usage;
import pt.dazz.copymapper.copybook.parse.CopybookParser;
import pt.dazz.copymapper.copybook.parse.ParseOptions;
import pt.dazz.copymapper.copybook.parse.SourceFormat;

class MappingSpecsTest {

    private static MappingSpec draft(String record, String... lines) {
        var result = CopybookParser.parse("ORDER.cpy", String.join("\n", lines),
                new ParseOptions(SourceFormat.FREE, StandardCharsets.UTF_8, List.of(), null));
        assertThat(result.copybook()).as(result.diagnostics().toString()).isNotNull();
        return MappingSpecs.draft(result.copybook(), record, DraftOptions.defaults());
    }

    private static SpecNode find(SpecNode node, String path) {
        if (node.path().equals(path)) {
            return node;
        }
        for (SpecNode child : node.children()) {
            SpecNode found = find(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static SpecNode at(MappingSpec spec, String path) {
        SpecNode found = find(spec.root(), path);
        assertThat(found).as(path).isNotNull();
        return found;
    }

    @Test
    void selectsTheRecordByName() {
        var spec = draft("b-rec", "01 A-REC.", "05 X PIC X.", "01 B-REC.", "05 Y PIC X.");
        assertThat(spec.root().path()).isEqualTo("B-REC");
        assertThat(spec.sourceName()).isEqualTo("ORDER.cpy");
        assertThat(spec.charset()).isEqualTo("IBM037");
        assertThat(spec.sourceHash()).hasSize(64);
    }

    @Test
    void unknownRecordFails() {
        assertThatThrownBy(() -> draft("NOPE", "01 A-REC.", "05 X PIC X.", "01 B-REC.",
                "05 Y PIC X."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOPE").hasMessageContaining("A-REC")
                .hasMessageContaining("B-REC");
    }

    @Test
    void duplicateRecordFails() {
        assertThatThrownBy(() -> draft("A-REC", "01 A-REC.", "05 X PIC X.", "01 A-REC.",
                "05 Y PIC X."))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("A-REC");
    }

    @Test
    void buildsPathsAndJsonNames() {
        var spec = draft("GET-ACCOUNT", "01 GET-ACCOUNT.", "05 ACCOUNT-ID PIC X(12).",
                "05 BALANCE PIC 9(7)V99 COMP-3.");
        var root = spec.root();
        assertThat(root.path()).isEqualTo("GET-ACCOUNT");
        assertThat(root.jsonName()).isEqualTo("getAccount");
        assertThat(root.jsonType()).isNull();
        assertThat(root.direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
        var id = at(spec, "GET-ACCOUNT.ACCOUNT-ID");
        assertThat(id.jsonName()).isEqualTo("accountId");
        assertThat(id.jsonType()).isEqualTo(JsonType.TEXT);
        assertThat(id.usage()).isEqualTo(Usage.DISPLAY);
        assertThat(id.picture()).isEqualTo("X(12)");
        var balance = at(spec, "GET-ACCOUNT.BALANCE");
        assertThat(balance.jsonName()).isEqualTo("balance");
        assertThat(balance.jsonType()).isEqualTo(JsonType.NUMBER);
        assertThat(balance.usage()).isEqualTo(Usage.COMP3);
        assertThat(balance.direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
        assertThat(balance.table()).isNull();
        assertThat(balance.counter()).isNull();
        assertThat(balance.variant()).isNull();
        assertThat(balance.format()).isNull();
        assertThat(balance.javaName()).isNull();
    }

    @Test
    void duplicateNamesGetAnIndex() {
        var spec = draft("R", "01 R.", "05 FILLER PIC X(2).", "05 FILLER PIC X(2).",
                "05 CODE PIC X.", "05 CODE PIC X.");
        assertThat(spec.root().children()).extracting(SpecNode::path).containsExactly(
                "R.FILLER", "R.FILLER#2", "R.CODE", "R.CODE#2");
        assertThat(spec.root().children()).extracting(SpecNode::jsonName)
                .containsExactly(null, null, "code", "code2");
        assertThat(spec.root().children()).extracting(SpecNode::direction).containsExactly(
                FieldDirection.IGNORE, FieldDirection.IGNORE, FieldDirection.INPUT_OUTPUT,
                FieldDirection.INPUT_OUTPUT);
    }

    @Test
    void thirdDuplicateGetsThree() {
        var spec = draft("R", "01 R.", "05 CODE PIC X.", "05 CODE PIC X.", "05 CODE PIC X.");
        assertThat(spec.root().children()).extracting(SpecNode::jsonName)
                .containsExactly("code", "code2", "code3");
    }

    @Test
    void inheritsGroupUsage() {
        var spec = draft("R", "01 R.", "05 AMOUNTS COMP-3.", "10 NET PIC 9(5).",
                "10 TAX PIC 9(3).");
        assertThat(at(spec, "R.AMOUNTS").usage()).isEqualTo(Usage.COMP3);
        assertThat(at(spec, "R.AMOUNTS.NET").usage()).isEqualTo(Usage.COMP3);
        assertThat(at(spec, "R.AMOUNTS.TAX").usage()).isEqualTo(Usage.COMP3);
        assertThat(spec.root().usage()).isEqualTo(Usage.DISPLAY);
    }

    @Test
    void resolvesDefaultSign() {
        var spec = draft("R", "01 R.", "05 A PIC S9(5).", "05 B PIC S9(5) SIGN LEADING SEPARATE.",
                "05 C PIC 9(5).", "05 D PIC S9(5) COMP-3.");
        assertThat(at(spec, "R.A").sign()).isEqualTo(Sign.TRAILING);
        assertThat(at(spec, "R.B").sign()).isEqualTo(Sign.LEADING_SEPARATE);
        assertThat(at(spec, "R.C").sign()).isNull();
        assertThat(at(spec, "R.D").sign()).isNull();
    }

    @Test
    void copiesOccursRedefinesValueConditions() {
        var spec = draft("R", "01 R.", "05 LINE-COUNT PIC 9(2).", "05 ROWS PIC X OCCURS 3.",
                "05 LINES PIC X OCCURS 1 TO 5 DEPENDING ON LINE-COUNT.",
                "05 RAW PIC X(4).", "05 COOKED REDEFINES RAW PIC X(4).",
                "05 FLAG PIC X VALUE 'A'.", "88 FLAG-A VALUE 'A'.",
                "05 LEVEL-NO PIC 9 VALUE 1.", "88 IN-RANGE VALUE 1 THRU 5.");
        assertThat(at(spec, "R.ROWS").occurs()).isEqualTo(new OccursSpec(3, 3, null));
        assertThat(at(spec, "R.ROWS").table()).isEqualTo(new TableSpec(TableMode.LOW_VALUES, null));
        assertThat(at(spec, "R.ROWS").counter()).isNull();
        var lines = at(spec, "R.LINES");
        assertThat(lines.occurs()).isEqualTo(new OccursSpec(1, 5, "R.LINE-COUNT"));
        assertThat(lines.table()).isNull();
        assertThat(lines.counter()).isEqualTo("R.LINE-COUNT");
        assertThat(at(spec, "R.LINE-COUNT").direction()).isEqualTo(FieldDirection.DERIVED);
        assertThat(at(spec, "R.COOKED").redefines()).isEqualTo("R.RAW");
        assertThat(at(spec, "R.FLAG").value()).isEqualTo(new ValueSpec("TEXT", "A"));
        assertThat(at(spec, "R.FLAG").conditions()).containsExactly(new ConditionSpec("FLAG-A",
                List.of(new ConditionRange(new ValueSpec("TEXT", "A"), null))));
        assertThat(at(spec, "R.LEVEL-NO").conditions()).containsExactly(
                new ConditionSpec("IN-RANGE", List.of(new ConditionRange(
                        new ValueSpec("NUMBER", "1"), new ValueSpec("NUMBER", "5")))));
    }

    @Test
    void resolvesQualifiedDependingOn() {
        var spec = draft("R", "01 R.", "05 HEAD.", "10 CNT PIC 9.", "05 TAIL.", "10 CNT PIC 9.",
                "10 ROWS PIC X OCCURS 1 TO 3 DEPENDING ON CNT OF TAIL.");
        assertThat(at(spec, "R.TAIL.ROWS").counter()).isEqualTo("R.TAIL.CNT");
        assertThat(at(spec, "R.TAIL.CNT").direction()).isEqualTo(FieldDirection.DERIVED);
        assertThat(at(spec, "R.HEAD.CNT").direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
    }

    @Test
    void keepsAnUnresolvedDependingOnName() {
        var spec = draft("R", "01 R.", "05 CNT PIC 9.",
                "05 ROWS PIC X OCCURS 1 TO 3 DEPENDING ON GONE.");
        assertThat(at(spec, "R.ROWS").occurs().dependingOn()).isEqualTo("GONE");
        assertThat(at(spec, "R.ROWS").counter()).isEqualTo("GONE");
        assertThat(at(spec, "R.CNT").direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
    }

    @Test
    void keepsAnUnresolvedQualifier() {
        var spec = draft("R", "01 R.", "05 CNT PIC 9.",
                "05 ROWS PIC X OCCURS 1 TO 3 DEPENDING ON CNT OF NOWHERE.");
        assertThat(at(spec, "R.ROWS").counter()).isEqualTo("CNT OF NOWHERE");
        assertThat(at(spec, "R.CNT").direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
    }

    @Test
    void keepsAnUnresolvedRedefines() {
        var spec = draft("R", "01 R.", "05 B REDEFINES GONE PIC X.");
        assertThat(at(spec, "R.B").redefines()).isEqualTo("GONE");
    }

    @Test
    void jsonNamesStayUniqueNextToNaturalOnes() {
        assertThat(draft("R", "01 R.", "05 CODE PIC X.", "05 CODE2 PIC X.", "05 CODE PIC X.")
                .root().children()).extracting(SpecNode::jsonName)
                .containsExactly("code", "code2", "code3");
        assertThat(draft("R", "01 R.", "05 CODE PIC X.", "05 CODE PIC X.", "05 CODE2 PIC X.")
                .root().children()).extracting(SpecNode::jsonName)
                .containsExactly("code", "code3", "code2");
    }

    @Test
    void flagsBlankWhenZeroAndEdited() {
        var spec = draft("R", "01 R.", "05 A PIC 9(5) BLANK WHEN ZERO.", "05 B PIC ZZ9.99.",
                "05 C PIC 9(5).");
        assertThat(at(spec, "R.A").blankWhenZero()).isTrue();
        assertThat(at(spec, "R.A").edited()).isFalse();
        assertThat(at(spec, "R.B").edited()).isTrue();
        assertThat(at(spec, "R.B").blankWhenZero()).isFalse();
        assertThat(at(spec, "R.C").blankWhenZero()).isFalse();
        assertThat(at(spec, "R.C").edited()).isFalse();
    }

    @Test
    void keepsTheFactsOfEditedPictures() {
        var spec = draft("R", "01 R.", "05 A PIC +ZZ9.", "05 B PIC -9(3).99 BLANK WHEN ZERO.",
                "05 C PIC ZZ9 BLANK WHEN ZERO.");
        assertThat(at(spec, "R.A").sign()).isNull();
        assertThat(at(spec, "R.A").edited()).isTrue();
        assertThat(at(spec, "R.A").blankWhenZero()).isFalse();
        assertThat(at(spec, "R.B").sign()).isNull();
        assertThat(at(spec, "R.B").edited()).isTrue();
        assertThat(at(spec, "R.B").blankWhenZero()).isTrue();
        assertThat(at(spec, "R.C").edited()).isTrue();
        assertThat(at(spec, "R.C").blankWhenZero()).isTrue();
    }

    @Test
    void ignoresEveryItemOfAFillerGroup() {
        var spec = draft("R", "01 R.", "05 FILLER.", "10 A PIC X(2).", "10 G.", "15 CNT PIC 9.",
                "05 ROWS PIC X OCCURS 1 TO 3 DEPENDING ON CNT.", "05 B PIC X.");
        for (String path : List.of("R.FILLER", "R.FILLER.A", "R.FILLER.G", "R.FILLER.G.CNT")) {
            assertThat(at(spec, path).direction()).as(path).isEqualTo(FieldDirection.IGNORE);
            assertThat(at(spec, path).jsonName()).as(path).isNull();
        }
        assertThat(at(spec, "R.ROWS").counter()).isEqualTo("R.FILLER.G.CNT");
        assertThat(at(spec, "R.B").direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
        assertThat(at(spec, "R.B").jsonName()).isEqualTo("b");
    }

    @Test
    void keepsAnAmbiguousDependingOnName() {
        var spec = draft("R", "01 R.", "05 G1.", "10 CNT PIC 9.", "05 G2.", "10 CNT PIC 9.",
                "10 ROWS PIC X OCCURS 1 TO 5 DEPENDING ON CNT.");
        assertThat(at(spec, "R.G2.ROWS").counter()).isEqualTo("CNT");
        assertThat(at(spec, "R.G2.ROWS").occurs().dependingOn()).isEqualTo("CNT");
        assertThat(at(spec, "R.G1.CNT").direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
        assertThat(at(spec, "R.G2.CNT").direction()).isEqualTo(FieldDirection.INPUT_OUTPUT);
    }

    @Test
    void doesNotCountAFillerAsACounter() {
        var spec = draft("R", "01 R.", "05 FILLER PIC 9.",
                "05 ROWS PIC X OCCURS 1 TO 5 DEPENDING ON FILLER.");
        assertThat(at(spec, "R.ROWS").counter()).isEqualTo("FILLER");
        assertThat(at(spec, "R.FILLER").direction()).isEqualTo(FieldDirection.IGNORE);
    }

    @Test
    void doesNotCountTheRecordAsACounter() {
        var spec = draft("CNT", "01 CNT.", "05 CNT PIC 9.",
                "05 ROWS PIC X OCCURS 1 TO 5 DEPENDING ON CNT.");
        assertThat(at(spec, "CNT.ROWS").counter()).isEqualTo("CNT.CNT");
        assertThat(at(spec, "CNT.CNT").direction()).isEqualTo(FieldDirection.DERIVED);
    }

    @Test
    void keepsSourceLocationAndHash() {
        var one = draft("R", "01 R.", "05 A PIC X(3).", "05 B PIC X(3).");
        var other = draft("R", "01 R.", "05 A PIC X(3).", "05 B PIC X(4).");
        var location = at(one, "R.A").location();
        assertThat(location.source()).isNotBlank();
        assertThat(location.line()).isEqualTo(2);
        assertThat(location.column()).isPositive();
        assertThat(one.sourceHash()).matches("[0-9a-f]{64}");
        assertThat(one.sourceHash()).isNotEqualTo(other.sourceHash());
        assertThat(one.sourceHash()).isEqualTo(
                draft("R", "01 R.", "05 A PIC X(3).", "05 B PIC X(3).").sourceHash());
    }

    @Test
    void draftRoundTripsThroughJson() {
        var spec = draft("R", "01 R.", "05 CNT PIC 9(2).", "05 ROWS PIC X OCCURS 1 TO 3 "
                + "DEPENDING ON CNT.", "05 F PIC X VALUE 'A'.", "88 F-A VALUE 'A'.",
                "05 FILLER PIC X.", "05 G PIC S9(3) COMP-3.", "05 H PIC ZZ9.");
        assertThat(MappingSpecJson.read(MappingSpecJson.write(spec))).isEqualTo(spec);
    }

    @Test
    void defaultOptionsUseIbm037() {
        assertThat(DraftOptions.defaults().charset()).isEqualTo("IBM037");
        assertThat(new ArrayList<>(List.of(new DraftOptions("IBM1047").charset())))
                .containsExactly("IBM1047");
    }
}
