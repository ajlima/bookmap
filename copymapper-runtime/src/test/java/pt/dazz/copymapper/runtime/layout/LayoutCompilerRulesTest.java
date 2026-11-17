package pt.dazz.copymapper.runtime.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.copymapper.runtime.layout.Specs.draft;
import static pt.dazz.copymapper.runtime.layout.Specs.edit;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.spec.JsonType;
import pt.dazz.copymapper.copybook.spec.MappingSpec;
import pt.dazz.copymapper.copybook.spec.OccursSpec;
import pt.dazz.copymapper.copybook.spec.TableMode;
import pt.dazz.copymapper.copybook.spec.TableSpec;
import pt.dazz.copymapper.copybook.spec.VariantSpec;

/** The rules on paths, levels, lengths, and the REST decisions of tables and variants. */
class LayoutCompilerRulesTest {

    private static MappingSpec plain() {
        return draft("REC", "01 REC.", "05 A PIC X(2).", "05 B PIC X(3).");
    }

    private static List<OpenDecision> decisions(MappingSpec spec) {
        return LayoutCompiler.compile(spec).openDecisions();
    }

    private static List<String> messages(MappingSpec spec) {
        return decisions(spec).stream().map(OpenDecision::message).toList();
    }

    private static List<OpenDecisionKind> kinds(MappingSpec spec) {
        return decisions(spec).stream().map(OpenDecision::kind).toList();
    }

