package pt.dazz.bookmap.examples.billing;

import lombok.Getter;
import lombok.Setter;
import pt.dazz.bookmap.annotation.BookField;

/**
 * Fields that all billing requests share. The account number stays text: usage defaults apply only to numbers.
 */
@Getter
@Setter
public class BaseRequest {

    @BookField(order = 10, pic = "X(10)")
    private String accountId;
}
