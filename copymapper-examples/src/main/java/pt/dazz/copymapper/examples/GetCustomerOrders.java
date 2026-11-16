package pt.dazz.copymapper.examples;

import static pt.dazz.copymapper.annotation.Direction.INPUT;
import static pt.dazz.copymapper.annotation.Direction.OUTPUT;

import java.util.List;
import pt.dazz.copymapper.Continuable;
import pt.dazz.copymapper.Validatable;
import pt.dazz.copymapper.annotation.Occurs;
import pt.dazz.copymapper.annotation.Field;
import pt.dazz.copymapper.annotation.Operation;

/**
 * Lists the orders of a customer. The class uses the convention style and handwritten
 * accessors.
 */
@Operation
public class GetCustomerOrders implements Continuable, Validatable {

    @Field(order = 10, pic = "X(10)", direction = INPUT)
    private String customerId;

    // INPUT_OUTPUT: the next request sends back the key of the last response.
    @Field(order = 20, pic = "X(12)")
    private String restartKey;

    @Field(order = 30, pic = "X(4)", direction = OUTPUT)
    private String returnCode;

    @Field(order = 40, pic = "X(60)", direction = OUTPUT)
    private String returnMessage;

    // 'Y' or 'N'.
    @Field(order = 50, pic = "X", direction = OUTPUT)
    private String moreData;

    // OUTPUT, as the collection: the number of orders in the response.
    @Field(order = 60, pic = "9(3)")
    private int orderCount;

    // OUTPUT. The count field is the previous member, orderCount.
    @Occurs(order = 70, maxOccurs = 50)
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