    private static void assertOne(MappingSpec spec, OpenDecisionKind kind, String path,
            String message) {
        assertThat(decisions(spec)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(kind);
            assertThat(d.path()).isEqualTo(path);
            assertThat(d.message()).isEqualTo(message);
        });
    }

    @Test
    void reportsADuplicatePath() {
        MappingSpec spec = draft("REC", "01 REC.", "05 A PIC X.", "05 A PIC X.");
        assertOne(edit(spec, "REC.A#2", c -> c.path("REC.A")), OpenDecisionKind.INVALID_COBOL,
                "REC.A", "A has the path REC.A, which another item has too.");
    }

    @Test
    void reportsAPathThatDoesNotFitTheName() {
        assertOne(edit(plain(), "REC.A", c -> c.path("REC.Z")), OpenDecisionKind.INVALID_COBOL,
                "REC.Z", "A has the path REC.Z. Its path must be REC.A, or REC.A#n with n from 2.");
        MappingSpec record = draft("REC", "01 REC PIC X(4).");
        assertOne(edit(record, "REC", c -> c.path("REC#2")), OpenDecisionKind.INVALID_COBOL,
                "REC#2", "REC has the path REC#2. The path of a record must be its name.");
    }

    @Test
    void acceptsAnIndexFromTwo() {
        for (String path : List.of("REC.A#2", "REC.A#9", "REC.A#10", "REC.A#123")) {
            assertThat(decisions(edit(plain(), "REC.A", c -> c.path(path)))).as(path).isEmpty();
        }
        for (String path : List.of("REC.A#1", "REC.A#0", "REC.A#02", "REC.A#", "REC.A#2x",
                "REC.AB", "XREC.A", "REC-A")) {
            assertThat(kinds(edit(plain(), "REC.A", c -> c.path(path)))).as(path)
                    .containsExactly(OpenDecisionKind.INVALID_COBOL);
        }
    }

    @Test
    void reportsTheLevelOfTheRecord() {
        assertOne(edit(plain(), "REC", c -> c.level(2)), OpenDecisionKind.INVALID_COBOL, "REC",
                "REC has the level 2. A record must have the level 1.");
    }

    @Test
    void reportsTheLevelOfAnItem() {
        assertThat(decisions(edit(plain(), "REC.A", c -> c.level(2)))).isEmpty();
        assertThat(decisions(edit(plain(), "REC.A", c -> c.level(49)))).isEmpty();
        assertOne(edit(plain(), "REC.A", c -> c.level(50)), OpenDecisionKind.INVALID_COBOL,
                "REC.A", "A has the level 50. An item of a group must have a level from 2 to 49 "
                        + "that is greater than 1, the level of its group.");
        assertThat(kinds(edit(plain(), "REC.A", c -> c.level(1))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
        MappingSpec group = draft("REC", "01 REC.", "05 G.", "10 A PIC X.");
        assertThat(kinds(edit(group, "REC.G.A", c -> c.level(5))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
        assertThat(kinds(edit(group, "REC.G.A", c -> c.level(4))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
        assertThat(decisions(edit(group, "REC.G.A", c -> c.level(6)))).isEmpty();
        MappingSpec zero = edit(edit(plain(), "REC", c -> c.level(0)), "REC.A", c -> c.level(1));
        assertThat(decisions(zero)).extracting(OpenDecision::path).containsExactly("REC",
                "REC.A");
    }

    @Test
    void compilesTheLongestRecord() {
        Compilation compilation = LayoutCompiler.compile(
                draft("REC", "01 REC.", "05 A PIC X(2147483647)."));
        assertThat(compilation.openDecisions()).isEmpty();
        assertThat(compilation.layout().root().length()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void reportsARecordThatIsTooLong() {
        MappingSpec spec = draft("REC", "01 REC.", "05 A PIC X(2147483647).", "05 B PIC X.");
        assertOne(spec, OpenDecisionKind.INVALID_COBOL, "REC",
                "REC is longer than 2147483647 bytes.");
    }

    @Test
    void reportsOnlyTheSmallestGroupThatIsTooLong() {
        MappingSpec spec = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2147483647).",
                "10 B PIC X(10).", "05 C PIC X.");
        assertOne(spec, OpenDecisionKind.INVALID_COBOL, "REC.G",
                "G is longer than 2147483647 bytes.");
    }

    @Test
    void reportsAnItemThatIsTooLong() {
        MappingSpec spec = draft("REC", "01 REC.", "05 A PIC S9(2147483647).");
        MappingSpec separate = edit(spec, "REC.A", c -> c.sign(Sign.LEADING_SEPARATE));
        assertThat(messages(separate)).contains("A is longer than 2147483647 bytes.");
        assertThat(messages(spec)).doesNotContain("A is longer than 2147483647 bytes.");
    }

    @Test
    void reportsAVariantOutsideARedefinesArea() {
        VariantSpec variant = new VariantSpec.FixedSelection("REC.A", null);
        assertOne(edit(plain(), "REC.A", c -> c.variant(variant)),
                OpenDecisionKind.INVALID_VARIANT, "REC.A",
                "A has a variant, but it is not part of a REDEFINES area.");
        MappingSpec area = draft("REC", "01 REC.", "05 A PIC X(2).", "05 B REDEFINES A PIC X(2).");
        VariantSpec discriminator = new VariantSpec.Discriminator("REC.T", Map.of("1", "REC.A"));
        assertThat(kinds(edit(area, "REC.A", c -> c.variant(discriminator))))
                .doesNotContain(OpenDecisionKind.INVALID_VARIANT);
        assertThat(kinds(edit(area, "REC.B", c -> c.variant(discriminator))))
                .doesNotContain(OpenDecisionKind.INVALID_VARIANT);
    }

    @Test
    void reportsATableWithoutAFixedOccurs() {
        TableSpec spaces = new TableSpec(TableMode.SPACES, null);
        assertOne(edit(plain(), "REC.A", c -> c.table(spaces)), OpenDecisionKind.INVALID_TABLE,
                "REC.A", "A has a table, but it has no fixed OCCURS.");
        MappingSpec tables = draft("REC", "01 REC.", "05 N PIC 9.", "05 F PIC X OCCURS 3.",
                "05 D PIC X OCCURS 1 TO 3 DEPENDING ON N.");
        assertThat(kinds(tables)).doesNotContain(OpenDecisionKind.INVALID_TABLE);
        assertThat(kinds(edit(tables, "REC.D", c -> c.table(spaces))))
                .contains(OpenDecisionKind.INVALID_TABLE);
    }

    @Test
    void reportsACounterWithoutAnOccursDependingOn() {
        assertOne(edit(plain(), "REC.A", c -> c.counter("REC.B")),
                OpenDecisionKind.INVALID_TABLE, "REC.A",
                "A has a counter, but it has no OCCURS DEPENDING ON.");
        MappingSpec fixed = draft("REC", "01 REC.", "05 N PIC 9.", "05 F PIC X OCCURS 3.");
        assertThat(kinds(edit(fixed, "REC.F", c -> c.counter("REC.N"))))
                .contains(OpenDecisionKind.INVALID_TABLE);
        assertThat(kinds(edit(fixed, "REC.F",
                c -> c.occurs(new OccursSpec(1, 3, "REC.N")).table(null).counter("REC.N"))))
                .doesNotContain(OpenDecisionKind.INVALID_TABLE);
    }

    @Test
    void reportsAFormatWithoutADateType() {
        assertOne(edit(plain(), "REC.A", c -> c.format("yyyyMMdd")),
                OpenDecisionKind.INVALID_TYPE, "REC.A",
                "A has a format, but its JSON type is not DATE, TIME, or DATE_TIME.");
        for (JsonType type : List.of(JsonType.DATE, JsonType.TIME, JsonType.DATE_TIME)) {
            assertThat(kinds(edit(plain(), "REC.A", c -> c.format("yyyy").jsonType(type))))
                    .as(type.name()).doesNotContain(OpenDecisionKind.INVALID_TYPE);
        }
        assertThat(kinds(edit(plain(), "REC.A", c -> c.format("yyyy").jsonType(null))))
                .containsExactly(OpenDecisionKind.INVALID_TYPE, OpenDecisionKind.INVALID_TYPE);
    }

    @Test
    void indexesEachPathOnce() {
        MappingSpec spec = plain();
        LayoutNode a = new LayoutNode(Specs.find(spec, "REC.A"), 0, 2, List.of());
        LayoutNode root = new LayoutNode(spec.root(), 0, 4, List.of(a, a));
        assertThatThrownBy(() -> new Layout(spec, root))
                .isInstanceOf(IllegalStateException.class);
    }
}
