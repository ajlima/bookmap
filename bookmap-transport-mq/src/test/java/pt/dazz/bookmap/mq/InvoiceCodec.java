package pt.dazz.bookmap.mq;

import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.spi.BookCodec;

/** Hand-written codec for {@link Invoice}: the payloads are text in the context charset. */
class InvoiceCodec implements BookCodec<Invoice> {

    @Override public Class<Invoice> type() { return Invoice.class; }

    @Override public String operationName() { return "GetInvoice"; }

    @Override public String charsetName() { return ""; }

    @Override public int maxResponseLength() { return 16; }

    @Override public Invoice newInstance() { return new Invoice(); }

    @Override public byte[] encodeRequest(Invoice value, CodecContext ctx) {
        return value.number.getBytes(ctx.charset());
    }

    @Override public void decodeResponse(byte[] data, Invoice target, CodecContext ctx) {
        target.status = new String(data, ctx.charset());
    }

    @Override public Invoice decodeRequest(byte[] data, CodecContext ctx) {
        Invoice invoice = new Invoice();
        invoice.number = new String(data, ctx.charset());
        return invoice;
    }

    @Override public byte[] encodeResponse(Invoice value, CodecContext ctx) {
        return value.status.getBytes(ctx.charset());
    }

    @Override public void resetOutputCollections(Invoice value) {
        // No collections.
    }
}
