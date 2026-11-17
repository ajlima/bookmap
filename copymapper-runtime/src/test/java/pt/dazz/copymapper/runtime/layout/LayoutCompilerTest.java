package pt.dazz.copymapper.runtime.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.dazz.copymapper.runtime.layout.Specs.draft;
import static pt.dazz.copymapper.runtime.layout.Specs.edit;

import java.util.List;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.copybook.ast.Usage;
import pt.dazz.copymapper.copybook.spec.FieldDirection;
import pt.dazz.copymapper.copybook.spec.JsonType;
import pt.dazz.copymapper.copybook.spec.MappingSpec;
import pt.dazz.copymapper.copybook.spec.OccursSpec;
import pt.dazz.copymapper.exception.MappingException;

class LayoutCompilerTest {

    private static MappingSpec account() {
        return draft("GET-ACCOUNT", "01 GET-ACCOUNT.", "05 ACCOUNT-ID PIC X(12).",
                "05 BALANCE PIC 9(7)V99 COMP-3.", "05 FILLER PIC X(3).");
    }

    private static List<OpenDecision> decisions(MappingSpec spec) {
        return LayoutCompiler.compile(spec).openDecisions();
    }

    private static List<OpenDecisionKind> kinds(MappingSpec spec) {
        return decisions(spec).stream().map(OpenDecision::kind).toList();
    }

    @Test
    void compilesTextAndPackedFields() {
        Compilation compilation = LayoutCompiler.compile(account());

        assertThat(compilation.complete()).isTrue();
        assertThat(compilation.openDecisions()).isEmpty();
        Layout layout = compilation.layout();
        assertThat(layout.root().children()).extracting(LayoutNode::offset)
                .containsExactly(0, 12, 17);
        assertThat(layout.root().children()).extracting(LayoutNode::length)
                .containsExactly(12, 5, 3);
        assertThat(layout.root().length()).isEqualTo(20);
        assertThat(layout.maxRequestLength()).isEqualTo(20);
        assertThat(layout.maxResponseLength()).isEqualTo(20);
    }

    @Test
    void compilesNestedGroups() {
        MappingSpec spec = draft("REC", "01 REC.", "05 HEAD.", "10 A PIC X(2).",
                "10 B PIC 9(3).", "05 TAIL.", "10 C PIC 9(3) COMP-3.", "10 D PIC X(4).");
        Layout layout = LayoutCompiler.compile(spec).layout();

        LayoutNode head = layout.node("REC.HEAD").orElseThrow();
        LayoutNode tail = layout.node("REC.TAIL").orElseThrow();
        assertThat(head.offset()).isZero();
        assertThat(head.length()).isEqualTo(5);
        assertThat(tail.offset()).isEqualTo(5);
        assertThat(tail.length()).isEqualTo(6);
        assertThat(layout.node("REC.TAIL.D").orElseThrow().offset()).isEqualTo(7);
        assertThat(layout.root().length()).isEqualTo(11);
    }

