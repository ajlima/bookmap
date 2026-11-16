package pt.dazz.copymapper.examples;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;
import pt.dazz.copymapper.annotation.Field;
import pt.dazz.copymapper.annotation.Operation;

/**
 * Reads a customer profile. The class uses Lombok {@code @Data} and has a field of each
 * supported type.
 */
@Data
@Operation("GetCustomerProfile")
public class CustomerProfile {

    @Field(order = 10, pic = "X(10)")
    private String customerId;

    @Field(order = 20, pic = "X(30)")
    private String name;

    @Field(order = 30, pic = "9(3)")
    private int age;

    @Field(order = 40, pic = "9(3)")
    private Integer visits;

    @Field(order = 50, pic = "9(12)")
    private long loyaltyPoints;

    @Field(order = 60, pic = "9(12)")
    private Long lifetimeSpend;

    @Field(order = 70, pic = "S9(9)V99 COMP-3")
    private BigDecimal creditLimit;

    @Field(order = 80, pic = "S9(5)V99")
    private BigDecimal balance;

    @Field(order = 90)
    private LocalDate birthDate;

    @Field(order = 100, pattern = "HHmmss")
    private LocalTime preferredContact;

    @Field(order = 110, pattern = "yyyyMMddHHmmss")
    private LocalDateTime lastLogin;
}
