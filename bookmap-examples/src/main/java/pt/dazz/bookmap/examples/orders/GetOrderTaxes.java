package pt.dazz.bookmap.examples.orders;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookCollection;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;

/**
 * Gives the lines of an order and the taxes of each line. The book has a table in a table
 * entry: each order line has a fixed table of taxes, with a count field in the entry.
 *
 * <pre>
 * 01  GET-ORDER-TAXES.
 *     05  ORDER-ID              PIC X(10).
 *     05  LINE-COUNT            PIC 9(2).
 *     05  ORDER-LINE OCCURS 0 TO 20 DEPENDING ON LINE-COUNT.
 *         10  PRODUCT-CODE      PIC X(8).
 *         10  QUANTITY          PIC 9(5).
 *         10  TAX-COUNT         PIC 9.
 *         10  LINE-TAX OCCURS 3.
 *             15  TAX-CODE      PIC X(4).
 *             15  TAX-AMOUNT    PIC S9(7)V99 COMP-3.
 *     05  ORDER-TOTAL           PIC S9(9)V99 COMP-3.
 * </pre>
 */
@Data
@BookOperation
public class GetOrderTaxes {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String orderId;

    @BookField(order = 20, pic = "9(2)")
    private int lineCount;

    @BookCollection(order = 30, maxOccurs = 20)
    private List<TaxedOrderLine> lines;

    @BookField(order = 40, pic = "S9(9)V99 COMP-3", direction = OUTPUT)
    private BigDecimal orderTotal;
}
