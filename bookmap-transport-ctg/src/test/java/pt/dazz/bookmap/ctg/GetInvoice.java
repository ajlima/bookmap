package pt.dazz.bookmap.ctg;

import static pt.dazz.bookmap.annotation.Direction.INPUT;
import static pt.dazz.bookmap.annotation.Direction.OUTPUT;

import java.math.BigDecimal;
import pt.dazz.bookmap.annotation.BookField;
import pt.dazz.bookmap.annotation.BookOperation;

/** Reads the total of an invoice. The response layout ends with binary and packed fields. */
@BookOperation("GetInvoice")
public class GetInvoice {

    @BookField(order = 10, pic = "X(10)", direction = INPUT)
    private String invoiceId;

    @BookField(order = 20, pic = "S9(7)V99 COMP-3", direction = OUTPUT)
    private BigDecimal amount;

    @BookField(order = 30, pic = "9(4) COMP", direction = OUTPUT)
    private int lineCount;

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }
}
