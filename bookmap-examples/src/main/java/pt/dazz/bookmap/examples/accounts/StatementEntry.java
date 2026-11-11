package pt.dazz.bookmap.examples.accounts;

import java.math.BigDecimal;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookSubTypes;

/**
 * One entry of an account statement. The entry type selects the layout: {@code C} for a card
 * payment, {@code T} for a transfer. Each entry occupies the length of the longest layout.
 */
@BookSubTypes(discriminator = "entryType", value = {
    @BookSubTypes.Type(value = "C", type = CardPayment.class),
    @BookSubTypes.Type(value = "T", type = Transfer.class)
})
public abstract class StatementEntry {

    @BookField(order = 10, pic = "X")
    private String entryType;

    @BookField(order = 20, pic = "S9(13)V99 COMP-3")
    private BigDecimal amount;

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
