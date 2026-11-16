package pt.dazz.copymapper.examples.billing;

import lombok.Getter;
import lombok.Setter;
import pt.dazz.copymapper.annotation.Field;

/**
 * Fields that all billing requests share. The account number stays text: usage defaults apply only to numbers.
 */
@Getter
@Setter
public class BaseRequest {

    @Field(order = 10, pic = "X(10)")
    private String accountId;
}
