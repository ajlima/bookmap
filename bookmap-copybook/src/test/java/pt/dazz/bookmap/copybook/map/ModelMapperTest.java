package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pt.dazz.bookmap.copybook.map.Mapping.diagnostics;
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
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.MemberModel;

class ModelMapperTest {

    private static List<String> errors(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR)
            .map(Diagnostic::message).toList();
    }

    @Test
    void recordBecomesOperationClass() {
        MapResult result = map("01 GET-CUSTOMER-ORDERS.", "05 CUSTOMER-ID PIC X(10).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.model().sourceName()).isEqualTo("ORDER.cpy");
        assertThat(result.model().formatVersion()).isEqualTo(1);
        ClassModel operation = result.model().classes().get(0);
        assertThat(operation.name()).isEqualTo("GetCustomerOrders");
        assertThat(operation.kind()).isEqualTo(ClassKind.OPERATION);
        assertThat(operation.superclass()).isNull();
        assertThat(operation.subTypes()).isNull();
        assertThat(operation.members()).containsExactly(new FieldMember("customerId",
            JavaType.STRING, 10, "X(10)", Direction.INPUT_OUTPUT, "CUSTOMER-ID",
            "05 CUSTOMER-ID PIC X(10)."));
    }

    @Test
    void groupsAreFlattened() {
        MapResult result = map("01 ORDER.", "05 HEADER.", "10 ORDER-ID PIC X(10).",
            "10 DATES.", "15 ORDER-DATE PIC 9(8).", "05 TOTAL PIC 9(7).");
        assertThat(result.model().classes()).hasSize(1);
        assertThat(result.model().classes().get(0).members()).extracting(MemberModel::name)
            .containsExactly("orderId", "orderDate", "total");
    }

    @Test
    void fieldTypesFollowThePicture() {
        MapResult result = map("01 ORDER.", "05 A PIC X.", "05 B PIC 9(9).", "05 C PIC 9(10).",
            "05 D PIC 9(18).", "05 E PIC 9(19).", "05 F PIC S9V99.");
        assertThat(result.model().classes().get(0).members())
            .extracting(m -> ((FieldMember) m).javaType())
            .containsExactly(JavaType.STRING, JavaType.INT, JavaType.LONG, JavaType.LONG,
                JavaType.BIG_DECIMAL, JavaType.BIG_DECIMAL);
    }

    @Test
    void picTextKeepsUsage() {
        MapResult result = map("01 ORDER.", "05 AMOUNTS COMP-3.", "10 NET PIC S9(7)V99.",
            "10 TAX PIC S9(5)V99 DISPLAY.", "05 QTY PIC 9(4) COMP.", "05 CODE PIC X(3).");
        assertThat(result.model().classes().get(0).members())
            .extracting(m -> ((FieldMember) m).pic())
            .containsExactly("S9(7)V99 COMP-3", "S9(5)V99", "9(4) COMP", "X(3)");
    }

    @Test
    void picTextKeepsTheSignClause() {
        MapResult result = map("01 ORDER.", "05 AMOUNTS SIGN LEADING SEPARATE.",
            "10 NET PIC S9(5)V99.", "10 TAX PIC S9(3) SIGN TRAILING.",
            "05 TOTAL PIC S9(5) TRAILING SEPARATE CHARACTER.", "05 DISCOUNT PIC S9(3) LEADING.");
        assertThat(result.model().classes().get(0).members())
            .extracting(m -> ((FieldMember) m).pic())
            .containsExactly("S9(5)V99 SIGN LEADING SEPARATE", "S9(3)",
                "S9(5) SIGN TRAILING SEPARATE", "S9(3) SIGN LEADING");
        // 8 + 3 + 6 + 3
        assertThat(result.diagnostics()).extracting(Diagnostic::message)
            .containsExactly("record ORDER has 20 bytes");
    }

    @Test
    void recordUsageIsInherited() {
        MapResult result = map("01 ORDER COMP.", "05 QTY PIC 9(4).");
        assertThat(((FieldMember) result.model().classes().get(0).members().get(0)).pic())
            .isEqualTo("9(4) COMP");
    }

    @Test
    void ordersStepByTen() {
        MapResult result = map("01 ORDER.", "05 A PIC X.", "05 B PIC X.", "05 C PIC X.");
        assertThat(result.model().classes().get(0).members()).extracting(MemberModel::order)
            .containsExactly(10, 20, 30);
    }

    @Test
    void fillersAreNumberedPerClass() {
        MapResult result = map("01 ORDER.", "05 FILLER PIC X(3).", "05 PIC X.",
            "05 LINE OCCURS 2.", "10 FILLER PIC X.", "10 SKU PIC X(5).",
            "05 FILLER OCCURS 2 PIC X.");
        assertThat(type(result, "Order").members()).extracting(MemberModel::name)
            .containsExactly("filler1", "filler2", "line", "filler3");
        assertThat(type(result, "Line").members()).extracting(MemberModel::name)
            .containsExactly("filler1", "sku");
        assertThat(type(result, "Filler").members()).extracting(MemberModel::name)
            .containsExactly("value");
    }

    @Test
    void fixedOccursBecomesFixedCollection() {
        MapResult result = map("01 ORDER.", "05 LINE-COUNT PIC 9(2).",
            "05 ORDER-LINE OCCURS 20 TIMES.", "10 SKU PIC X(8).", "10 QTY PIC 9(5).",
            "05 TOTAL PIC 9(9).");
        assertThat(result.hasErrors()).isFalse();
        assertThat(member(type(result, "Order"), "orderLine")).isEqualTo(new CollectionMember(
            "orderLine", 20, Direction.INPUT_OUTPUT, "ORDER-LINE", "05 ORDER-LINE OCCURS 20 TIMES.",
            20, true, null, "OrderLine"));
        ClassModel line = type(result, "OrderLine");
        assertThat(line.kind()).isEqualTo(ClassKind.ITEM);
        assertThat(line.members()).extracting(MemberModel::name, MemberModel::order)
            .containsExactly(tuple("sku", 10), tuple("qty", 20));
        assertThat(result.model().classes()).extracting(ClassModel::name)
            .containsExactly("Order", "OrderLine");
    }

    @Test
    void occursDependingOnUsesTheCountField() {
        MapResult result = map("01 ORDER.", "05 WS-LINE-COUNT PIC 9(3).",
            "05 ORDER-LINE OCCURS 1 TO 50 DEPENDING ON WS-LINE-COUNT.", "10 SKU PIC X(8).");
        CollectionMember lines = (CollectionMember) member(type(result, "Order"), "orderLine");
        assertThat(lines.fixed()).isFalse();
        assertThat(lines.maxOccurs()).isEqualTo(50);
        assertThat(lines.dependsOn()).isEqualTo("wsLineCount");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "05 A PIC X.|05 L OCCURS 2 DEPENDING ON N PIC X.|DEPENDING ON item N does not exist",
        "05 G1. 10 N PIC 9. 05 G2. 10 N PIC 9.|05 L OCCURS 2 DEPENDING ON N PIC X."
            + "|DEPENDING ON item N is ambiguous. Qualify it: N OF G1 OF ORDER, N OF G2 OF ORDER",
        "05 A PIC X.|05 L OCCURS 2 DEPENDING ON N PIC X. 05 N PIC 9."
            + "|DEPENDING ON item N must be in the same class, before the table",
        "05 N PIC X.|05 L OCCURS 2 DEPENDING ON N PIC X.|DEPENDING ON item N must be an integer",
        "05 N PIC 9.|05 L OCCURS 20 DEPENDING ON N PIC X."
            + "|DEPENDING ON item N has 1 digits and cannot hold 20",
    })
    void dependsOnErrors(String before, String table, String message) {
        MapResult result = map("01 ORDER.", before, table);
        assertThat(errors(result)).containsExactly(message);
        assertThat(result.model()).isNull();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void dependsOnInAnotherClassIsAnError() {
        MapResult result = map("01 ORDER.", "05 OUTER OCCURS 2.", "10 N PIC 9.",
            "05 L OCCURS 2 DEPENDING ON N PIC X.");
        assertThat(errors(result))
            .containsExactly("DEPENDING ON item N must be in the same class, before the table");
    }

    @Test
    void largeCountFieldIsAccepted() {
        MapResult result = map("01 ORDER.", "05 N PIC 9(10).",
            "05 L OCCURS 2 DEPENDING ON N PIC X.");
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void elementaryOccursGetsValueItem() {
        MapResult result = map("01 CUSTOMER.", "05 PHONE-NUMBER OCCURS 3 PIC X(15).");
        assertThat(member(type(result, "Customer"), "phoneNumber"))
            .isInstanceOf(CollectionMember.class);
        assertThat(type(result, "PhoneNumber").members()).containsExactly(new FieldMember(
            "value", JavaType.STRING, 10, "X(15)", Direction.INPUT_OUTPUT, "PHONE-NUMBER",
            "05 PHONE-NUMBER OCCURS 3 PIC X(15)."));
    }

    @Test
    void nestedOccursMapsToAnInnerCollection() {
        MapResult result = map("01 ORDER.", "05 LINE OCCURS 2.", "10 TAG OCCURS 3 PIC X.");
        assertThat(errors(result)).isEmpty();
        assertThat(member(type(result, "Line"), "tag")).isInstanceOf(CollectionMember.class);
    }

    @Test
    void fieldCollisionUsesGroupName() {
        MapResult result = map("01 ORDER.", "05 BILLING-ADDRESS.", "10 CITY PIC X(20).",
            "05 SHIPPING-ADDRESS.", "10 CITY PIC X(20).", "05 CITY PIC X(5).",
            "05 SHIPPING-ADDRESS-CITY PIC X.");
        assertThat(type(result, "Order").members()).extracting(MemberModel::name)
            .containsExactly("city", "shippingAddressCity", "city2", "shippingAddressCity2");
    }

    @Test
    void classCollisionUsesParentName() {
        MapResult result = map("01 ORDER.", "05 LINE OCCURS 2 PIC X.", "01 RETURN.",
            "05 LINE OCCURS 2 PIC X.", "01 ORDER-COPY.", "05 ORDER OCCURS 2 PIC X.",
            "01 ORDER.", "05 A PIC X.");
        assertThat(result.model().classes()).extracting(ClassModel::name).containsExactly(
            "Order", "Line", "Return", "ReturnLine", "OrderCopy", "OrderCopyOrder", "Order2");
    }

    @Test
    void elementaryRedefinesIsSkippedWithWarning() {
        MapResult result = map("01 ORDER.", "05 ORDER-DATE PIC 9(8).",
            "05 ORDER-DATE-X REDEFINES ORDER-DATE PIC X(8).");
        assertThat(type(result, "Order").members()).extracting(MemberModel::name)
            .containsExactly("orderDate");
        assertThat(diagnostics(result)).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(tuple(Severity.WARNING,
                "ORDER-DATE-X redefines ORDER-DATE and is skipped"));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void elementaryRedefinesOfAMissingItemIsSkipped() {
        MapResult result = map("01 ORDER.", "05 A PIC X(2).", "05 B REDEFINES NOWHERE PIC X.");
        assertThat(diagnostics(result)).extracting(Diagnostic::severity)
            .containsExactly(Severity.WARNING);
    }

    @Test
    void longerElementaryRedefinesIsAnError() {
        MapResult result = map("01 ORDER.", "05 A PIC X(2).", "05 B REDEFINES A PIC X(6).",
            "05 C PIC X.");
        assertThat(errors(result)).containsExactly("B redefines A and is longer than A."
            + " BookMap keeps A, so the items after it would move.");
    }

    @Test
    void groupRedefiningAnElementaryItemIsAnError() {
        MapResult result = map("01 ORDER.", "05 ORDER-DATE PIC 9(8).",
            "05 PARTS REDEFINES ORDER-DATE.", "10 YEAR PIC 9(4).", "10 REST PIC 9(4).");
        assertThat(errors(result)).containsExactly(
            "PARTS redefines ORDER-DATE, which is not a group with the same parent");
    }

    @Test
    void redefinesOfAMissingItemIsAnError() {
        MapResult result = map("01 ORDER.", "05 A PIC X.", "05 B REDEFINES NOWHERE.",
            "10 Y PIC X.", "05 C PIC X.", "05 D REDEFINES C PIC X.");
        assertThat(errors(result)).containsExactly(
            "B redefines NOWHERE, which is not a group with the same parent");
    }

    @Test
    void conditionsOfAGroupAreInfo() {
        MapResult result = map("01 ORDER.", "05 STATUS.", "88 STATUS-OPEN VALUE 'O'.",
            "10 CODE PIC X.");
        assertThat(diagnostics(result)).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(tuple(Severity.INFO, "condition STATUS-OPEN is ignored"));
    }

    @Test
    void compItemInFixedTableGivesInfo() {
        MapResult result = map("01 ORDER.", "05 QTY OCCURS 5 PIC 9(4) COMP.",
            "05 N PIC 9(2).", "05 OTHER OCCURS 5 DEPENDING ON N PIC 9(4) COMP.",
            "05 CODES OCCURS 5 PIC X.");
        assertThat(diagnostics(result)).extracting(Diagnostic::severity, Diagnostic::message)
            .containsExactly(tuple(Severity.INFO, "QTY: a COMP value of 0 is all LOW-VALUES and"
                + " cannot be written. Link the count field with the option countFields, or the"
                + " key item with the option emptyKeys, if the program has one."));
    }

    @Test
    void textWithCompUsageIsAnError() {
        MapResult result = map("01 ORDER.", "05 CODE PIC X(4) COMP.",
            "05 BIG PIC 9(19) COMP.", "05 HUGE PIC 9(19) COMP-5.", "05 PACKED PIC 9(19) COMP-3.");
        assertThat(errors(result)).containsExactly(
            "CODE has a text picture and must not have COMP or COMP-3",
            "BIG: COMP supports at most 18 digits",
            "HUGE: COMP supports at most 18 digits");
    }

    @Test
    void comp5IsMappedAsCompWithTheTypeOfTheBytes() {
        MapResult result = map("01 ORDER COMP-5.", "05 A PIC 9(4).", "05 B PIC S9(4).",
            "05 C PIC S9(9).", "05 D PIC 9(9).", "05 E PIC S9(18).", "05 F PIC 9(10).",
            "05 G PIC S9(5)V99.");
        assertThat(result.model().classes().get(0).members())
            .extracting(m -> ((FieldMember) m).javaType(), m -> ((FieldMember) m).pic())
            .containsExactly(tuple(JavaType.INT, "9(4) COMP"), tuple(JavaType.INT, "S9(4) COMP"),
                tuple(JavaType.INT, "S9(9) COMP"), tuple(JavaType.LONG, "9(9) COMP"),
                tuple(JavaType.LONG, "S9(18) COMP"), tuple(JavaType.BIG_DECIMAL, "9(10) COMP"),
                tuple(JavaType.BIG_DECIMAL, "S9(5)V99 COMP"));
        List<Diagnostic> warnings = result.diagnostics().stream()
            .filter(d -> d.severity() == Severity.WARNING).toList();
        assertThat(warnings).hasSize(7);
        assertThat(warnings.get(0).message()).isEqualTo("COMP-5 on A is mapped as"
            + " COMP. BookMap reads all binary values, but writes only values with at most 4"
            + " digits.");
    }

    @Test
    void groupRedefinesNeedsADecision() {
        MapResult result = map("01 ORDER.", "05 A.", "10 X PIC X.", "05 B REDEFINES A.",
            "10 Y PIC X.");
        assertThat(errors(result)).singleElement().asString()
            .startsWith("REDEFINES area A at line 2 needs a decision.");
    }

    @Test
    void defaultOptions() {
        MappingOptions defaults = MappingOptions.defaults();
        assertThat(defaults.inputMarkers()).containsExactly("INPUT", "IN", "REQUEST", "REQ");
        assertThat(defaults.outputMarkers()).containsExactly("OUTPUT", "OUT", "RESPONSE",
            "RESP");
        assertThat(defaults.stripPrefixes()).isEmpty();
        assertThat(defaults.directions()).isEqualTo(Map.of());
        assertThat(defaults.subTypes()).isEqualTo(Map.of());
    }
}
