package pt.daaz.bookmap.examples.billing;

import java.math.BigDecimal;
import lombok.Data;
import pt.daaz.bookmap.annotation.BookField;

/**
 * One invoice. Both numeric fields are COMP-3 because of the package defaults.
 * The amount takes its sign from the pic. The tax takes it from the defaults.
 */
@Data
public class Invoice {

    @BookField(order = 10, pic = "X(10)")
    private String invoiceNumber;

    @BookField(order = 20, pic = "S9(7)V99")
    private BigDecimal amount;

    @BookField(order = 30, length = 7, scale = 2)
    private BigDecimal tax;
}
