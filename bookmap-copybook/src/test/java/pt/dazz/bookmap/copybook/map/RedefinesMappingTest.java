package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pt.dazz.bookmap.copybook.map.Mapping.map;
import static pt.dazz.bookmap.copybook.map.Mapping.member;
import static pt.dazz.bookmap.copybook.map.Mapping.type;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Severity;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.MemberModel;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;

class RedefinesMappingTest {

    private static final String[] INVOICE = {
        "01 GET-INVOICE.",
        "   05 RETURN-CODE PIC X(4).",
        "   05 REQUEST-DATA.",
        "      10 INVOICE-ID PIC X(12).",
        "   05 RESPONSE-DATA REDEFINES REQUEST-DATA.",
        "      10 INVOICE-AMOUNT PIC S9(7)V99 COMP-3.",
        "      10 DUE-DATE PIC 9(8).",
        "   05 TRAILER PIC X(10).",
    };

    private static final String[] ACCOUNT = {
        "01 GET-ACCOUNT.",
        "   05 ACCOUNT-ID PIC X(12).",
        "   05 ACCOUNT-TYPE PIC 9(2).",
        "   05 CHECKING-DATA.",
        "      10 OVERDRAFT-LIMIT PIC S9(9)V99 COMP-3.",
        "      10 ENTRY-COUNT PIC 9(2).",
        "      10 ENTRIES OCCURS 0 TO 10 DEPENDING ON ENTRY-COUNT.",
        "         15 AMOUNT PIC S9(7)V99 COMP-3.",
        "   05 SAVINGS-DATA REDEFINES CHECKING-DATA.",
        "      10 INTEREST-RATE PIC 9V9(4).",
        "      10 MATURITY-DATE PIC 9(8).",
    };

