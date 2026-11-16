package pt.dazz.copymapper.examples.orders;

import static pt.dazz.copymapper.annotation.Direction.INPUT;
import static pt.dazz.copymapper.annotation.Direction.OUTPUT;

import java.util.List;
import lombok.Data;
import pt.dazz.copymapper.annotation.Occurs;
import pt.dazz.copymapper.annotation.Field;
import pt.dazz.copymapper.annotation.Operation;

/**
 * Lists the open orders of a customer. The program sends only the used entries of the fixed
 * table: the response ends after the last open order. The collection has
 * {@code partial = true}, and it is the last member of the response.
 *
 * <pre>
 * 01  LIST-OPEN-ORDERS.
 *     05  CUSTOMER-ID         PIC X(10).
 *     05  RETURN-CODE         PIC X(4).
 *     05  ORDER-COUNT         PIC 9(2).
 *     05  OPEN-ORDER OCCURS 10.
 *         10  ORDER-ID        PIC X(12).
 *         10  ORDER-STATUS    PIC X(2).
 * </pre>
 */
@Data
@Operation
public class ListOpenOrders {

    @Field(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @Field(order = 20, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    @Field(order = 30, pic = "9(2)")
    private int orderCount;

    @Occurs(order = 40, maxOccurs = 10, fixed = true, dependsOn = "orderCount",
        partial = true)
    private List<OpenOrder> orders;
}
