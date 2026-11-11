package pt.dazz.bookmap.examples.orders;

import java.math.BigDecimal;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;

/**
 * One line of an order: 18 bytes.
 */
@Data
public class OrderLine {

    @BookField(order = 10, pic = "X(8)")
    private String productCode;

    @BookField(order = 20, pic = "9(5)")
    private int quantity;

    @BookField(order = 30, pic = "S9(7)V99 COMP-3")
    private BigDecimal unitPrice;
}
