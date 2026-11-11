package pt.dazz.bookmap.examples.accounts;

import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import java.time.LocalDate;
import pt.dazz.bookmap.annotation.BookField;

/**
 * A savings account: account type {@code 02}.
 */
public class SavingsAccount extends GetAccount {

    @BookField(order = 50, pic = "9V9(4)", direction = OUTPUT)
    private BigDecimal interestRate;

    @BookField(order = 60, direction = OUTPUT)
    private LocalDate maturityDate;

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public void setMaturityDate(LocalDate maturityDate) {
        this.maturityDate = maturityDate;
    }
}
