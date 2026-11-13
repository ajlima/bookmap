package pt.dazz.bookmap.codec;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Arrays;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.codec.internal.BinaryInteger;
import pt.dazz.bookmap.codec.internal.Blank;
import pt.dazz.bookmap.codec.internal.PackedDecimal;
import pt.dazz.bookmap.codec.internal.ZonedDecimal;

/**
 * Tells which slot of a fixed collection is empty, and gives the bytes of an unused slot.
 * A read and a write use the same instance. Thus, a write never makes an item that a read
 * finds empty, and the bytes of an unused slot always read as empty. Generated codecs keep
 * one instance in a constant. An instance is immutable.
 */
public final class EmptySlot {

    private static final EmptySlot LOW_VALUES = new EmptySlot(false, null);
    private static final EmptySlot SPACES = new EmptySlot(true, null);

    private final boolean spaces;
    private final Key key;

    /**
     * Describes the key field of a slot.
     *
     * @param offset the offset of the key in the slot
     * @param length the length in bytes of a text key, or the number of digits of a number key
     * @param scale the number of implied decimal places of a number key
     * @param signed true if the number key has a sign
     * @param usage the storage format of a number key, or null for a text key
     */
    private record Key(int offset, int length, int scale, boolean signed, Usage usage) {
    }

    private EmptySlot(boolean spaces, Key key) {
        this.spaces = spaces;
        this.key = key;
    }

    /**
     * Gives the marker for slots with only LOW-VALUES ({@code 0x00}).
     *
     * @return the marker
     */
    public static EmptySlot lowValues() {
        return LOW_VALUES;
    }

    /**
     * Gives the marker for slots with only spaces. A read also accepts LOW-VALUES in the slot.
     *
     * @return the marker
     */
    public static EmptySlot spaces() {
        return SPACES;
    }

    /**
     * Gives a marker with a text key. A slot is empty if its key has only spaces or
     * LOW-VALUES. The fill bytes of this marker stay the bytes of an unused slot.
     *
     * @param offset the offset of the key in the slot
     * @param length the length of the key in bytes
     * @return the new marker
     */
    public EmptySlot withTextKey(int offset, int length) {
        return new EmptySlot(spaces, new Key(offset, length, 0, false, null));
    }

    /**
     * Gives a marker with a number key. A slot is empty if its key is blank or zero. In an
     * unused slot, the key gets the value zero.
     *
     * @param offset the offset of the key in the slot
     * @param length the number of digits of the key
     * @param scale the number of implied decimal places
     * @param signed true if the key has a sign
     * @param usage the storage format
     * @return the new marker
     */
    public EmptySlot withNumberKey(int offset, int length, int scale, boolean signed,
                                   Usage usage) {
        return new EmptySlot(spaces, new Key(offset, length, scale, signed, usage));
    }

    /**
     * Tells if a slot is empty.
     *
     * @param field the descriptor of the slot, for error messages
     * @param data the buffer
     * @param from the index of the first byte of the slot
     * @param slotLength the number of bytes of the slot
     * @param charset the charset that defines the space
     * @return true if the slot is empty
     * @throws pt.dazz.bookmap.exception.MappingException if a number key has bytes that are not
     *     valid
     */
    public boolean isEmpty(String field, byte[] data, int from, int slotLength,
                           Charset charset) {
        if (key == null) {
            return spaces ? Blank.isBlank(data, from, slotLength, charset)
                : isLowValues(data, from, slotLength);
        }
        int start = from + key.offset();
        if (key.usage() == null) {
            return Blank.isBlank(data, start, key.length(), charset);
        }
        BigDecimal value = switch (key.usage()) {
            case DISPLAY -> ZonedDecimal.decode(field, data, start, key.length(), key.scale(),
                key.signed(), charset);
            case COMP3 -> PackedDecimal.decode(field, data, start, key.length(), key.scale(),
                key.signed(), charset);
            case COMP -> BinaryInteger.decode(data, start,
                BookWriter.byteLength(Usage.COMP, key.length()), key.scale(), key.signed());
        };
        return value == null || value.signum() == 0;
    }

    private static boolean isLowValues(byte[] data, int from, int length) {
        for (int i = from; i < from + length; i++) {
            if (data[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives the bytes of an unused slot.
     *
     * @param slotLength the number of bytes of the slot
     * @param charset the charset that defines the space
     * @return the bytes, a new array
     */
    public byte[] fill(int slotLength, Charset charset) {
        byte[] bytes = new byte[slotLength];
        if (spaces) {
            Arrays.fill(bytes, " ".getBytes(charset)[0]);
        }
        if (key != null && key.usage() != null) {
            byte[] zero = switch (key.usage()) {
                case DISPLAY -> ZonedDecimal.encode("key", BigDecimal.ZERO, key.length(),
                    key.scale(), key.signed(), charset);
                case COMP3 -> PackedDecimal.encode("key", BigDecimal.ZERO, key.length(),
                    key.scale(), key.signed());
                case COMP -> BinaryInteger.encode("key", BigDecimal.ZERO, key.length(),
                    key.scale(), key.signed(), BookWriter.byteLength(Usage.COMP, key.length()));
            };
            System.arraycopy(zero, 0, bytes, key.offset(), zero.length);
        }
        return bytes;
    }
}
