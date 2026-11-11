package pt.dazz.bookmap.examples.orders;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import pt.dazz.bookmap.Continuable;
import pt.dazz.bookmap.annotation.BookCollection;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;

/**
 * Reads the lines of an order. The book has a fixed table without a count field: the unused
 * slots contain LOW-VALUES. The order total comes after the table, at a fixed offset.
 *
 * <pre>
 * 01  GET-ORDER.
 *     05  ORDER-ID          PIC X(10).
 *     05  RESTART-KEY       PIC X(4).
 *     05  MORE-DATA         PIC X.
 *     05  ORDER-LINE OCCURS 3.
 *         10  PRODUCT-CODE  PIC X(8).
 *         10  QUANTITY      PIC 9(5).
 *         10  UNIT-PRICE    PIC S9(7)V99 COMP-3.
 *     05  ORDER-TOTAL       PIC S9(9)V99 COMP-3.
 * </pre>
 */
@Data
@BookOperation
public class GetOrder implements Continuable {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String orderId;

    @BookField(order = 20, pic = "X(4)")
    private String restartKey;

    @BookField(order = 30, pic = "X", direction = OUTPUT)
    private String moreData;

    @BookCollection(order = 40, maxOccurs = 3, fixed = true)
    private List<OrderLine> lines;

    @BookField(order = 50, pic = "S9(9)V99 COMP-3", direction = OUTPUT)
    private BigDecimal orderTotal;

    @Override
    public boolean hasContinuation() {
        return "Y".equals(moreData);
    }
}
