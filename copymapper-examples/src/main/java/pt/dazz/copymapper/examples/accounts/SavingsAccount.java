package pt.dazz.copymapper.examples.accounts;

import static pt.dazz.copymapper.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import java.time.LocalDate;
import pt.dazz.copymapper.annotation.Field;

/**
 * A savings account: account type {@code 02}.
 */
public class SavingsAccount extends GetAccount {

    @Field(order = 50, pic = "9V9(4)", direction = OUTPUT)
    private BigDecimal interestRate;

    @Field(order = 60, direction = OUTPUT)
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
