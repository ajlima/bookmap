package pt.dazz.bookmap.examples.orders;

import java.math.BigDecimal;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookField;

/**
 * One tax of an order line: 9 bytes.
 */
@Data
public class LineTax {

    @BookField(order = 10, pic = "X(4)")
    private String taxCode;

    @BookField(order = 20, pic = "S9(7)V99 COMP-3")
    private BigDecimal taxAmount;
}
