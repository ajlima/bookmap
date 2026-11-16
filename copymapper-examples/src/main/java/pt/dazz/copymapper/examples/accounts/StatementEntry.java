package pt.dazz.copymapper.examples.accounts;

import java.math.BigDecimal;
import pt.dazz.copymapper.annotation.Field;
import pt.dazz.copymapper.annotation.SubTypes;

/**
 * One entry of an account statement. The entry type selects the layout: {@code C} for a card
 * payment, {@code T} for a transfer. Each entry occupies the length of the longest layout.
 */
@SubTypes(discriminator = "entryType", value = {
    @SubTypes.Type(value = "C", type = CardPayment.class),
    @SubTypes.Type(value = "T", type = Transfer.class)
})
public abstract class StatementEntry {

    @Field(order = 10, pic = "X")
    private String entryType;

    @Field(order = 20, pic = "S9(13)V99 COMP-3")
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
