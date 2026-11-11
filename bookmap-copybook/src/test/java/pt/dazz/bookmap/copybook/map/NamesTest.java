package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NamesTest {

    private final Names names = new Names(List.of("ws-", "LK-"));

    @ParameterizedTest
    @CsvSource({
        "GET-CUSTOMER-ORDERS, GetCustomerOrders, getCustomerOrders",
        "ORDER-AMOUNT, OrderAmount, orderAmount",
        "ORDER_AMOUNT, OrderAmount, orderAmount",
        "order--amount, OrderAmount, orderAmount",
        "WS-ORDER-ID, OrderId, orderId",
        "lk-total, Total, total",
        "WS-, Ws, ws",
        "CLASS, ClassRecord, classValue",
        "TRUE, True, trueValue",
        "1ST-ADDRESS, Record1stAddress, field1stAddress",
        "STRING, StringRecord, string",
        "X, X, x",
    })
    void convertsNames(String cobol, String className, String fieldName) {
        assertThat(names.className(cobol)).isEqualTo(className);
        assertThat(names.fieldName(cobol)).isEqualTo(fieldName);
    }

    @Test
    void withoutPrefixesKeepsTheFullName() {
        assertThat(new Names(List.of()).fieldName("WS-ORDER-ID")).isEqualTo("wsOrderId");
    }
}
