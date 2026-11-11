package pt.dazz.bookmap.examples.accounts;

import pt.dazz.bookmap.annotation.BookField;

/**
 * A transfer: entry type {@code T}.
 */
public class Transfer extends StatementEntry {

    @BookField(order = 30, pic = "X(34)")
    private String counterpartyIban;

    public String getCounterpartyIban() {
        return counterpartyIban;
    }

    public void setCounterpartyIban(String counterpartyIban) {
        this.counterpartyIban = counterpartyIban;
    }
}
