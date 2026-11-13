package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static pt.dazz.bookmap.copybook.map.Mapping.map;
import static pt.dazz.bookmap.copybook.map.Mapping.member;
import static pt.dazz.bookmap.copybook.map.Mapping.type;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.copybook.ast.Condition;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.ConditionMember;
import pt.dazz.bookmap.copybook.model.EnumConstant;
import pt.dazz.bookmap.copybook.model.EnumModel;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.ValueRange;

class ConditionMapperTest {

    private static MappingOptions modes(Map<String, ConditionMode> modes) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of(), null, modes);
    }

    private static MappingOptions mode(String name, ConditionMode mode) {
        return modes(Map.of(name, mode));
    }

    private static FieldMember field(MapResult result, String name) {
        return (FieldMember) member(type(result, "Order"), name);
    }

    private static List<String> messages(MapResult result) {
        return Mapping.diagnostics(result).stream().map(Diagnostic::message).toList();
    }

    private static ValueRange one(String value) {
        return new ValueRange(value, null);
    }

    private static final String[] PARTY = {
        "01 ORDER.",
        "05 PARTY-ROLE PIC X(8) VALUE 'BILL-TO '.",
        "88 PARTY-IS-SELLER VALUE 'SELLER'.",
        "88 PARTY-IS-BILL-TO VALUE 'BILL-TO'.",
        "88 PARTY-IS-SHIP-TO VALUE 'SHIP-TO'."};

    @Test
    void discreteValuesGiveAnEnum() {
        MapResult result = map(PARTY);
        assertThat(Mapping.diagnostics(result)).isEmpty();
        FieldMember role = field(result, "partyRole");
        assertThat(role.javaType()).isEqualTo(JavaType.ENUM);
        assertThat(role.enumType()).isEqualTo("PartyRole");
        assertThat(role.initialValue()).isEqualTo("BILL_TO");
        assertThat(role.conditions()).isEmpty();
        assertThat(result.model().enums()).containsExactly(new EnumModel("PartyRole",
            "PARTY-ROLE", List.of(
                new EnumConstant("SELLER", "PARTY-IS-SELLER", List.of(one("SELLER"))),
                new EnumConstant("BILL_TO", "PARTY-IS-BILL-TO", List.of(one("BILL-TO"))),
                new EnumConstant("SHIP_TO", "PARTY-IS-SHIP-TO", List.of(one("SHIP-TO"))))));
    }

    @Test
    void numericEnumAndEnumNameCollision() {
        MapResult result = map("01 ORDER.", "05 PRIORITY OCCURS 2.", "10 LEVEL PIC X.",
            "05 PRIO OCCURS 2.", "10 PRIORITY PIC S9(2)V9 VALUE +01.0.",
            "88 HIGH VALUE 1.", "88 LOW VALUES -1.5 2.");
        assertThat(Mapping.diagnostics(result)).isEmpty();
        FieldMember priority = (FieldMember) member(type(result, "Prio"), "priority");
        assertThat(priority.enumType()).isEqualTo("PrioPriority");
        assertThat(priority.initialValue()).isEqualTo("HIGH");
        assertThat(result.model().enums().get(0).constants())
            .extracting(EnumConstant::name, EnumConstant::values)
            .containsExactly(tuple("HIGH", List.of(one("1"))),
                tuple("LOW", List.of(one("-1.5"), one("2"))));
    }

    @Test
    void initialValueOutsideTheEnumIsAWarning() {
        MapResult result = map("01 ORDER.", "05 CODE PIC X VALUE 'Z'.",
            "88 CODE-A VALUE 'A'.", "88 CODE-B VALUE 'B'.");
        assertThat(messages(result)).containsExactly("the VALUE of CODE is not a value of a"
            + " level 88 item. The field has no initial value.");
        assertThat(field(result, "code").initialValue()).isNull();
    }

    @Test
    void explicitEnumPermitsRangesAndOneItem() {
        MapResult result = map(modes(Map.of("CODE", ConditionMode.ENUM,
                "FLAG", ConditionMode.ENUM)),
            "01 ORDER.", "05 CODE PIC X VALUE 'C'.",
            "88 CODE-LOW VALUE 'A' THRU 'D'.", "88 CODE-HIGH VALUE 'E' THRU 'H' 'Z'.",
            "05 FLAG PIC 9.", "88 FLAG-ON VALUE 1.");
        assertThat(Mapping.diagnostics(result)).isEmpty();
        assertThat(field(result, "code").initialValue()).isEqualTo("LOW");
        assertThat(result.model().enums()).extracting(EnumModel::name)
            .containsExactly("Code", "Flag");
        assertThat(result.model().enums().get(1).constants()).extracting(EnumConstant::name)
            .containsExactly("FLAG_ON");
        assertThat(result.model().enums().get(0).constants().get(1).values())
            .containsExactly(new ValueRange("E", "H"), one("Z"));
    }

    @Test
    void autoModeGivesConstantsAndTellsWhy() {
        MapResult result = map(
            new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of("ALT-A",
                new SubTypeDecision("KIND", Map.of("ALT-A", "A", "ALT-B", "B"), Map.of()))),
            "01 ORDER.",
            "05 FILLER PIC X.", "88 F-A VALUE 'A'.", "88 F-B VALUE 'B'.",
            "05 KIND PIC X.", "88 KIND-A VALUE 'A'.", "88 KIND-B VALUE 'B'.",
            "05 ONE PIC X.", "88 ONE-ON VALUE 'Y'.",
            "05 RNG PIC X.", "88 RNG-A VALUE 'A' THRU 'C'.", "88 RNG-B VALUE 'D'.",
            "05 BAD PIC X.", "88 BAD-A VALUE 'AB'.", "88 BAD-B VALUE 'B'.",
            "05 DUP PIC 9.", "88 DUP-A VALUE 1 2.", "88 DUP-B VALUE 2.",
            "05 ALT-A.", "10 A-1 PIC X.",
            "05 ALT-B REDEFINES ALT-A.", "10 B-1 PIC X.");
        assertThat(Mapping.diagnostics(result)).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(
                tuple(Severity.INFO, info("FILLER", "the item is a FILLER")),
                tuple(Severity.INFO, info("KIND",
                    "the item is a discriminator or a DEPENDING ON item")),
                tuple(Severity.INFO, info("ONE", "it has only one level 88 item")),
                tuple(Severity.INFO, info("RNG", "a level 88 item has a THRU range")),
                tuple(Severity.INFO, info("BAD",
                    "condition BAD-A: the value 'AB' is longer than 1 characters")),
                tuple(Severity.WARNING, "condition BAD-A: the value 'AB' is longer than 1"
                    + " characters. The condition has no constant."),
                tuple(Severity.INFO, info("DUP",
                    "the level 88 items DUP-A and DUP-B share a value")));
        assertThat(field(result, "filler1").conditions()).extracting(ConditionMember::constant,
            ConditionMember::method).containsExactly(tuple("F_A", "isFA"), tuple("F_B", "isFB"));
        assertThat(field(result, "bad").conditions()).extracting(ConditionMember::cobolName)
            .containsExactly("BAD-B");
        assertThat(field(result, "rng").conditions().get(0).values())
            .containsExactly(new ValueRange("A", "C"));
        assertThat(result.model().enums()).isEmpty();
    }

    @Test
    void countItemKeepsItsType() {
        MapResult result = map("01 ORDER.", "05 N PIC 9.", "88 N-ONE VALUE 1.",
            "88 N-TWO VALUE 2.", "05 LINES OCCURS 1 TO 2 DEPENDING ON N PIC X.");
        assertThat(messages(result)).containsExactly(info("N",
            "the item is a discriminator or a DEPENDING ON item"));
        assertThat(field(result, "n").javaType()).isEqualTo(JavaType.INT);
    }

    @Test
    void countFieldOfAFixedTableKeepsItsType() {
        MapResult result = map(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of("LINES", "N")),
            "01 ORDER.", "05 N PIC 9.", "88 N-ONE VALUE 1.", "88 N-TWO VALUE 2.",
            "05 LINES OCCURS 3 PIC X.");
        assertThat(field(result, "n").javaType()).isEqualTo(JavaType.INT);
        MapResult badName = map(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of(), Map.of("LINES", "N OF")),
            "01 ORDER.", "05 N PIC 9.", "05 LINES OCCURS 3 PIC X.");
        assertThat(badName.model()).isNull();
    }

    private static String info(String item, String reason) {
        return item + ": the level 88 items give constants, not an enum, because " + reason
            + ". Set the condition mode of " + item + " to change this.";
    }

    @Test
    void explicitEnumThatCannotBeAnEnumIsAnError() {
        MapResult result = map(modes(Map.of("DUP", ConditionMode.ENUM)), "01 ORDER.",
            "05 DUP PIC X.", "88 DUP-A VALUE 'A' THRU 'C'.", "88 DUP-B VALUE 'B'.");
        assertThat(result.model()).isNull();
        assertThat(messages(result)).containsExactly("DUP: the level 88 items cannot be an"
            + " enum: the level 88 items DUP-A and DUP-B share a value");
    }

    @Test
    void explicitConstantsAndNone() {
        MapResult result = map(modes(Map.of("STATUS", ConditionMode.CONSTANTS,
                "NOTE", ConditionMode.NONE)), "01 ORDER.",
            "05 STATUS PIC X.", "88 ACTIVE VALUE 'A'.", "88 CLOSED VALUE 'C'.",
            "05 NOTE PIC X.", "88 NOTE-A VALUE 'A'.");
        assertThat(Mapping.diagnostics(result)).extracting(Diagnostic::message).containsExactly(
            "condition NOTE-A is ignored");
        assertThat(Mapping.diagnostics(result).get(0).line()).isEqualTo(6);
        assertThat(field(result, "status").conditions()).containsExactly(
            new ConditionMember("ACTIVE", "ACTIVE", "isActive", List.of(one("A"))),
            new ConditionMember("CLOSED", "CLOSED", "isClosed", List.of(one("C"))));
        assertThat(field(result, "note").conditions()).isEmpty();
        assertThat(field(result, "note").javaType()).isEqualTo(JavaType.STRING);
    }

    @Test
    void conditionNamesAreUniqueInAClass() {
        MapResult result = map(modes(Map.of("STATUS OF A", ConditionMode.CONSTANTS,
                "STATUS OF B", ConditionMode.CONSTANTS)), "01 ORDER.",
            "05 A.", "10 STATUS PIC X.", "88 ACTIVE VALUE 'A'.",
            "05 B.", "10 STATUS PIC X.", "88 ACTIVE VALUE 'A'.");
        assertThat(field(result, "bStatus").conditions()).extracting(ConditionMember::constant,
            ConditionMember::method).containsExactly(tuple("ACTIVE2", "isActive2"));
    }

    @Test
    void optionNamesMustBeElementaryItemsWithConditions() {
        MapResult result = map(modes(Map.of("NOWHERE", ConditionMode.ENUM,
                "GRP", ConditionMode.ENUM, "PLAIN", ConditionMode.ENUM)), "01 ORDER.",
            "05 GRP.", "88 GRP-A VALUE 'A'.", "10 PLAIN PIC X.");
        assertThat(messages(result)).containsExactlyInAnyOrder(
            "condition mode: item NOWHERE does not exist",
            "condition mode: GRP is not an elementary item with level 88 items",
            "condition mode: PLAIN is not an elementary item with level 88 items",
            "condition GRP-A is ignored");
    }

    @Test
    void badDiscriminatorNameIsLeftToTheRedefinesResolver() {
        MapResult result = map(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of("ALT-A",
                new SubTypeDecision("KIND OF", Map.of("ALT-A", "A"), Map.of()))),
            "01 ORDER.", "05 KIND PIC X.", "05 ALT-A.", "10 A-1 PIC X.");
        assertThat(result.model()).isNull();
    }

    @Test
    void redefiningElementaryItemKeepsTheInfo() {
        MapResult result = map("01 ORDER.", "05 A PIC X.", "05 B REDEFINES A PIC X.",
            "88 B-ON VALUE 'Y'.");
        assertThat(messages(result)).containsExactly("condition B-ON is ignored",
            "B redefines A and is skipped");
    }

    @Test
    void initialValues() {
        MapResult result = map("01 ORDER.",
            "05 CURRENCY PIC X(3) VALUE 'EUR'.",
            "05 NOTE PIC X(8) VALUE SPACES.",
            "05 PADDED PIC X(3) VALUE ZEROS.",
            "05 COUNTER PIC 9(3) VALUE ZERO.",
            "05 TOTAL PIC 9(12) VALUE 7.",
            "05 RATE PIC S9(3)V99 VALUE -12.50.",
            "05 PLAIN PIC X.");
        assertThat(Mapping.diagnostics(result)).isEmpty();
        assertThat(type(result, "Order").members()).extracting(m -> ((FieldMember) m)
                .initialValue())
            .containsExactly("EUR", "", "000", "0", "7", "-12.50", null);
    }

    @Test
    void valuesThatAreNotValidAreWarnings() {
        MapResult result = map("01 ORDER.",
            "05 A PIC X(2) VALUE 'ABC'.",
            "05 B PIC X VALUE 1.",
            "05 C PIC 9 VALUE 'A'.",
            "05 D PIC 9 VALUE SPACES.",
            "05 E PIC X VALUE HIGH-VALUES.",
            "05 F PIC 9 VALUE -1.",
            "05 G PIC 9V9 VALUE 1.25.",
            "05 H PIC 9V9 VALUE 100.");
        assertThat(messages(result)).containsExactly(
            "the VALUE 'ABC' of A is longer than 2 characters. The field has no initial value.",
            "the VALUE 1 of B is a number, but the item is text. The field has no initial"
                + " value.",
            "the VALUE 'A' of C is text, but the item is numeric. The field has no initial"
                + " value.",
            "the VALUE SPACE of D is not valid for a numeric item. The field has no initial"
                + " value.",
            "the VALUE HIGH-VALUE of E is not supported. The field has no initial value.",
            "the VALUE -1 of F is negative, but the item has no sign. The field has no"
                + " initial value.",
            "the VALUE 1.25 of G has more than 1 decimal places. The field has no initial"
                + " value.",
            "the VALUE 100 of H has more than 2 digits. The field has no initial value.");
    }

    @Test
    void numericRangesOverlap() {
        MapResult result = map(modes(Map.of("N", ConditionMode.ENUM)), "01 ORDER.",
            "05 N PIC 9(3).", "88 N-A VALUE 1 THRU 10.", "88 N-B VALUE 10 THRU 20.");
        assertThat(messages(result)).containsExactly("N: the level 88 items cannot be an"
            + " enum: the level 88 items N-A and N-B share a value");
        MapResult apart = map(modes(Map.of("N", ConditionMode.ENUM)), "01 ORDER.",
            "05 N PIC 9(3).", "88 N-A VALUE 1 THRU 10.", "88 N-B VALUE 11 THRU 20.");
        assertThat(Mapping.diagnostics(apart)).isEmpty();
    }

    @Test
    void stripPrefixesApplyToConditionNames() {
        MapResult result = map(new MappingOptions(List.of("WS-"), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of(), Map.of()),
            "01 ORDER.", "05 WS-FLAG PIC X.", "88 WS-FLAG-ON VALUE 'Y'.");
        assertThat(((FieldMember) member(type(result, "Order"), "flag")).conditions())
            .extracting(ConditionMember::constant, ConditionMember::method)
            .containsExactly(tuple("FLAG_ON", "isFlagOn"));
    }

    private static List<Condition> conditions(String... names) {
        return java.util.Arrays.stream(names)
            .map(name -> new Condition(name, List.of(), 1, 1)).toList();
    }

    @Test
    void constantNames() {
        assertThat(ConditionMapper.constantNames(conditions("PARTY-IS-SELLER",
            "PARTY-IS-BILL-TO", "PARTY-IS-SHIP-TO")))
            .containsExactly("SELLER", "BILL_TO", "SHIP_TO");
        assertThat(ConditionMapper.constantNames(conditions("ST-A-X", "ST-B-X")))
            .containsExactly("A_X", "B_X");
        assertThat(ConditionMapper.constantNames(conditions("ST-OPEN", "ST")))
            .containsExactly("ST_OPEN", "ST");
        assertThat(ConditionMapper.constantNames(conditions("A-X", "B-X", "A-X")))
            .containsExactly("A_X", "B_X", "A_X2");
        assertThat(ConditionMapper.constantNames(conditions("CODE-1", "CODE-2")))
            .containsExactly("CODE_1", "CODE_2");
        assertThat(ConditionMapper.constantNames(conditions("1ST", "2ND")))
            .containsExactly("VALUE_1ST", "VALUE_2ND");
        assertThat(ConditionMapper.constantNames(conditions("ONLY-ONE")))
            .containsExactly("ONLY_ONE");
    }

    @Test
    void modeWords() {
        assertThat(ConditionMode.of("Enum")).isEqualTo(ConditionMode.ENUM);
        assertThat(ConditionMode.CONSTANTS.word()).isEqualTo("constants");
        assertThatThrownBy(() -> ConditionMode.of("flags"))
            .hasMessage("unknown condition mode 'flags'. Use enum, constants, or none.");
    }
}
