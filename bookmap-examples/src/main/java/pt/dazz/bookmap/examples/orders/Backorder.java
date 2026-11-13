package pt.dazz.bookmap.examples.orders;

import java.math.BigDecimal;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;

/**
 * One backorder in a list: 17 bytes. An order number of zero marks an unused entry.
 */
@Data
public class Backorder {

    @BookField(order = 10, pic = "9(8)")
    private long orderNumber;

    @BookField(order = 20, pic = "X(3)")
    private String currency;

    @BookField(order = 30, pic = "S9(9)V99 COMP-3")
    private BigDecimal amount;
}
