package pt.dazz.bookmap.simulator;

import java.nio.charset.Charset;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.spi.BookCodec;

/** Hand-written codec for {@link Greeting}: the payloads are plain text in the context charset. */
final class GreetingCodec implements BookCodec<Greeting> {

    private final String charsetName;

    GreetingCodec(String charsetName) {
        this.charsetName = charsetName;
    }

    @Override public Class<Greeting> type() { return Greeting.class; }

    @Override public String operationName() { return "Greet"; }

    @Override public String charsetName() { return charsetName; }

    @Override public int maxResponseLength() { return 64; }

    @Override public Greeting newInstance() { return new Greeting(); }

    @Override public byte[] encodeRequest(Greeting value, CodecContext ctx) {
        return value.name.getBytes(ctx.charset());
    }

    @Override public void decodeResponse(byte[] data, Greeting target, CodecContext ctx) {
        target.message = new String(data, ctx.charset());
    }

    @Override public Greeting decodeRequest(byte[] data, CodecContext ctx) {
        Greeting greeting = new Greeting();
        greeting.name = new String(data, ctx.charset());
        return greeting;
    }

    @Override public byte[] encodeResponse(Greeting value, CodecContext ctx) {
        return value.message.getBytes(ctx.charset());
    }

    @Override public void resetOutputCollections(Greeting value) { }

    static Charset charset(String name) { return Charset.forName(name); }
}
