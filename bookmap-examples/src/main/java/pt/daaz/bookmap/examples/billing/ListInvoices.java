package pt.daaz.bookmap.examples.billing;

import static pt.daaz.bookmap.annotation.Direction.OUTPUT;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pt.daaz.bookmap.annotation.BookCollection;
import pt.daaz.bookmap.annotation.BookField;
import pt.daaz.bookmap.annotation.BookOperation;

/**
 * Lists the invoices of an account. The account number comes from {@link BaseRequest}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@BookOperation("ListInvoices")
public class ListInvoices extends BaseRequest {

    @BookField(order = 20, pic = "9(3)", direction = OUTPUT)
    private int invoiceCount;

    @BookCollection(order = 30, maxOccurs = 10)
    private List<Invoice> invoices;
}
