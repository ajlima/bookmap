package pt.dazz.bookmap.examples.accounts;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import pt.dazz.bookmap.Continuable;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;
import pt.dazz.bookmap.annotation.BookSubTypes;

/**
 * Reads a bank account. The remote program sets the account type in the response. The type
 * selects the layout of the end of the book (COBOL {@code REDEFINES}):
 *
 * <pre>
 * 01  GET-ACCOUNT.
 *     05  ACCOUNT-ID        PIC X(12).
 *     05  RESTART-KEY       PIC X(10).
 *     05  MORE-DATA         PIC X.
 *     05  ACCOUNT-TYPE      PIC 9(2).
 *     05  CHECKING-DATA.
 *         10  OVERDRAFT-LIMIT   PIC S9(9)V99 COMP-3.
 *         10  ENTRY-COUNT       PIC 9(2).
 *         10  ENTRIES OCCURS 0 TO 10 DEPENDING ON ENTRY-COUNT.
 *             ...
 *     05  SAVINGS-DATA REDEFINES CHECKING-DATA.
 *         10  INTEREST-RATE     PIC 9V9(4).
 *         10  MATURITY-DATE     PIC 9(8).
 * </pre>
 *
 * <p>Send a {@code GetAccount}. The client gives a {@link CheckingAccount} or a
 * {@link SavingsAccount}.
 */
@BookOperation
@BookSubTypes(discriminator = "accountType", value = {
    @BookSubTypes.Type(value = "01", type = CheckingAccount.class),
    @BookSubTypes.Type(value = "02", type = SavingsAccount.class)
})
public class GetAccount implements Continuable {

    @BookField(order = 10, pic = "X(12)", direction = INPUT)
    private String accountId;

    @BookField(order = 20, pic = "X(10)")
    private String restartKey;

    @BookField(order = 30, pic = "X", direction = OUTPUT)
    private String moreData;

    @BookField(order = 40, pic = "9(2)")
    private int accountType;

    @Override
    public boolean hasContinuation() {
        return "Y".equals(moreData);
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getRestartKey() {
        return restartKey;
    }

    public void setRestartKey(String restartKey) {
        this.restartKey = restartKey;
    }

    public String getMoreData() {
        return moreData;
    }

    public void setMoreData(String moreData) {
        this.moreData = moreData;
    }

    public int getAccountType() {
        return accountType;
    }

    public void setAccountType(int accountType) {
        this.accountType = accountType;
    }
}
