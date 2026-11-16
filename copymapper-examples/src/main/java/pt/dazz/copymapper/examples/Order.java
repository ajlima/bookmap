package pt.dazz.copymapper.examples;

import java.math.BigDecimal;
import java.time.LocalDate;
import pt.dazz.copymapper.annotation.Field;

/**
 * One order in the answer of {@link GetCustomerOrders}. The class uses the convention style.
 */
public class Order {

    @Field(order = 10, pic = "X(10)")
    private String orderNumber;

    @Field(order = 20, pic = "S9(13)V99 COMP-3")
    private BigDecimal totalAmount;

    // The length is 8, from the default pattern yyyyMMdd.
    @Field(order = 30)
    private LocalDate orderDate;

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }
}
