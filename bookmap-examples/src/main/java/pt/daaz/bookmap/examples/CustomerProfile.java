package pt.daaz.bookmap.examples;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;
import pt.daaz.bookmap.annotation.BookField;
import pt.daaz.bookmap.annotation.BookOperation;

/**
 * Reads a customer profile. Lombok {@code @Data}, fields of all supported types.
 */
@Data
@BookOperation("GetCustomerProfile")
public class CustomerProfile {

    @BookField(order = 10, pic = "X(10)")
    private String customerId;

    @BookField(order = 20, pic = "X(30)")
    private String name;

    @BookField(order = 30, pic = "9(3)")
    private int age;

    @BookField(order = 40, pic = "9(3)")
    private Integer visits;

    @BookField(order = 50, pic = "9(12)")
    private long loyaltyPoints;

    @BookField(order = 60, pic = "9(12)")
    private Long lifetimeSpend;

    @BookField(order = 70, pic = "S9(9)V99 COMP-3")
    private BigDecimal creditLimit;

    @BookField(order = 80, pic = "S9(5)V99")
    private BigDecimal balance;

    @BookField(order = 90)
    private LocalDate birthDate;

    @BookField(order = 100, pattern = "HHmmss", length = 6)
    private LocalTime preferredContact;

    @BookField(order = 110, pattern = "yyyyMMddHHmmss", length = 14)
    private LocalDateTime lastLogin;
}
