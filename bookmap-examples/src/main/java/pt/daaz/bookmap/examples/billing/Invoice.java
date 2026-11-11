package pt.daaz.bookmap.examples.billing;

import java.math.BigDecimal;
import lombok.Data;
import pt.daaz.bookmap.annotation.BookField;

/**
 * One invoice. The amount is COMP-3 because of the package defaults.
 */
@Data
public class Invoice {

    @BookField(order = 10, pic = "X(10)")
    private String invoiceNumber;

    @BookField(order = 20, pic = "S9(7)V99")
    private BigDecimal amount;
}
