package pt.dazz.copymapper.copybook;

import java.util.List;
import pt.dazz.copymapper.annotation.Direction;
import pt.dazz.copymapper.annotation.EmptyItem;
import pt.dazz.copymapper.copybook.model.BookModel;
import pt.dazz.copymapper.copybook.model.ClassKind;
import pt.dazz.copymapper.copybook.model.ClassModel;
import pt.dazz.copymapper.copybook.model.CollectionMember;
import pt.dazz.copymapper.copybook.model.ConditionMember;
import pt.dazz.copymapper.copybook.model.FieldMember;
import pt.dazz.copymapper.copybook.model.GroupMember;
import pt.dazz.copymapper.copybook.model.JavaType;
import pt.dazz.copymapper.copybook.model.SubTypeEntry;
import pt.dazz.copymapper.copybook.model.SubTypes;
import pt.dazz.copymapper.copybook.model.ValueRange;

/** Test models. */
public final class Models {

    private Models() {
    }

    /**
     * An account book with a fixed and a variable table, two subtypes, date fields, and the
     * {@code Continuable} and {@code Validatable} handlers.
     */
    public static BookModel accountModel() {
        ClassModel account = new ClassModel("GetAccount", ClassKind.OPERATION, null,
            new SubTypes("accountType", List.of(new SubTypeEntry("01", "CheckingData"),
                new SubTypeEntry("02", "SavingsAccount"))),
            List.of(
                new FieldMember("accountId", JavaType.STRING, 10, "X(12)",
                    Direction.INPUT_OUTPUT, "ACCOUNT-ID", "05 ACCOUNT-ID PIC X(12)."),
                new FieldMember("accountType", JavaType.INT, 20, "9(2)",
                    Direction.INPUT_OUTPUT, "ACCOUNT-TYPE", "05 ACCOUNT-TYPE PIC 9(2)."),
                new CollectionMember("holder", 30, Direction.OUTPUT, "HOLDER",
                    "05 HOLDER OCCURS 2.", 2, true, null, "Holder")),
            "com.example.accounts.AccountPaging", "ReturnCodes");
        ClassModel holder = new ClassModel("Holder", ClassKind.ITEM, null, null, List.of(
            new FieldMember("holderName", JavaType.STRING, 10, "X(30)", Direction.INPUT_OUTPUT,
                "HOLDER-NAME", "10 HOLDER-NAME PIC X(30)."),
            new FieldMember("since", JavaType.LOCAL_DATE, 20, "9(8)", Direction.INPUT_OUTPUT,
                "SINCE", "10 SINCE PIC 9(8).", "yyyyMMdd"),
            new FieldMember("openedAt", JavaType.LOCAL_TIME, 30, "X(8)", Direction.INPUT_OUTPUT,
                "OPENED-AT", "10 OPENED-AT PIC X(8).", "HH:mm:ss"),
            new FieldMember("lastUpdate", JavaType.LOCAL_DATE_TIME, 40, "9(14)",
                Direction.INPUT_OUTPUT, "LAST-UPDATE", "10 LAST-UPDATE PIC 9(14).",
                "yyyyMMddHHmmss")));
        ClassModel checking = new ClassModel("CheckingData", ClassKind.SUBTYPE, "GetAccount",
            null, List.of(
                new FieldMember("overdraftLimit", JavaType.BIG_DECIMAL, 40, "S9(9)V99 COMP-3",
                    Direction.OUTPUT, "OVERDRAFT-LIMIT",
                    "10 OVERDRAFT-LIMIT PIC S9(9)V99 COMP-3."),
                new FieldMember("entryCount", JavaType.LONG, 50, "9(10)", Direction.OUTPUT,
                    "ENTRY-COUNT", "10 ENTRY-COUNT PIC 9(10)."),
                new CollectionMember("entry", 60, Direction.OUTPUT, "ENTRY",
                    "10 ENTRY OCCURS 0 TO 10 DEPENDING ON ENTRY-COUNT.", 10, false,
                    "entryCount", "Entry")));
        return new BookModel(BookModel.FORMAT_VERSION, "ACCOUNT.cpy",
            List.of(account, holder, checking));
    }

