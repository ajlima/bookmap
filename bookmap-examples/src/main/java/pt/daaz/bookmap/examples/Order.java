package pt.daaz.bookmap.examples;

import java.math.BigDecimal;
import java.time.LocalDate;
import pt.daaz.bookmap.annotation.BookField;

/**
 * One order in the answer of {@link GetCustomerOrders}. Convention style.
 */
public class Order {

    @BookField(order = 10, pic = "X(10)")
    private String orderNumber;

    @BookField(order = 20, pic = "S9(13)V99 COMP-3")
    private BigDecimal totalAmount;

    @BookField(order = 30)
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