    @Test
    void reportsAMissingUsageOnAnElementaryItemOnly() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.ACCOUNT-ID", c -> c.usage(null));
        assertThat(decisions(spec)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(OpenDecisionKind.INVALID_COBOL);
            assertThat(d.message()).isEqualTo("ACCOUNT-ID has no USAGE.");
        });
        MappingSpec group = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2).");
        assertThat(LayoutCompiler.compile(edit(group, "REC.G", c -> c.usage(null))).complete())
                .isTrue();
    }

    @Test
    void reportsAllOpenDecisionsAtOnce() {
        MappingSpec spec = draft("GET-ACCOUNT", "01 GET-ACCOUNT.", "05 AMOUNT PIC Z(4)9.",
                "05 ITEM PIC X(2) OCCURS 3.", "05 SIGNED PIC S9(3).",
                "05 PLAIN PIC X(3).", "05 OTHER REDEFINES PLAIN PIC X(3).",
                "05 BIN PIC 9(4) COMP.");
        List<OpenDecision> found = decisions(spec);

        assertThat(found).extracting(OpenDecision::kind)
                .containsOnly(OpenDecisionKind.UNSUPPORTED_TYPE).hasSize(6);
        assertThat(found).extracting(OpenDecision::path).containsExactly(
                "GET-ACCOUNT.AMOUNT", "GET-ACCOUNT.ITEM", "GET-ACCOUNT.SIGNED",
                "GET-ACCOUNT.PLAIN", "GET-ACCOUNT.OTHER", "GET-ACCOUNT.BIN");
        assertThat(found.get(0).message()).isEqualTo("AMOUNT has an edited picture. "
                + "The runtime does not support edited pictures.");
    }

    @Test
    void reportsEachUnsupportedFeature() {
        MappingSpec base = account();
        String id = "GET-ACCOUNT.ACCOUNT-ID";
        assertThat(decisions(edit(base, id, c -> c.blankWhenZero(true))))
                .singleElement().extracting(OpenDecision::message).asString()
                .contains("BLANK WHEN ZERO");
        assertThat(decisions(edit(base, id, c -> c.occurs(new OccursSpec(3, 3, null)))))
                .singleElement().extracting(OpenDecision::message).asString().contains("OCCURS");
        assertThat(decisions(edit(base, id, c -> c.redefines("GET-ACCOUNT.BALANCE"))))
                .extracting(OpenDecision::path)
                .containsExactly(id, "GET-ACCOUNT.BALANCE");
        assertThat(decisions(edit(base, id, c -> c.usage(Usage.COMP))))
                .singleElement().extracting(OpenDecision::message).asString()
                .contains("USAGE COMP.");
        assertThat(decisions(edit(base, "GET-ACCOUNT.BALANCE", c -> c.usage(Usage.COMP5))))
                .singleElement().extracting(OpenDecision::message).asString()
                .contains("USAGE COMP5");
        for (JsonType type : List.of(JsonType.DATE, JsonType.TIME, JsonType.DATE_TIME)) {
            assertThat(decisions(edit(base, id, c -> c.jsonType(type))))
                    .singleElement().extracting(OpenDecision::message).asString()
                    .contains(type.name());
        }
    }

    @Test
    void reportsASignedPicture() {
        MappingSpec spec = draft("REC", "01 REC.", "05 A PIC S9(3).");
        assertThat(decisions(spec)).singleElement().extracting(OpenDecision::message)
                .asString().contains("signed pictures");
    }

    @Test
    void namesEveryFeatureOfAnItem() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.ACCOUNT-ID",
                c -> c.occurs(new OccursSpec(3, 3, null)).usage(Usage.COMP));
        assertThat(decisions(spec)).singleElement().extracting(OpenDecision::message)
                .isEqualTo("ACCOUNT-ID has OCCURS and USAGE COMP. "
                        + "The runtime does not support them.");
    }

    @Test
    void namesBothSidesOfARedefines() {
        MappingSpec spec = draft("REC", "01 REC.", "05 A PIC X(3).", "05 B REDEFINES A PIC X(3).");
        assertThat(decisions(spec)).extracting(OpenDecision::message).containsExactly(
                "A is redefined by B. The runtime does not support REDEFINES.",
                "B has REDEFINES. The runtime does not support REDEFINES.");
        MappingSpec both = edit(spec, "REC.A", c -> c.occurs(new OccursSpec(2, 2, null)));
        assertThat(decisions(both).get(0).message()).isEqualTo(
                "A has OCCURS and is redefined by B. The runtime does not support them.");
    }

    @Test
    void trustsTheEditSymbolsOfThePictureOverTheFlag() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.ACCOUNT-ID",
                c -> c.picture("Z(4)9").jsonType(JsonType.NUMBER).edited(false));
        assertThat(decisions(spec)).singleElement().extracting(OpenDecision::message)
                .asString().startsWith("ACCOUNT-ID has an edited picture.");
    }

    @Test
    void reportsAnItemThatASiblingRedefines() {
        MappingSpec spec = draft("GET-ACCOUNT", "01 GET-ACCOUNT.", "05 A PIC X(3).",
                "05 B REDEFINES A PIC X(3).");
        assertThat(decisions(spec)).extracting(OpenDecision::path)
                .containsExactly("GET-ACCOUNT.A", "GET-ACCOUNT.B");
    }

    @Test
    void reportsDuplicateJsonNames() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.BALANCE", c -> c.jsonName("accountId"));
        List<OpenDecision> found = decisions(spec);

        assertThat(found).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(OpenDecisionKind.INVALID_JSON_NAME);
            assertThat(d.path()).isEqualTo("GET-ACCOUNT.BALANCE");
        });
    }

    @Test
    void ignoresTheJsonNameOfAnIgnoredItem() {
        MappingSpec spec = edit(edit(account(), "GET-ACCOUNT.BALANCE", c -> c.jsonName("accountId")),
                "GET-ACCOUNT.BALANCE", c -> c.direction(FieldDirection.IGNORE));
        assertThat(LayoutCompiler.compile(spec).complete()).isTrue();
        MappingSpec unnamed = edit(account(), "GET-ACCOUNT.BALANCE",
                c -> c.direction(FieldDirection.IGNORE).jsonName(null));
        assertThat(LayoutCompiler.compile(unnamed).complete()).isTrue();
    }

    @Test
    void reportsMissingJsonName() {
        assertThat(kinds(edit(account(), "GET-ACCOUNT.BALANCE", c -> c.jsonName(null))))
                .containsExactly(OpenDecisionKind.INVALID_JSON_NAME);
        assertThat(kinds(edit(account(), "GET-ACCOUNT.BALANCE", c -> c.jsonName("  "))))
                .containsExactly(OpenDecisionKind.INVALID_JSON_NAME);
    }

    @Test
    void reportsDerivedDirection() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.BALANCE",
                c -> c.direction(FieldDirection.DERIVED));
        assertThat(kinds(spec)).containsExactly(OpenDecisionKind.INVALID_DIRECTION);
    }

    @Test
    void reportsAMissingDirection() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.BALANCE", c -> c.direction(null));
        assertThat(kinds(spec)).containsExactly(OpenDecisionKind.INVALID_DIRECTION);
    }

    @Test
    void reportsFillerWithADirection() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.FILLER",
                c -> c.direction(FieldDirection.INPUT).jsonName("pad"));
        assertThat(decisions(spec)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(OpenDecisionKind.INVALID_DIRECTION);
            assertThat(d.path()).isEqualTo("GET-ACCOUNT.FILLER");
        });
    }

    @Test
    void reportsChildOutsideParentDirection() {
        MappingSpec group = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2).", "10 B PIC X(2).");
        MappingSpec output = edit(group, "REC.G", c -> c.direction(FieldDirection.OUTPUT));
        MappingSpec spec = edit(edit(output, "REC.G.A", c -> c.direction(FieldDirection.INPUT)),
                "REC.G.B", c -> c.direction(FieldDirection.OUTPUT));
        MappingSpec both = edit(spec, "REC.G.B", c -> c.direction(FieldDirection.INPUT_OUTPUT));

        assertThat(decisions(spec)).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(OpenDecisionKind.INVALID_DIRECTION);
            assertThat(d.path()).isEqualTo("REC.G.A");
        });
        assertThat(decisions(both)).extracting(OpenDecision::path)
                .containsExactly("REC.G.A", "REC.G.B");
        MappingSpec inputGroup = edit(edit(edit(group, "REC.G",
                c -> c.direction(FieldDirection.INPUT)),
                "REC.G.A", c -> c.direction(FieldDirection.INPUT)),
                "REC.G.B", c -> c.direction(FieldDirection.OUTPUT));
        assertThat(decisions(inputGroup)).extracting(OpenDecision::path)
                .containsExactly("REC.G.B");
    }

    @Test
    void acceptsAChildInsideTheParentDirection() {
        MappingSpec group = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2).", "10 B PIC X(2).");
        MappingSpec output = edit(group, "REC.G", c -> c.direction(FieldDirection.OUTPUT));
        MappingSpec spec = edit(edit(output, "REC.G.A", c -> c.direction(FieldDirection.IGNORE)),
                "REC.G.B", c -> c.direction(FieldDirection.OUTPUT));
        assertThat(LayoutCompiler.compile(spec).complete()).isTrue();
    }

    @Test
    void skipsTheParentCheckWhenTheParentHasNoDirection() {
        MappingSpec group = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2).");
        MappingSpec spec = edit(group, "REC.G", c -> c.direction(null));
        assertThat(decisions(spec)).extracting(OpenDecision::path).containsExactly("REC.G");
    }

    @Test
    void reportsTypeMismatch() {
        MappingSpec spec = draft("REC", "01 REC.", "05 N PIC 9(3).", "05 T PIC X(3).",
                "05 D PIC 9(3).", "05 OK1 PIC 9(3).", "05 OK2 PIC X(3).");
        MappingSpec bad = edit(edit(edit(spec, "REC.N", c -> c.jsonType(JsonType.TEXT)),
                "REC.T", c -> c.jsonType(JsonType.NUMBER)),
                "REC.D", c -> c.jsonType(JsonType.DECIMAL_STRING));
        MappingSpec textDecimal = edit(spec, "REC.OK1", c -> c.jsonType(JsonType.DECIMAL_STRING));

        assertThat(decisions(bad)).extracting(OpenDecision::path)
                .containsExactly("REC.N", "REC.T");
        assertThat(decisions(bad)).extracting(OpenDecision::kind)
                .containsOnly(OpenDecisionKind.INVALID_TYPE);
        assertThat(LayoutCompiler.compile(textDecimal).complete()).isTrue();
    }

    @Test
    void reportsTypesOnGroupsAndMissingTypes() {
        MappingSpec group = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2).");
        assertThat(kinds(edit(group, "REC.G", c -> c.jsonType(JsonType.TEXT))))
                .containsExactly(OpenDecisionKind.INVALID_TYPE);
        assertThat(kinds(edit(group, "REC.G.A", c -> c.jsonType(null))))
                .containsExactly(OpenDecisionKind.INVALID_TYPE);
        MappingSpec filler = draft("REC", "01 REC.", "05 FILLER PIC X(2).");
        assertThat(LayoutCompiler.compile(edit(filler, "REC.FILLER", c -> c.jsonType(null)))
                .complete()).isTrue();
    }

    @Test
    void reportsInvalidCobol() {
        MappingSpec base = account();
        assertThat(kinds(edit(base, "GET-ACCOUNT.ACCOUNT-ID", c -> c.usage(Usage.COMP3))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
        assertThat(kinds(edit(base, "GET-ACCOUNT.ACCOUNT-ID", c -> c.picture("9(0)"))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
        assertThat(kinds(edit(base, "GET-ACCOUNT.BALANCE", c -> c.sign(Sign.TRAILING))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
        assertThat(kinds(edit(base, "GET-ACCOUNT.ACCOUNT-ID",
                c -> c.usage(Usage.DISPLAY).sign(Sign.TRAILING))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
    }

    @Test
    void reportsGroupsWithAPictureAndItemsWithoutOne() {
        MappingSpec group = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2).");
        assertThat(kinds(edit(group, "REC.G", c -> c.picture("X(2)"))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
        assertThat(kinds(edit(group, "REC.G.A", c -> c.picture(null))))
                .containsExactly(OpenDecisionKind.INVALID_COBOL);
    }

    @Test
    void layoutThrowsWithAllDecisions() {
        MappingSpec spec = edit(edit(account(), "GET-ACCOUNT.BALANCE", c -> c.jsonName(null)),
                "GET-ACCOUNT.ACCOUNT-ID", c -> c.direction(FieldDirection.DERIVED));
        Compilation compilation = LayoutCompiler.compile(spec);

        assertThat(compilation.complete()).isFalse();
        assertThatThrownBy(compilation::layout).isInstanceOf(MappingException.class)
                .satisfies(e -> {
                    String[] lines = e.getMessage().split("\n");
                    assertThat(lines).hasSize(3);
                    assertThat(lines[0]).isEqualTo(
                            "the mapping spec of GET-ACCOUNT has 2 open decisions:");
                    assertThat(lines[1]).isEqualTo(compilation.openDecisions().get(0).format());
                    assertThat(lines[2]).isEqualTo(compilation.openDecisions().get(1).format());
                });
    }

    @Test
    void layoutMessageUsesTheSingularForOneDecision() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.BALANCE", c -> c.jsonName(null));
        assertThatThrownBy(() -> LayoutCompiler.compile(spec).layout())
                .hasMessageStartingWith("the mapping spec of GET-ACCOUNT has 1 open decision:\n");
    }

    @Test
    void formatsADecisionWithItsLocation() {
        MappingSpec spec = edit(account(), "GET-ACCOUNT.BALANCE", c -> c.jsonName(null));
        OpenDecision decision = decisions(spec).get(0);
        assertThat(decision.format()).isEqualTo(decision.location().source() + ":"
                + decision.location().line() + ":" + decision.location().column()
                + ": GET-ACCOUNT.BALANCE: " + decision.message());
        assertThat(decision.format()).startsWith("GET-ACCOUNT.cpy:3:");
    }
}
