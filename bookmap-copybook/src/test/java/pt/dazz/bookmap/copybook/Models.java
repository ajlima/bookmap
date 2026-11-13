package pt.dazz.bookmap.copybook;

import java.util.List;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.copybook.model.BookModel;
import pt.dazz.bookmap.copybook.model.ClassKind;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.CollectionMember;
import pt.dazz.bookmap.copybook.model.FieldMember;
import pt.dazz.bookmap.copybook.model.JavaType;
import pt.dazz.bookmap.copybook.model.SubTypeEntry;
import pt.dazz.bookmap.copybook.model.SubTypes;

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

}
