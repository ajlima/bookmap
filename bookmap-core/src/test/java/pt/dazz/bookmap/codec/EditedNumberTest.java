package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.convert.CodecContext;
import pt.dazz.bookmap.exception.MappingException;

/** Tests the write and the read of numeric edited pictures. */
class EditedNumberTest {

    static final CodecContext ASCII = new CodecContext(StandardCharsets.US_ASCII);
    static final CodecContext EBCDIC = CodecContext.defaults();
    static final String F = "Invoice.total (order 40)";

    private static String write(String picture, String value) {
        return write(picture, false, value);
    }

    private static String write(String picture, boolean blankWhenZero, String value) {
        var writer = new BookWriter(ASCII);
        writer.writeEdited(F, value == null ? null : new BigDecimal(value),
            EditedPicture.parse(picture, blankWhenZero));
        return new String(writer.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static BigDecimal read(String picture, String text) {
        return read(picture, text, ASCII);
    }

    private static BigDecimal read(String picture, String text, CodecContext ctx) {
        Charset cs = ctx.charset();
        byte[] data = (text + "#").getBytes(cs);
        var reader = new BookReader(data, ctx);
        BigDecimal value = reader.readEdited(F, EditedPicture.parse(picture, false));
        assertThat(reader.position()).isEqualTo(data.length - 1);
        return value;
    }

    /** The examples of the IBM Language Reference, and other examples. '_' is a space. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "ZZZZ.99|0|____.00",
        "ZZZZ.ZZ|0|_______",
        "****.**|0|****.**",
        "****.99|0|****.00",
        "ZZ99.99|0|__00.00",
        "Z,ZZZ.ZZ+|123.45|__123.45+",
        "*,***.**+|-123.45|**123.45-",
        "**,***,***.**+|12345678.9|12,345,678.90+",
        "$Z,ZZZ,ZZZ.ZZCR|12345.67|$___12,345.67__",
        "$B*,***,***.**BBDB|-12345.67|$_***12,345.67__DB",
        "$$$$.99|0.12|___$.12",
        "$$$9.99|0.12|__$0.12",
        "$,$$$,999.99|1234.56|___$1,234.56",
        "+,+++,999.99|-123456.78|_-123,456.78",
        "$$,$$$,$$$.99CR|-1234567|$1,234,567.00CR",
        "++,+++,+++.+++|0|______________",
        "$$$$.99|0|___$.00",
        "-9(5)|42|_00042",
        "-9(5)|-42|-00042",
        "+9(3)|42|+042",
        "+9(3)|0|+000",
        "9(3)-|-42|042-",
        "9(3)+|42|042+",
        "9(3)CR|42|042__",
        "9(3)DB|-42|042DB",
        "ZZ9|7|__7",
        "ZZ9|0|__0",
        "ZZZ|0|___",
        "ZZZ|105|105",
        "***|0|***",
        "ZZ,ZZ9|1234|_1,234",
        "ZZ,ZZ9|123|___123",
        "**,**9|123|***123",
        "ZZZ.ZZ|0.05|___.05",
        "ZZZ.ZZ|1.5|__1.50",
        "99/99/99|311226|31/12/26",
        "999B999|123456|123_456",
        "9990|12|0120",
        "---9|-5|__-5",
        "---9|5|___5",
        "+++9|5|__+5",
        "+++9|-123|-123",
        "+++,+++|1234|_+1,234",
        "+++,+++|12|____+12",
        "$$$|0|___",
        "$$$|12|$12",
        "+$ZZ9|-12|-$_12",
        "$ZZ9-|-12|$_12-",
        "ZZ9V99|1.5|__150",
        "ZZ9||__0",
        "ZZZ.ZZCR|0|________",
        "***.**CR|0|***.****",
        "+ZZZ|0|____",
        "$ZZZ|0|____",
    })
    void writesTheEditedText(String picture, String value, String expected) {
        String text = write(picture, value);

        assertThat(text.replace(' ', '_')).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ZZ9.99|0",
        "9(5)|0",
        "9(3)V99|0.00",
        "$$$9.99CR|0",
        "ZZ9|",
    })
    void blankWhenZeroWritesSpaces(String picture, String value) {
        String text = write(picture, true, value);

        assertThat(text).isBlank().hasSize(EditedPicture.parse(picture, true).byteLength());
    }

    @Test
    void blankWhenZeroWritesANonzeroValue() {
        assertThat(write("9(5)", true, "42")).isEqualTo("00042");
        assertThat(write("ZZ9.99", true, "0.01")).isEqualTo("  0.01");
    }

    @Test
    void writesInEbcdic() {
        var writer = new BookWriter(EBCDIC);
        writer.writeEdited(F, new BigDecimal("-1234.5"),
            EditedPicture.parse("$Z,ZZ9.99CR", false));

        assertThat(writer.toByteArray()).containsExactly(0x5B, 0xF1, 0x6B, 0xF2, 0xF3, 0xF4,
            0x4B, 0xF5, 0xF0, 0xC3, 0xD9);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ZZ9.99|1.234|Invoice.total (order 40): 1.234 has more than 2 decimal places",
        "ZZ9|1000|Invoice.total (order 40): 1000 has more than 3 digits",
        "$$$9|1000|Invoice.total (order 40): 1000 has more than 3 digits",
        "ZZ9|-1|Invoice.total (order 40): negative value -1 in an unsigned field",
    })
    void rejectsAValueThatDoesNotFit(String picture, String value, String message) {
        assertThatThrownBy(() -> write(picture, value))
            .isInstanceOf(MappingException.class)
            .hasMessage(message);
    }

    /** Values that the write accepts. The read gives them back. '_' is a space. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "ZZZZ.99|____.00|0.00",
        "****.**|****.**|0.00",
        "****.99|****.00|0.00",
        "ZZ99.99|__00.00|0.00",
        "Z,ZZZ.ZZ+|__123.45+|123.45",
        "*,***.**+|**123.45-|-123.45",
        "**,***,***.**+|12,345,678.90+|12345678.90",
        "$Z,ZZZ,ZZZ.ZZCR|$___12,345.67__|12345.67",
        "$B*,***,***.**BBDB|$_***12,345.67__DB|-12345.67",
        "$$$$.99|___$.12|0.12",
        "$,$$$,999.99|___$1,234.56|1234.56",
        "+,+++,999.99|_-123,456.78|-123456.78",
        "$$,$$$,$$$.99CR|$1,234,567.00CR|-1234567.00",
        "-9(5)|_00042|42",
        "-9(5)|-00042|-42",
        "+9(3)|+042|42",
        "+9(3)|-042|-42",
        "9(3)-|042-|-42",
        "9(3)+|042+|42",
        "9(3)CR|042__|42",
        "9(3)DB|042DB|-42",
        "ZZ9|__7|7",
        "***|***|0",
        "ZZ,ZZ9|___123|123",
        "**,**9|***123|123",
        "ZZZ.ZZ|___.05|0.05",
        "99/99/99|31/12/26|311226",
        "999B999|123_456|123456",
        "9990|0120|12",
        "---9|__-5|-5",
        "+++9|__+5|5",
        "+++9|-123|-123",
        "+++,+++|____+12|12",
        "$$$|$12|12",
        "+$ZZ9|-$_12|-12",
        "$ZZ9-|$_12-|-12",
        "ZZ9V99|__150|1.50",
        "***.**CR|***.****|0.00",
        "ZZZ9|0_12|12",
        "$$$9|$$$1|1",
        "ZZZZ|___0|0",
        "ZZ9|*12|12",
        "+***|****|0",
        "-***|****|0",
        "$***|****|0",
        "$ZZ9|___1|1",
        "Z.99|1_99|1.99",
        "**9|__1|1",
    })
    void readsTheEditedText(String picture, String text, String expected) {
        assertThat(read(picture, text.replace('_', ' '))).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void readsABlankFieldAsNull() {
        assertThat(read("ZZZ.ZZ", "      ")).isNull();
        assertThat(read("$$$9.99CR", "\0\0\0\0\0\0\0\0\0")).isNull();
    }

    @Test
    void readsInEbcdic() {
        byte[] data = {0x5B, (byte) 0xF1, 0x6B, (byte) 0xF2, (byte) 0xF3, (byte) 0xF4, 0x4B,
            (byte) 0xF5, (byte) 0xF0, (byte) 0xC3, (byte) 0xD9};
        var reader = new BookReader(data, EBCDIC);

        assertThat(reader.readEdited(F, EditedPicture.parse("$Z,ZZ9.99CR", false)))
            .isEqualTo(new BigDecimal("-1234.50"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "ZZ9|1A3|0x41|1",
        "ZZ9|12_|0x20|2",
        "999|_12|0x20|0",
        "9.99|1,23|0x2C|1",
        "9,999|1.234|0x2E|1",
        "+999|_123|0x20|0",
        "+999|*123|0x2A|0",
        "-999|+123|0x2B|0",
        "999CR|123CX|0x58|4",
        "999CR|123_R|0x52|4",
        "999CR|123DB|0x44|3",
        "999DB|123CR|0x43|3",
        "$999|#123|0x23|0",
        "$$$9|+$$1|0x2B|0",
        "---9|__+5|0x2B|2",
        "+++9|__$5|0x24|2",
        "Z9|-1|0x2D|0",
        "**9|$_1|0x24|0",
        "ZZ9|__-|0x2D|2",
        "99/99|12-34|0x2D|2",
        "-999|*123|0x2A|0",
        "999CR|123**|0x2A|3",
        "999CR|123_*|0x2A|4",
    })
    void rejectsAByteThatIsNotValid(String picture, String text, String hex, int offset) {
        assertThatThrownBy(() -> read(picture, text.replace('_', ' ')))
            .isInstanceOf(MappingException.class)
            .hasMessage(F + ": byte " + hex + " at offset " + offset
                + " of the field is not valid in the edited picture " + picture);
    }

    @Test
    void rejectsACharacterThatTheCharsetCannotRead() {
        byte[] data = {(byte) 0x80, 0x31};
        var reader = new BookReader(data, ASCII);

        assertThatThrownBy(() -> reader.readEdited(F, EditedPicture.parse("Z9", false)))
            .isInstanceOf(MappingException.class);
    }

    @Test
    void rejectsATooShortResponse() {
        var reader = new BookReader("12".getBytes(StandardCharsets.US_ASCII), ASCII);

        assertThatThrownBy(() -> reader.readEdited(F, EditedPicture.parse("ZZ9", false)))
            .isInstanceOf(MappingException.class);
    }

    /** Each value that the write accepts reads back with the scale of the picture. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "$$,$$9.99CR", "+ZZ,ZZ9.99", "**,**9.99-", "---,--9", "$B*,***.**BBDB", "ZZZ.ZZ",
        "+++.++", "9(3)/99B0",
    })
    void readsBackEachWrittenValue(String picture) {
        EditedPicture edited = EditedPicture.parse(picture, false);
        for (String value : new String[] {"1", "12.5", "999", "0.01", "0.1", "100", "7"}) {
            for (String sign : edited.signed() ? new String[] {"", "-"} : new String[] {""}) {
                if (new BigDecimal(value).scale() > edited.scale()) {
                    continue;
                }
                BigDecimal number = new BigDecimal(sign + value).setScale(edited.scale(),
                    java.math.RoundingMode.UNNECESSARY);
                if (number.precision() - number.scale() > edited.digits() - edited.scale()) {
                    continue;
                }
                String text = write(picture, number.toPlainString());
                assertThat(read(picture, text)).as(picture + " " + number + " '" + text + "'")
                    .isEqualTo(number);
            }
        }
    }
}
