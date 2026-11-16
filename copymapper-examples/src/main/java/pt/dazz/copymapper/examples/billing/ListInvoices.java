package pt.dazz.copymapper.examples.billing;

import static pt.dazz.copymapper.annotation.Direction.OUTPUT;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pt.dazz.copymapper.annotation.Occurs;
import pt.dazz.copymapper.annotation.Field;
import pt.dazz.copymapper.annotation.Operation;

/**
 * Lists the invoices of an account. The account number comes from {@link BaseRequest}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Operation("ListInvoices")
public class ListInvoices extends BaseRequest {

    @Field(order = 20, pic = "9(3)", direction = OUTPUT)
    private int invoiceCount;

    @Occurs(order = 30, maxOccurs = 10)
    private List<Invoice> invoices;
}
