package pt.dazz.bookmap.examples.accounts;

import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import java.util.List;
import pt.dazz.bookmap.annotation.BookCollection;
import pt.dazz.bookmap.annotation.BookField;

/**
 * A checking account: account type {@code 01}. The statement entries are card payments and
 * transfers.
 */
public class CheckingAccount extends GetAccount {

    @BookField(order = 50, pic = "S9(9)V99 COMP-3", direction = OUTPUT)
    private BigDecimal overdraftLimit;

    @BookField(order = 60, pic = "9(2)")
    private int entryCount;

    @BookCollection(order = 70, maxOccurs = 10)
    private List<StatementEntry> entries;

    public BigDecimal getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(BigDecimal overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = entryCount;
    }

    public List<StatementEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<StatementEntry> entries) {
        this.entries = entries;
    }
}
