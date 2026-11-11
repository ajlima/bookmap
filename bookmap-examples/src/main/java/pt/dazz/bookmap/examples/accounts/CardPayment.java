package pt.dazz.bookmap.examples.accounts;

import pt.dazz.bookmap.annotation.BookField;

/**
 * A card payment: entry type {@code C}.
 */
public class CardPayment extends StatementEntry {

    @BookField(order = 30, pic = "X(20)")
    private String merchant;

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }
}
