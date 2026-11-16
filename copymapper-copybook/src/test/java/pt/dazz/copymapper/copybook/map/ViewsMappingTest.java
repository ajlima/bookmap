package pt.dazz.copymapper.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pt.dazz.copymapper.copybook.map.Mapping.diagnostics;
import static pt.dazz.copymapper.copybook.map.Mapping.map;
import static pt.dazz.copymapper.copybook.map.Mapping.member;
import static pt.dazz.copymapper.copybook.map.Mapping.type;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.annotation.Direction;
import pt.dazz.copymapper.copybook.diag.Diagnostic;
import pt.dazz.copymapper.copybook.diag.Severity;
import pt.dazz.copymapper.copybook.model.ClassKind;
import pt.dazz.copymapper.copybook.model.ClassModel;
import pt.dazz.copymapper.copybook.model.CollectionMember;
import pt.dazz.copymapper.copybook.model.FieldMember;
import pt.dazz.copymapper.copybook.model.GroupMember;
import pt.dazz.copymapper.copybook.model.JavaType;
import pt.dazz.copymapper.copybook.model.MemberModel;

/** Tests the option {@code views}: a {@code REDEFINES} area without a discriminator. */
class ViewsMappingTest {

    private static final String[] SHIPMENT = {
        "01 GET-SHIPMENT.",
        "   05 SHIPMENT-ID PIC X(10).",
        "   05 TRACKING-CODE PIC X(16).",
        "   05 TRACKING-PARTS REDEFINES TRACKING-CODE.",
        "      10 CARRIER-CODE PIC X(4).",
        "      10 ROUTE-NUMBER PIC 9(6).",
        "      10 SEQUENCE-NUMBER PIC 9(6).",
        "   05 RESULT-CODE PIC X(2).",
        "   05 RESULT-DETAIL.",
        "      10 ERROR-TEXT PIC X(40).",
        "   05 DELIVERY-DATA REDEFINES RESULT-DETAIL.",
        "      10 DELIVERY-DATE PIC 9(8).",
        "      10 DELIVERED-WEIGHT PIC S9(5)V99 COMP-3.",
        "      10 SIGNED-BY PIC X(20).",
        "   05 SHIPPING-CHARGE PIC S9(7)V99 COMP-3.",
    };

    /** Gives the options with views, on top of other options. */
    private static MappingOptions views(MappingOptions base, String... names) {
        return new MappingOptions(base.stripPrefixes(), base.inputMarkers(),
            base.outputMarkers(), base.directions(), base.subTypes(), base.countFields(),
            base.maxRecordLength(), base.dates(), base.continuable(), base.validatable(),
            base.conditions(), base.emptyItems(), base.emptyKeys(), base.partialTables(),
            List.of(names));
    }

    private static MappingOptions views(String... names) {
        return views(MappingOptions.defaults(), names);
    }

