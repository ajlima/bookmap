package pt.dazz.bookmap.examples;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.util.List;
import pt.dazz.bookmap.Continuable;
import pt.dazz.bookmap.Validatable;
import pt.dazz.bookmap.annotation.BookCollection;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;

/**
 * Lists the orders of a customer. Convention style, handwritten accessors.
 */
@BookOperation
public class GetCustomerOrders implements Continuable, Validatable {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    @BookField(order = 20, pic = "X(12)")
    private String restartKey;

    @BookField(order = 30, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    @BookField(order = 40, pic = "X(60)", direction = OUTPUT)
    private String returnMessage;

    @BookField(order = 50, pic = "X", direction = OUTPUT)
    private String moreData;

    @BookField(order = 60, pic = "9(3)")
    private int orderCount;

    @BookCollection(order = 70, maxOccurs = 50)
    private List<Order> orders;

    @Override
    public void validate() {
        if (!"0000".equals(returnCode)) {
            throw new OrderQueryException(returnCode, returnMessage);
        }
    }

    @Override
    public boolean hasContinuation() {
        return "Y".equals(moreData);
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getRestartKey() {
        return restartKey;
    }

    public void setRestartKey(String restartKey) {
        this.restartKey = restartKey;
    }

    public String getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(String returnCode) {
        this.returnCode = returnCode;
    }

    public String getReturnMessage() {
        return returnMessage;
    }

    public void setReturnMessage(String returnMessage) {
        this.returnMessage = returnMessage;
    }

    public String getMoreData() {
        return moreData;
    }

    public void setMoreData(String moreData) {
        this.moreData = moreData;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(int orderCount) {
        this.orderCount = orderCount;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }
}