    /** A model with a fixed table that has the markers SPACES and a key. */
    public static BookModel markerModel() {
        FieldMember number = new FieldMember("invoiceNumber", JavaType.STRING, 10, "X(10)",
            Direction.INPUT_OUTPUT, "INVOICE-NUMBER", "10 INVOICE-NUMBER PIC X(10).");
        return new BookModel(BookModel.FORMAT_VERSION, "LIST-INVOICES.cpy", List.of(
            new ClassModel("ListInvoices", ClassKind.OPERATION, null, null, List.of(
                new CollectionMember("invoiceLine", 10, Direction.OUTPUT, "INVOICE-LINE",
                    "05 INVOICE-LINE OCCURS 5 TIMES.", 5, true, null, "InvoiceLine",
                    EmptyItem.SPACES, "invoiceNumber"))),
            new ClassModel("InvoiceLine", ClassKind.ITEM, null, null, List.of(number))));
    }

    /** A model with a partial table: the response can end inside it. */
    public static BookModel partialModel() {
        FieldMember orderId = new FieldMember("orderId", JavaType.STRING, 10, "X(12)",
            Direction.INPUT_OUTPUT, "ORDER-ID", "10 ORDER-ID PIC X(12).");
        return new BookModel(BookModel.FORMAT_VERSION, "LIST-OPEN-ORDERS.cpy", List.of(
            new ClassModel("ListOpenOrders", ClassKind.OPERATION, null, null, List.of(
                new CollectionMember("openOrder", 10, Direction.OUTPUT, "OPEN-ORDER",
                    "05 OPEN-ORDER OCCURS 10.", 10, true, null, "OpenOrder",
                    EmptyItem.LOW_VALUES, null, true))),
            new ClassModel("OpenOrder", ClassKind.ITEM, null, null, List.of(orderId))));
    }

    /**
     * Gives a model with a {@code REDEFINES} view area: a text, a number view with a level 88
     * item, a table view, and a group view.
     */
    public static BookModel viewModel() {
        FieldMember code = new FieldMember("code", JavaType.STRING, 10, "X(4)",
            Direction.OUTPUT, "CODE", "05 CODE PIC X(4).", null, null, null, List.of(), "");
        FieldMember number = new FieldMember("number", JavaType.INT, 20, "9(4)",
            Direction.OUTPUT, "NUM", "05 NUM REDEFINES CODE PIC 9(4).", null, null, null,
            List.of(new ConditionMember("ZERO-NUM", "ZERO_NUM", "isZeroNum",
                List.of(new ValueRange("0", null)))), "code");
        FieldMember total = new FieldMember("total", JavaType.LONG, 30, "9(4)",
            Direction.OUTPUT, "TOTAL", "05 TOTAL REDEFINES CODE PIC 9(4).", null, null, null,
            List.of(new ConditionMember("SMALL-TOTAL", "SMALL_TOTAL", "isSmallTotal",
                List.of(new ValueRange("1", "9")))), "code");
        CollectionMember parts = new CollectionMember("parts", 40, Direction.OUTPUT, "PARTS",
            "05 PARTS REDEFINES CODE OCCURS 2.", 2, true, null, "Parts", EmptyItem.LOW_VALUES,
            null, false, "code");
        GroupMember detail = new GroupMember("detail", 50, Direction.OUTPUT, "DETAIL",
            "05 DETAIL REDEFINES CODE.", "Detail", "code");
        FieldMember part = new FieldMember("value", JavaType.STRING, 10, "X(2)",
            Direction.INPUT_OUTPUT, "PARTS", "05 PARTS REDEFINES CODE OCCURS 2.");
        FieldMember text = new FieldMember("text", JavaType.STRING, 10, "X(4)",
            Direction.OUTPUT, "TEXT", "10 TEXT PIC X(4).");
        return new BookModel(BookModel.FORMAT_VERSION, "GET-CODE.cpy", List.of(
            new ClassModel("GetCode", ClassKind.OPERATION, null, null,
                List.of(code, number, total, parts, detail)),
            new ClassModel("Parts", ClassKind.ITEM, null, null, List.of(part)),
            new ClassModel("Detail", ClassKind.GROUP, null, null, List.of(text))));
    }
}
