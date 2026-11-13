package pt.dazz.bookmap.examples.orders;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.util.List;
import lombok.Data;
import pt.dazz.bookmap.annotation.BookCollection;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;
import pt.dazz.bookmap.annotation.EmptyItem;

/**
 * Lists the open backorders of a customer. The book has a fixed table without a count field.
 * The program runs {@code INITIALIZE} on the table, so an unused entry has spaces and an
 * order number of zero. The collection names the key with {@code emptyKey}.
 *
 * <pre>
 * 01  LIST-BACKORDERS.
 *     05  CUSTOMER-ID         PIC X(10).
 *     05  BACKORDER OCCURS 4.
 *         10  ORDER-NUMBER    PIC 9(8).
 *         10  CURRENCY        PIC X(3).
 *         10  AMOUNT          PIC S9(9)V99 COMP-3.
 *     05  RETURN-CODE         PIC X(4).
 * </pre>
 */
@Data
@BookOperation
public class ListBackorders {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @BookCollection(order = 20, maxOccurs = 4, fixed = true, emptyKey = "orderNumber",
        emptyItem = EmptyItem.SPACES)
    private List<Backorder> backorders;

    @BookField(order = 30, pic = "X(4)", direction = OUTPUT)
    private String returnCode;
}