    private static MappingOptions directions(Map<String, Direction> directions) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, directions, Map.of());
    }

    private static MappingOptions subTypes(Map<String, SubTypeDecision> decisions) {
        return new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
            MappingOptions.OUTPUT_MARKERS, Map.of(), decisions);
    }

    private static SubTypeDecision accountDecision() {
        return new SubTypeDecision("ACCOUNT-TYPE",
            Map.of("CHECKING-DATA", "01", "SAVINGS-DATA", "02"),
            Map.of("SAVINGS-DATA", "SavingsAccount"));
    }

    private static List<String> errors(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR)
            .map(Diagnostic::message).toList();
    }

    @Test
    void directionSplitByMarkers() {
        MapResult result = map(INVOICE);
        assertThat(result.diagnostics()).isEmpty();
        ClassModel invoice = type(result, "GetInvoice");
        assertThat(invoice.members()).extracting(MemberModel::name, MemberModel::direction,
                MemberModel::order)
            .containsExactly(tuple("returnCode", Direction.INPUT_OUTPUT, 10),
                tuple("invoiceId", Direction.INPUT, 20),
                tuple("filler1", Direction.INPUT, 30),
                tuple("invoiceAmount", Direction.OUTPUT, 40),
                tuple("dueDate", Direction.OUTPUT, 50),
                tuple("trailer", Direction.INPUT_OUTPUT, 60));
        assertThat(member(invoice, "filler1")).isEqualTo(new FieldMember("filler1",
            JavaType.STRING, 30, "X(1)", Direction.INPUT, "FILLER",
            "FILLER PIC X(1). Added to give REQUEST-DATA the length of RESPONSE-DATA."));
    }

    @Test
    void directionSplitPadsTheOutputSide() {
        MapResult result = map("01 GET-ORDER.", "05 ORDER-IN.", "10 ORDER-ID PIC X(10).",
            "05 ORDER-OUT REDEFINES ORDER-IN.", "10 STATUS PIC X(2).", "05 TRAILER PIC X.");
        assertThat(type(result, "GetOrder").members()).extracting(MemberModel::name,
                MemberModel::direction)
            .containsExactly(tuple("orderId", Direction.INPUT),
                tuple("status", Direction.OUTPUT), tuple("filler1", Direction.OUTPUT),
                tuple("trailer", Direction.INPUT_OUTPUT));
    }

    @Test
    void directionSplitByExplicitDecision() {
        MapResult result = map(directions(Map.of("QUERY", Direction.INPUT, "DETAIL",
                Direction.OUTPUT)),
            "01 GET-ORDER.", "05 QUERY.", "10 ORDER-ID PIC X(10).",
            "05 DETAIL REDEFINES QUERY.", "10 STATUS PIC X(10).");
        assertThat(type(result, "GetOrder").members()).extracting(MemberModel::direction)
            .containsExactly(Direction.INPUT, Direction.OUTPUT);
    }

    @Test
    void fillerAlsoWhenNothingFollows() {
        MapResult result = map("01 GET-ORDER.", "05 ORDER-IN.", "10 ORDER-ID PIC X(10).",
            "05 ORDER-OUT REDEFINES ORDER-IN.", "10 STATUS PIC X(2).");
        assertThat(type(result, "GetOrder").members()).extracting(MemberModel::name)
            .containsExactly("orderId", "status", "filler1");
    }

    @Test
    void splitPadsWhenTheAreaEndsItsGroup() {
        MapResult result = map("01 MSG.", "05 BODY.", "10 REQUEST-DATA.", "15 CUST-ID PIC X(4).",
            "10 RESPONSE-DATA REDEFINES REQUEST-DATA.", "15 CUST-NAME PIC X(10).",
            "05 TRAILER PIC X(2).");
        assertThat(type(result, "Msg").members()).extracting(MemberModel::name,
                MemberModel::direction)
            .containsExactly(tuple("custId", Direction.INPUT), tuple("filler1", Direction.INPUT),
                tuple("custName", Direction.OUTPUT), tuple("trailer", Direction.INPUT_OUTPUT));
        assertThat(((FieldMember) member(type(result, "Msg"), "filler1")).pic())
            .isEqualTo("X(6)");
    }

    @Test
    void fixedTableInASplitIsPermitted() {
        MapResult result = map("01 MSG.", "05 REQUEST-DATA.", "10 QLINE OCCURS 3 PIC X(3).",
            "05 RESPONSE-DATA REDEFINES REQUEST-DATA.", "10 NAME PIC X(20).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(((FieldMember) member(type(result, "Msg"), "filler1")).pic())
            .isEqualTo("X(11)");
    }

    @Test
    void elementaryAlternativeOfAGroupIsAnError() {
        MapResult result = map("01 MSG.", "05 REQUEST-DATA.", "10 CUST-ID PIC X(4).",
            "05 RESPONSE-TEXT REDEFINES REQUEST-DATA PIC X(20).", "05 TRAILER PIC X(2).");
        assertThat(errors(result)).containsExactly("RESPONSE-TEXT is an elementary item that"
            + " redefines the group REQUEST-DATA. BookMap does not support this.");
    }

    @Test
    void variableTableInASplitIsAnError() {
        MapResult result = map("01 MSG.", "05 REQUEST-DATA.", "10 N PIC 9.",
            "10 QLINE OCCURS 1 TO 5 DEPENDING ON N PIC X(3).",
            "05 RESPONSE-DATA REDEFINES REQUEST-DATA.", "10 NAME PIC X(20).");
        assertThat(errors(result)).containsExactly("the REDEFINES alternative REQUEST-DATA has"
            + " OCCURS DEPENDING ON. A direction split needs a fixed size.");
    }

    @Test
    void ambiguousAreaIsAnError() {
        MapResult result = map("01 ORDER.", "05 PART-A.", "10 X PIC X.",
            "05 PART-B REDEFINES PART-A.", "10 Y PIC X.", "05 PART-C REDEFINES PART-A.",
            "10 Z PIC X.");
        assertThat(errors(result)).containsExactly("REDEFINES area PART-A at line 2 needs a"
            + " decision. Add a direction for each alternative, for example \"directions\":"
            + " {\"PART-A\": \"INPUT\", \"PART-B\": \"OUTPUT\"}. Or add a subtype decision, for"
            + " example \"subTypes\": {\"PART-A\": {\"discriminator\": \"...\", \"values\":"
            + " {\"PART-A\": \"01\", \"PART-B\": \"02\"}}}.");
    }

    @Test
    void sameDirectionsIsAnError() {
        MapResult result = map("01 ORDER.", "05 IN-A.", "10 X PIC X.",
            "05 IN-B REDEFINES IN-A.", "10 Y PIC X.");
        assertThat(errors(result)).singleElement().asString()
            .startsWith("REDEFINES area IN-A at line 2 needs a decision.");
    }

    @Test
    void bothMarkersIsAWarning() {
        MapResult result = map("01 ORDER.", "05 IN-OUT-DATA.", "10 X PIC X.",
            "05 OUT-DATA REDEFINES IN-OUT-DATA.", "10 Y PIC X.");
        assertThat(result.diagnostics()).extracting(Diagnostic::severity)
            .containsExactly(Severity.WARNING, Severity.ERROR);
        assertThat(result.diagnostics().get(0).message()).isEqualTo("IN-OUT-DATA has an input"
            + " marker and an output marker. Its direction is not clear.");
    }

    @Test
    void customMarkers() {
        MapResult result = map(new MappingOptions(List.of(), List.of("ENTRADA"),
                List.of("SAIDA"), Map.of(), Map.of()),
            "01 PEDIDO.", "05 DADOS-ENTRADA.", "10 X PIC X.",
            "05 DADOS-SAIDA REDEFINES DADOS-ENTRADA.", "10 Y PIC X.");
        assertThat(type(result, "Pedido").members()).extracting(MemberModel::direction)
            .containsExactly(Direction.INPUT, Direction.OUTPUT);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "RETURN-CODE|direction decision: RETURN-CODE is not a REDEFINES alternative. Outside a"
            + " REDEFINES area, a direction would move the offsets of the items after it.",
        "INVOICE-ID|direction decision: INVOICE-ID is inside a REDEFINES alternative. Set the"
            + " decision on the alternative group.",
        "NOWHERE|direction decision: item NOWHERE does not exist",
        "A OF|direction decision: 'A OF' is not a valid data name",
    })
    void directionDecisionErrors(String name, String message) {
        MapResult result = map(directions(Map.of(name, Direction.INPUT)), INVOICE);
        assertThat(result.diagnostics()).extracting(Diagnostic::source, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(tuple("ORDER.cpy", 1, message));
    }

    @Test
    void ambiguousDecisionNameIsAnError() {
        MapResult result = map(directions(Map.of("A", Direction.INPUT)),
            "01 R1. 05 A. 10 X PIC X. 05 B REDEFINES A. 10 Y PIC X.",
            "01 R2. 05 A. 10 X PIC X.");
        assertThat(errors(result)).contains("direction decision: item A is ambiguous. Qualify"
            + " it: A OF R1, A OF R2");
    }

    @Test
    void recordIsNotAnAlternative() {
        MapResult result = map(directions(Map.of("R1", Direction.INPUT)),
            "01 R1. 05 X PIC X.");
        assertThat(errors(result)).singleElement().asString()
            .startsWith("direction decision: R1 is not a REDEFINES alternative.");
    }

    @Test
    void directionSplitInsideOccursIsAnError() {
        MapResult result = map("01 ORDER.", "05 LINE OCCURS 2.", "10 LINE-IN.", "15 X PIC X.",
            "10 LINE-OUT REDEFINES LINE-IN.", "15 Y PIC X.");
        assertThat(errors(result)).containsExactly("the REDEFINES area LINE-IN is inside"
            + " OCCURS LINE. A direction split is not possible in an item class. Use subtypes,"
            + " or edit the class by hand.");
    }

    @Test
    void subtypesFromDecision() {
        MapResult result = map(subTypes(Map.of("SAVINGS-DATA", accountDecision())), ACCOUNT);
        assertThat(result.diagnostics()).isEmpty();
        ClassModel account = type(result, "GetAccount");
        assertThat(account.subTypes()).isEqualTo(new SubTypes("accountType", List.of(
            new SubTypeEntry("01", "CheckingData"), new SubTypeEntry("02", "SavingsAccount"))));
        assertThat(account.members()).extracting(MemberModel::name)
            .containsExactly("accountId", "accountType");
        ClassModel checking = type(result, "CheckingData");
        assertThat(checking.kind()).isEqualTo(ClassKind.SUBTYPE);
        assertThat(checking.superclass()).isEqualTo("GetAccount");
        assertThat(checking.members()).extracting(MemberModel::name, MemberModel::order)
            .containsExactly(tuple("overdraftLimit", 30), tuple("entryCount", 40),
                tuple("entries", 50));
        assertThat(type(result, "SavingsAccount").members()).extracting(MemberModel::order)
            .containsExactly(30, 40);
        assertThat(result.model().classes()).extracting(ClassModel::name).containsExactly(
            "GetAccount", "CheckingData", "Entries", "SavingsAccount");
    }

    @Test
    void subtypesInAnItemClass() {
        MapResult result = map(subTypes(Map.of("CARD-DATA", new SubTypeDecision("ENTRY-TYPE",
                Map.of("CARD-DATA", "C", "WIRE-DATA", "W"), Map.of()))),
            "01 STATEMENT.", "05 ENTRY OCCURS 5.", "10 ENTRY-TYPE PIC X.", "10 CARD-DATA.",
            "15 CARD-NUMBER PIC X(4).", "10 WIRE-DATA REDEFINES CARD-DATA.",
            "15 IBAN PIC X(20).");
        assertThat(type(result, "Entry").subTypes().types()).extracting(SubTypeEntry::className)
            .containsExactly("CardData", "WireData");
        assertThat(type(result, "CardData").superclass()).isEqualTo("Entry");
    }

    @Test
    void subtypeWinsOverMarkers() {
        MapResult result = map(subTypes(Map.of("REQUEST-DATA", new SubTypeDecision(
            "RETURN-CODE", Map.of("REQUEST-DATA", "A", "RESPONSE-DATA", "B"), Map.of()))),
            "01 GET-INVOICE.", "05 RETURN-CODE PIC X(4).", "05 REQUEST-DATA.",
            "10 INVOICE-ID PIC X(12).", "05 RESPONSE-DATA REDEFINES REQUEST-DATA.",
            "10 DUE-DATE PIC 9(8).");
        assertThat(type(result, "GetInvoice").subTypes()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "NOWHERE|01=A;02=B|subtype decision for CHECKING-DATA: item NOWHERE does not exist",
        "OVERDRAFT-LIMIT|01=A;02=B|the discriminator OVERDRAFT-LIMIT must be an item before the"
            + " REDEFINES area CHECKING-DATA, in the same class",
        "AMOUNT-DUE|01=A;02=B|the discriminator AMOUNT-DUE must be text or an integer",
        "ACCOUNT-TYPE|01=A|the REDEFINES alternative SAVINGS-DATA has no discriminator value",
        "ACCOUNT-TYPE|01=A;02=B;03=ACCOUNT-ID|subtype value: ACCOUNT-ID is not an alternative of"
            + " the REDEFINES area CHECKING-DATA",
    })
    void subtypeErrors(String discriminator, String values, String message) {
        Map<String, String> byName = new java.util.HashMap<>();
        for (String pair : values.split(";")) {
            String[] parts = pair.split("=");
            String alternative = switch (parts[1]) {
                case "A" -> "CHECKING-DATA";
                case "B" -> "SAVINGS-DATA";
                default -> parts[1];
            };
            byName.put(alternative, parts[0]);
        }
        String[] lines = new String[ACCOUNT.length + 1];
        lines[0] = ACCOUNT[0];
        lines[1] = "   05 AMOUNT-DUE PIC 9(3)V99.";
        System.arraycopy(ACCOUNT, 1, lines, 2, ACCOUNT.length - 1);
        MapResult result = map(subTypes(Map.of("CHECKING-DATA",
            new SubTypeDecision(discriminator, byName, Map.of()))), lines);
        assertThat(errors(result)).containsExactly(message);
    }

    @Test
    void itemAfterSubtypeAreaIsAnError() {
        String[] lines = new String[ACCOUNT.length + 1];
        System.arraycopy(ACCOUNT, 0, lines, 0, ACCOUNT.length);
        lines[ACCOUNT.length] = "   05 TRAILER PIC X.";
        MapResult result = map(subTypes(Map.of("SAVINGS-DATA", accountDecision())), lines);
        assertThat(errors(result)).containsExactly("TRAILER comes after the REDEFINES area"
            + " CHECKING-DATA. BookMap needs the redefined area at the end of the layout.");
    }

    @Test
    void twoDecisionsForOneAreaIsAnError() {
        MapResult result = map(subTypes(Map.of("SAVINGS-DATA", accountDecision(),
            "CHECKING-DATA", accountDecision())), ACCOUNT);
        assertThat(errors(result)).contains(
            "subtype decision: two decisions name the REDEFINES area CHECKING-DATA");
    }

    @Test
    void subtypeAreaInsideAnotherAreaKeepsTheRedefinedGroup() {
        MapResult result = map(subTypes(Map.of("SAVINGS-DATA", accountDecision())),
            "01 GET-ACCOUNT.", "05 ACCOUNT-TYPE PIC 9(2).", "05 CHECKING-DATA.",
            "10 LIMITS.", "15 L1 PIC 9.", "10 OTHER-LIMITS REDEFINES LIMITS.", "15 L2 PIC 9.",
            "05 SAVINGS-DATA REDEFINES CHECKING-DATA.", "10 RATE PIC 9.");
        assertThat(result.diagnostics()).extracting(Diagnostic::severity, Diagnostic::line,
                Diagnostic::message)
            .containsExactly(tuple(Severity.WARNING, 6, "the REDEFINES area LIMITS is inside"
                + " another REDEFINES area. BookMap keeps LIMITS and skips OTHER-LIMITS."
                + " See issues #38 and #39."));
        assertThat(type(result, "CheckingData").members()).extracting(MemberModel::name)
            .containsExactly("l1");
    }

    @Test
    void areaInsideADirectionSplitKeepsTheRedefinedGroup() {
        MapResult result = map("01 ORDER.", "05 ORDER-IN.", "10 A.", "15 X PIC X.",
            "10 B REDEFINES A.", "15 Y PIC X.", "10 C REDEFINES A.", "15 OCC OCCURS 2 PIC X.",
            "05 ORDER-OUT REDEFINES ORDER-IN.", "10 Z PIC X(2).");
        assertThat(errors(result)).containsExactly("C redefines A and is longer than A."
            + " BookMap keeps A, so the items after it would move.");
        assertThat(result.diagnostics()).extracting(Diagnostic::severity)
            .containsExactly(Severity.WARNING, Severity.ERROR);
        MapResult accepted = map("01 ORDER.", "05 ORDER-IN.", "10 A OCCURS 2.", "15 X PIC X.",
            "10 B REDEFINES A.", "15 Y PIC X(2).", "05 ORDER-OUT REDEFINES ORDER-IN.",
            "10 Z PIC X(2).");
        assertThat(errors(accepted)).isEmpty();
        assertThat(type(accepted, "Order").members())
            .extracting(MemberModel::name, MemberModel::direction)
            .containsExactly(tuple("a", Direction.INPUT), tuple("z", Direction.OUTPUT));
    }

    @Test
    void secondSubtypeAreaInTheSameClassIsAnError() {
        MapResult result = map(subTypes(Map.of("A", new SubTypeDecision("T",
                Map.of("A", "1", "B", "2"), Map.of()))),
            "01 ORDER.", "05 T PIC 9.", "05 A.", "10 X PIC X.", "05 B REDEFINES A.",
            "10 Y PIC X.", "05 C.", "10 Z PIC X.", "05 D REDEFINES C.", "10 W PIC X.");
        assertThat(errors(result)).containsExactly("Z comes after the REDEFINES area A."
            + " BookMap needs the redefined area at the end of the layout.");
    }

    @Test
    void alternativeWithOccursIsAnError() {
        MapResult result = map("01 ORDER.", "05 ORDER-IN.", "10 X PIC X.",
            "05 ORDER-OUT REDEFINES ORDER-IN OCCURS 2.", "10 Y PIC X.");
        assertThat(errors(result)).containsExactly("the REDEFINES alternative ORDER-OUT has"
            + " OCCURS. BookMap does not support this.");
    }

    @Test
    void countFieldOutsideAreaIsAnError() {
        MapResult result = map("01 ORDER.", "05 N PIC 9(2).", "05 ORDER-IN.", "10 X PIC X.",
            "05 ORDER-OUT REDEFINES ORDER-IN.",
            "10 LINES OCCURS 1 TO 10 DEPENDING ON N PIC X.");
        assertThat(errors(result)).containsExactly("the REDEFINES alternative ORDER-OUT has"
            + " OCCURS DEPENDING ON. A direction split needs a fixed size.");
    }

    @Test
    void lengthsFollowTheBookMapRules() {
        MapResult parsed = map("01 ORDER.", "05 A PIC X.");
        assertThat(parsed.hasErrors()).isFalse();
        pt.dazz.bookmap.copybook.ast.DataItem group = pt.dazz.bookmap.copybook.parse
            .CopybookParser.parse("L.cpy", String.join("\n", "01 L.",
                "05 T PIC S9(7)V99 COMP-3.", "05 B PIC 9(4) COMP.", "05 C PIC 9(9) COMP.",
                "05 D PIC 9(18) COMP.", "05 E OCCURS 3 PIC X(2).", "05 F.", "10 G PIC X(5).",
                "05 H REDEFINES F.", "10 I PIC X(8)."),
                new pt.dazz.bookmap.copybook.parse.ParseOptions(
                    pt.dazz.bookmap.copybook.parse.SourceFormat.FREE,
                    java.nio.charset.StandardCharsets.UTF_8, List.of(), null))
            .copybook().records().get(0);
        assertThat(RedefinesResolver.length(group,
            pt.dazz.bookmap.copybook.ast.Usage.DISPLAY)).isEqualTo(5 + 2 + 4 + 8 + 6 + 8);
    }

    @Test
    void decisionsOnItemsThatAreNotAlternatives() {
        MapResult result = map(new MappingOptions(List.of(), MappingOptions.INPUT_MARKERS,
                MappingOptions.OUTPUT_MARKERS, Map.of("B", Direction.INPUT, "PARTS",
                    Direction.OUTPUT),
                Map.of("NOWHERE", accountDecision())),
            "01 ORDER.", "05 A PIC X.", "05 B REDEFINES MISSING.", "10 Y PIC X.",
            "05 ORDER-DATE PIC 9(8).", "05 PARTS REDEFINES ORDER-DATE.", "10 YEAR PIC 9(4).");
        assertThat(errors(result)).contains(
            "subtype decision: item NOWHERE does not exist",
            "direction decision: B is not a REDEFINES alternative. Outside a REDEFINES area,"
                + " a direction would move the offsets of the items after it.",
            "direction decision: PARTS is not a REDEFINES alternative. Outside a REDEFINES"
                + " area, a direction would move the offsets of the items after it.");
    }

    @Test
    void discriminatorInAnotherClassIsAnError() {
        MapResult result = map(subTypes(Map.of("A", new SubTypeDecision("T",
                Map.of("A", "1", "B", "2"), Map.of()))),
            "01 ORDER.", "05 LINE OCCURS 2.", "10 T PIC 9.", "05 A.", "10 X PIC X.",
            "05 B REDEFINES A.", "10 Y PIC X.");
        assertThat(errors(result)).containsExactly("the discriminator T must be an item before"
            + " the REDEFINES area A, in the same class");
    }

    @Test
    void unknownValueNameIsAnError() {
        MapResult result = map(subTypes(Map.of("SAVINGS-DATA", new SubTypeDecision(
            "ACCOUNT-TYPE", Map.of("CHECKING-DATA", "01", "SAVINGS-DATA", "02", "NOWHERE",
                "03"), Map.of()))), ACCOUNT);
        assertThat(errors(result)).containsExactly("subtype value: item NOWHERE does not exist");
    }
}