    private static MappingOptions partial(String table) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of(), null, Map.of(),
            Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(table));
    }

    private static List<String> errors(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR)
            .map(Diagnostic::message).toList();
    }

    @Test
    void anAreaWithTheOptionBecomesViews() {
        MapResult result = map(views("TRACKING-CODE", "DELIVERY-DATA"), SHIPMENT);
        assertThat(diagnostics(result)).isEmpty();
        ClassModel shipment = type(result, "GetShipment");
        assertThat(shipment.members()).extracting(MemberModel::name, MemberModel::order,
                MemberModel::redefines)
            .containsExactly(tuple("shipmentId", 10, null), tuple("trackingCode", 20, ""),
                tuple("trackingParts", 30, "trackingCode"), tuple("resultCode", 40, null),
                tuple("resultDetail", 50, ""), tuple("deliveryData", 60, "resultDetail"),
                tuple("shippingCharge", 70, null));
        GroupMember parts = (GroupMember) member(shipment, "trackingParts");
        assertThat(parts.groupClass()).isEqualTo("TrackingParts");
        assertThat(parts.direction()).isEqualTo(Direction.INPUT_OUTPUT);
        assertThat(parts.cobolName()).isEqualTo("TRACKING-PARTS");
        ClassModel group = type(result, "TrackingParts");
        assertThat(group.kind()).isEqualTo(ClassKind.GROUP);
        assertThat(group.members()).extracting(MemberModel::name, MemberModel::redefines)
            .containsExactly(tuple("carrierCode", null), tuple("routeNumber", null),
                tuple("sequenceNumber", null));
        assertThat(type(result, "DeliveryData").members()).hasSize(3);
    }

    @Test
    void anElementaryViewHasNoInitialValue() {
        MapResult result = map(views("NUM"), "01 ORDER.", "05 CODE PIC X(4) VALUE 'ABCD'.",
            "05 NUM REDEFINES CODE PIC 9(4).", "05 TRAILER PIC X.");
        assertThat(diagnostics(result)).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(tuple(Severity.INFO, "the VALUE of CODE is ignored, because CODE"
                + " is in a REDEFINES view"));
        ClassModel order = type(result, "Order");
        FieldMember code = (FieldMember) member(order, "code");
        assertThat(code.initialValue()).isNull();
        assertThat(code.redefines()).isEmpty();
        FieldMember number = (FieldMember) member(order, "num");
        assertThat(number.javaType()).isEqualTo(JavaType.INT);
        assertThat(number.redefines()).isEqualTo("code");
    }

    @Test
    void aViewKeepsTheInitialValueOfAFieldInAGroupClass() {
        MapResult result = map(views("A"), "01 ORDER.", "05 A PIC X(4).",
            "05 B REDEFINES A.", "10 B1 PIC X(4) VALUE 'WXYZ'.");
        assertThat(errors(result)).isEmpty();
        assertThat(((FieldMember) member(type(result, "B"), "b1")).initialValue())
            .isEqualTo("WXYZ");
    }

    @Test
    void theLevel88ItemsOfAnElementaryViewAreMapped() {
        MapResult result = map(views("CODE"), "01 ORDER.", "05 CODE PIC X(2).",
            "05 STATUS-CODE REDEFINES CODE PIC 9(2).", "88 ACTIVE VALUE 1.");
        assertThat(diagnostics(result)).extracting(Diagnostic::message)
            .noneMatch(message -> message.contains("is ignored"));
        assertThat(((FieldMember) member(type(result, "Order"), "statusCode")).conditions())
            .hasSize(1);
    }

    @Test
    void aTableViewIsAFixedCollection() {
        MapResult result = map(views("RAW"), "01 ORDER.", "05 RAW PIC X(6).",
            "05 PARTS REDEFINES RAW OCCURS 3 PIC X(2).");
        assertThat(errors(result)).isEmpty();
        CollectionMember parts = (CollectionMember) member(type(result, "Order"), "parts");
        assertThat(parts.fixed()).isTrue();
        assertThat(parts.redefines()).isEqualTo("raw");
    }

    @Test
    void aViewInASplitAlternativeHasItsDirection() {
        MapResult result = map(views("CODE"), "01 ORDER.", "05 ORDER-REQUEST.",
            "10 ORDER-ID PIC X(4).", "05 ORDER-RESPONSE REDEFINES ORDER-REQUEST.",
            "10 CODE PIC X(4).", "10 NUM REDEFINES CODE PIC 9(4).");
        assertThat(errors(result)).isEmpty();
        assertThat(type(result, "Order").members()).extracting(MemberModel::name,
                MemberModel::direction)
            .containsExactly(tuple("orderId", Direction.INPUT), tuple("code", Direction.OUTPUT),
                tuple("num", Direction.OUTPUT));
    }

    @Test
    void anAreaInAGroupViewNeedsItsOwnOption() {
        String[] lines = {"01 ORDER.", "05 RAW PIC X(4).", "05 LIMITS REDEFINES RAW.",
            "10 L1.", "15 A PIC X(2).", "10 L2 REDEFINES L1.", "15 B PIC 9(2).",
            "10 C PIC X(2)."};
        MapResult skipped = map(views("RAW"), lines);
        assertThat(diagnostics(skipped)).extracting(Diagnostic::message).containsExactly(
            "the REDEFINES area L1 is inside another REDEFINES area. CopyMapper keeps L1 and skips"
                + " L2. To keep both, add \"views\": [\"L1\"].");
        MapResult both = map(views("RAW", "L2"), lines);
        assertThat(errors(both)).isEmpty();
        assertThat(type(both, "Limits").members()).extracting(MemberModel::name)
            .containsExactly("l1", "l2", "c");
    }

    @Test
    void anAreaInATableEntryCanBeViews() {
        MapResult result = map(views("CODE"), "01 ORDER.", "05 STOPS OCCURS 2.",
            "10 CODE PIC X(4).", "10 NUM REDEFINES CODE PIC 9(4).");
        assertThat(errors(result)).isEmpty();
        assertThat(type(result, "Stops").members()).extracting(MemberModel::redefines)
            .containsExactly("", "code");
    }

    @Test
    void anAreaInASubtypeCanBeViews() {
        MapResult result = map(views(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of("CARD-DATA", new SubTypeDecision(
                    "KIND", Map.of("CARD-DATA", "C", "LOAN-DATA", "L"), Map.of()))), "CODE"),
            "01 ACCOUNT.", "05 KIND PIC X.", "05 CARD-DATA.", "10 CODE PIC X(2).",
            "10 NUM REDEFINES CODE PIC 9(2).", "05 LOAN-DATA REDEFINES CARD-DATA.",
            "10 RATE PIC X(2).");
        assertThat(errors(result)).isEmpty();
        assertThat(type(result, "CardData").members()).extracting(MemberModel::redefines)
            .containsExactly("", "code");
    }

    @Test
    void aFillerGroupViewGetsAFillerName() {
        MapResult result = map(views("A"), "01 ORDER.", "05 A PIC X(2).",
            "05 FILLER REDEFINES A.", "10 B PIC X(2).");
        assertThat(errors(result)).isEmpty();
        assertThat(type(result, "Order").members()).extracting(MemberModel::name)
            .containsExactly("a", "filler1");
    }

    // ---- diagnostics ----

    @Test
    void theOptionNeedsARedefinesItem() {
        assertThat(errors(map(views("A"), "01 ORDER.", "05 A PIC X.", "05 B PIC X.")))
            .containsExactly("views option: A is not a REDEFINES item");
        assertThat(errors(map(views("NOPE"), "01 ORDER.", "05 A PIC X.")))
            .containsExactly("views option: item NOPE does not exist");
        assertThat(errors(map(views("B"), "01 ORDER.", "05 G.", "10 A PIC X.",
            "05 B REDEFINES A PIC X.")))
            .contains("views option: B is not a REDEFINES item");
    }

    @Test
    void twoNamesOfOneAreaAreAnError() {
        assertThat(errors(map(views("A", "B"), "01 ORDER.", "05 A PIC X.",
            "05 B REDEFINES A PIC X.")))
            .containsExactly("views option: two names give the REDEFINES area A");
    }

    @Test
    void anAreaWithASubtypeDecisionIsAnError() {
        MapResult result = map(views(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of("CARD-DATA", new SubTypeDecision(
                    "KIND", Map.of("CARD-DATA", "C", "LOAN-DATA", "L"), Map.of()))),
                "LOAN-DATA"),
            "01 ACCOUNT.", "05 KIND PIC X.", "05 CARD-DATA.", "10 A PIC X.",
            "05 LOAN-DATA REDEFINES CARD-DATA.", "10 B PIC X.");
        assertThat(errors(result)).contains("views option: the REDEFINES area CARD-DATA also has"
            + " a subtype decision. Give one decision.");
    }

    @Test
    void anAreaWithADirectionDecisionIsAnError() {
        MappingOptions base = new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of("Q-DATA", Direction.INPUT, "R-DATA",
                Direction.OUTPUT), Map.of());
        String[] lines = {"01 ACCOUNT.", "05 Q-DATA.", "10 A PIC X.",
            "05 R-DATA REDEFINES Q-DATA.", "10 B PIC X."};
        String message = "views option: the REDEFINES area Q-DATA also has a direction"
            + " decision. Give one decision.";
        assertThat(errors(map(views(base, "Q-DATA"), lines))).contains(message);
        MappingOptions second = new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of("R-DATA", Direction.OUTPUT), Map.of());
        assertThat(errors(map(views(second, "Q-DATA"), lines))).contains(message);
    }

    @Test
    void aVariableTableInAViewIsAnError() {
        assertThat(errors(map(views("A"), "01 ORDER.", "05 N PIC 9.", "05 A PIC X(5).",
            "05 B REDEFINES A.", "10 T OCCURS 1 TO 5 DEPENDING ON N PIC X.")))
            .containsExactly("the REDEFINES alternative B has OCCURS DEPENDING ON. A view needs"
                + " a fixed size.");
    }

    @Test
    void aTableViewMustNotHaveACountField() {
        MappingOptions base = new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of("PARTS", "N"));
        assertThat(errors(map(views(base, "RAW"), "01 ORDER.", "05 N PIC 9.",
            "05 RAW PIC X(6).", "05 PARTS REDEFINES RAW OCCURS 3 PIC X(2).")))
            .containsExactly("count field option: PARTS is in a REDEFINES view. A table in a"
                + " view must not have a count field.");
    }

    @Test
    void aTableInAViewCannotBePartial() {
        assertThat(errors(map(views(partial("PARTS"), "RAW"), "01 ORDER.", "05 RAW PIC X(6).",
            "05 PARTS REDEFINES RAW OCCURS 3 PIC X(2).")))
            .containsExactly("partial table option: PARTS is in a REDEFINES view");
        assertThat(errors(map(views(partial("INNER"), "RAW"), "01 ORDER.", "05 RAW PIC X(6).",
            "05 G REDEFINES RAW.", "10 INNER OCCURS 3 PIC X(2).")))
            .containsExactly("partial table option: INNER is in a REDEFINES view");
    }

    @Test
    void aDiscriminatorMustNotBeInAView() {
        MapResult result = map(views(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of("CARD-DATA", new SubTypeDecision(
                    "KIND", Map.of("CARD-DATA", "C", "LOAN-DATA", "L"), Map.of()))), "KIND-X"),
            "01 ACCOUNT.", "05 KIND-X PIC X.", "05 KIND REDEFINES KIND-X PIC X.",
            "05 CARD-DATA.", "10 A PIC X.", "05 LOAN-DATA REDEFINES CARD-DATA.", "10 B PIC X.");
        assertThat(errors(result)).contains("the discriminator KIND must not be in a REDEFINES"
            + " view");
    }

    @Test
    void anErrorInTheRedefinedItemStopsTheArea() {
        MapResult result = map(views("A"), "01 ORDER.", "05 A PIC X COMP-3.",
            "05 B REDEFINES A PIC 9.");
        assertThat(errors(result)).containsExactly("A has a text picture and must not have"
            + " COMP or COMP-3");
    }

    // ---- options ----

    @Test
    void theOptionsWithoutViewsHaveNoViews() {
        MappingOptions options = new MappingOptions(List.of(), List.of(), List.of(), Map.of(),
            Map.of(), Map.of(), null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(), List.of("T"));
        assertThat(options.views()).isEmpty();
        assertThat(options.partialTables()).containsExactly("T");
        assertThat(MappingOptions.defaults().views()).isEmpty();
        assertThat(views("A").withHints(Map.of(), Map.of(), Map.of()).views())
            .containsExactly("A");
    }
}
