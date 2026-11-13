package pt.dazz.bookmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.annotation.EmptyItem;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.convert.FieldContext;
import pt.dazz.bookmap.convert.NoConverter;
import pt.dazz.bookmap.exception.BookmapException;
import pt.dazz.bookmap.exception.BusinessException;
import pt.dazz.bookmap.exception.ConnectionException;
import pt.dazz.bookmap.exception.ContinuationLimitException;
import pt.dazz.bookmap.exception.MappingException;
import pt.dazz.bookmap.exception.OperationNotFoundException;
import pt.dazz.bookmap.exception.RemoteAbendException;
import pt.dazz.bookmap.exception.TransportException;
import pt.dazz.bookmap.exception.TransportTimeoutException;
import pt.dazz.bookmap.spi.BookRequest;

class ApiTypesTest {
    @Test void bookRequestReplacesNullAttributesWithEmptyMap() {
        var r = new BookRequest("GetCustomerOrders", new byte[]{1}, 100, null);
        assertThat(r.attributes()).isEmpty();
    }
    @Test void bookRequestRejectsNullOperation() {
        assertThatNullPointerException().isThrownBy(() -> new BookRequest(null, new byte[0], 0));
    }
    @Test void bookRequestRejectsNullPayload() {
        assertThatNullPointerException().isThrownBy(() -> new BookRequest("Op", null, 0, Map.of()));
    }
    @Test void bookRequestWithoutAttributesHasEmptyMap() {
        var r = new BookRequest("Op", new byte[]{2}, 7);
        assertThat(r.attributes()).isEmpty();
        assertThat(r.payload()).containsExactly(2);
    }
    @Test void bookRequestKeepsGivenAttributes() {
        var r = new BookRequest("Op", new byte[0], 5, Map.of("k", "v"));
        assertThat(r.attributes()).containsEntry("k", "v");
        assertThat(r.operation()).isEqualTo("Op");
        assertThat(r.maxResponseLength()).isEqualTo(5);
    }
    @Test void bookRequestCopiesTheAttributes() {
        var source = new HashMap<String, String>();
        source.put("k", "v");
        var r = new BookRequest("Op", new byte[0], 5, source);
        source.put("k", "changed");
        assertThat(r.attributes()).containsExactly(Map.entry("k", "v"));
        assertThatThrownBy(() -> r.attributes().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void bookRequestRejectsNullAttributeValue() {
        var source = new HashMap<String, String>();
        source.put("k", null);
        assertThatNullPointerException().isThrownBy(() -> new BookRequest("Op", new byte[0], 5, source));
    }
    @Test void directionFlags() {
        assertThat(Direction.INPUT.isInput()).isTrue();      assertThat(Direction.INPUT.isOutput()).isFalse();
        assertThat(Direction.OUTPUT.isOutput()).isTrue();    assertThat(Direction.OUTPUT.isInput()).isFalse();
        assertThat(Direction.INPUT_OUTPUT.isInput()).isTrue(); assertThat(Direction.INPUT_OUTPUT.isOutput()).isTrue();
    }
    @Test void usageValues() {
        assertThat(Usage.values()).containsExactly(Usage.DISPLAY, Usage.COMP3, Usage.COMP);
        assertThat(Usage.valueOf("COMP3")).isEqualTo(Usage.COMP3);
        assertThat(Direction.valueOf("INPUT")).isEqualTo(Direction.INPUT);
        assertThat(EmptyItem.values()).containsExactly(EmptyItem.LOW_VALUES, EmptyItem.SPACES);
    }
    @Test void exceptionHierarchy() {
        assertThat(new RemoteAbendException("ASRA", "abend")).isInstanceOf(TransportException.class)
            .isInstanceOf(BookmapException.class).isInstanceOf(RuntimeException.class);
        assertThat(new RemoteAbendException("ASRA", "abend").abendCode()).isEqualTo("ASRA");
        assertThat(new BusinessException("x")).isInstanceOf(BookmapException.class);
        assertThat(new TransportTimeoutException("m")).isInstanceOf(TransportException.class);
        assertThat(new ConnectionException("m")).isInstanceOf(TransportException.class);
        assertThat(new OperationNotFoundException("m")).isInstanceOf(TransportException.class);
    }
    @Test void exceptionConstructorsKeepMessageAndCause() {
        var cause = new IllegalStateException("c");
        BookmapException[] all = {
            new BookmapException("m"), new BookmapException("m", cause),
            new MappingException("m"), new MappingException("m", cause),
            new ContinuationLimitException("m"), new ContinuationLimitException("m", cause),
            new BusinessException("m"), new BusinessException("m", cause),
            new TransportException("m"), new TransportException("m", cause),
            new TransportTimeoutException("m"), new TransportTimeoutException("m", cause),
            new ConnectionException("m"), new ConnectionException("m", cause),
            new OperationNotFoundException("m"), new OperationNotFoundException("m", cause),
            new RemoteAbendException("ASRA", "m"), new RemoteAbendException("ASRA", "m", cause)
        };
        for (int i = 0; i < all.length; i++) {
            assertThat(all[i]).hasMessage("m");
            assertThat(all[i].getCause()).isEqualTo(i % 2 == 0 ? null : cause);
        }
        assertThat(new RemoteAbendException("ASRA", "m", cause).abendCode()).isEqualTo("ASRA");
    }
    @Test void defaultCodecContextUsesIbm037() {
        assertThat(CodecContext.defaults().charset()).isEqualTo(Charset.forName("IBM037"));
    }
    @Test void codecContextRejectsMultiByteCharset() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CodecContext(StandardCharsets.UTF_8));
    }
    @Test void codecContextRejectsCharsetThatCannotEncode() {
        Charset decodeOnly = new Charset("X-DECODE-ONLY", null) {
            @Override public boolean contains(Charset cs) { return false; }
            @Override public java.nio.charset.CharsetDecoder newDecoder() {
                return StandardCharsets.ISO_8859_1.newDecoder();
            }
            @Override public java.nio.charset.CharsetEncoder newEncoder() {
                throw new UnsupportedOperationException();
            }
            @Override public boolean canEncode() { return false; }
        };
        assertThatIllegalArgumentException().isThrownBy(() -> new CodecContext(decodeOnly))
            .withMessage("The charset must encode and decode: X-DECODE-ONLY");
    }
    @Test void codecContextRejectsNullCharset() {
        assertThatNullPointerException().isThrownBy(() -> new CodecContext(null));
    }
    @Test void codecContextAcceptsSingleByteCharset() {
        assertThat(new CodecContext(StandardCharsets.ISO_8859_1).charset()).isEqualTo(StandardCharsets.ISO_8859_1);
    }
    @Test void noConverterThrowsOnBothMethods() {
        var converter = new NoConverter();
        assertThatThrownBy(() -> converter.decode(new byte[0], null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> converter.encode(null, null)).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void fieldContextKeepsItsValues() {
        var c = new FieldContext("amount", 9, 2, true, Usage.COMP3, "yyyyMMdd", 5, StandardCharsets.ISO_8859_1);
        assertThat(c.field()).isEqualTo("amount");
        assertThat(c.length()).isEqualTo(9);
        assertThat(c.scale()).isEqualTo(2);
        assertThat(c.signed()).isTrue();
        assertThat(c.usage()).isEqualTo(Usage.COMP3);
        assertThat(c.pattern()).isEqualTo("yyyyMMdd");
        assertThat(c.byteLength()).isEqualTo(5);
        assertThat(c.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
    }
}
