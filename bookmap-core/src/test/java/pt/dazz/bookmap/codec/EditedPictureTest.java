package pt.dazz.bookmap.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EditedPictureTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "ZZ9|3|0|false|3",
        "Z,ZZ9.99CR|6|2|true|10",
        "$$$,$$9.99|7|2|false|10",
        "-9(5)|5|0|true|6",
        "***9.99|6|2|false|7",
        "99/99/99|6|0|false|8",
        "Z(3)9V99|6|2|false|6",
        "+,+++,999.99|8|2|true|12",
        "$B*,***,***.**BBDB|9|2|true|18",
        "+$Z9|2|0|true|4",
        "$ZZ9-|3|0|true|5",
        "9(3)DB|3|0|true|5",
        "++9|2|0|true|3",
        "--9|2|0|true|3",
        "B0/9|1|0|false|4",
        "ZZZ.ZZ|5|2|false|6",
        "$$$.$$|4|2|false|6",
        "9(5)|5|0|false|5",
        "zz9.99cr|5|2|true|8",
    })
    void givesTheAttributes(String text, int digits, int scale, boolean signed, int bytes) {
        EditedPicture picture = EditedPicture.parse(text, false);

        assertThat(picture.text()).isEqualTo(text.toUpperCase(java.util.Locale.ROOT));
        assertThat(picture.digits()).isEqualTo(digits);
        assertThat(picture.scale()).isEqualTo(scale);
        assertThat(picture.signed()).isEqualTo(signed);
        assertThat(picture.byteLength()).isEqualTo(bytes);
        assertThat(picture.blankWhenZero()).isFalse();
    }

    @Test
    void keepsBlankWhenZero() {
        EditedPicture picture = EditedPicture.parse("9(3)V99", true);

        assertThat(picture.blankWhenZero()).isTrue();
        assertThat(picture.digits()).isEqualTo(5);
        assertThat(picture.byteLength()).isEqualTo(5);
        assertThat(picture).hasToString("9(3)V99 BLANK WHEN ZERO");
        assertThat(EditedPicture.parse("ZZ9", false)).hasToString("ZZ9");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "ZZ9|true",
        "***|true",
        "9.99|true",
        "9,999|true",
        "B99|true",
        "990|true",
        "99/99|true",
        "+99|true",
        "99-|true",
        "$99|true",
        "9(5)CR|true",
        "9(5)DB|true",
        "zz9|true",
        "9(10)|false",
        "S9(7)V99|false",
        "X(10)|false",
        "9V9(2)|false",
        "X(3)B|true",
    })
    void findsAnEditSymbol(String text, boolean edited) {
        assertThat(EditedPicture.isEdited(text)).isEqualTo(edited);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "+|the edited picture has no digit position",
        "$+|the edited picture has no digit position",
        ".|the edited picture has no digit position",
        "9+9|a fixed '+' or '-' must be the first or the last symbol",
        "9-9|a fixed '+' or '-' must be the first or the last symbol",
        "9CR9|CR or DB must be the last symbol",
        "DB9|CR or DB must be the last symbol",
        "+9-|the picture has more than one sign",
        "-9CR|the picture has more than one sign",
        "++9-|the picture has more than one sign",
        "+9DB|the picture has more than one sign",
        "9$|a fixed '$' must be the first symbol, or the second after a sign",
        "9$9|a fixed '$' must be the first symbol, or the second after a sign",
        "Z$9|a fixed '$' must be the first symbol, or the second after a sign",
        "Z*9|Z, *, and a floating string are mutually exclusive",
        "$$Z9|Z, *, and a floating string are mutually exclusive",
        "++*9|Z, *, and a floating string are mutually exclusive",
        "Z++9|Z, *, and a floating string are mutually exclusive",
        "$$++9|the picture has more than one floating string",
        "++9++|the picture has more than one floating string",
        "$$9$$|the picture has more than one floating string",
        "$$9.$$|the picture has more than one floating string",
        "9Z|a '9' must not come before 'Z', '*', or a floating symbol",
        "9**|a '9' must not come before 'Z', '*', or a floating symbol",
        "Z9.ZZ|'Z' after the decimal point needs 'Z' in all digit positions",
        "**9.**|'*' after the decimal point needs '*' in all digit positions",
        "$$.$$9|'$' after the decimal point needs '$' in all digit positions",
        "9.9.9|the picture has more than one decimal point",
        "9V9.9|the picture has more than one decimal point",
        "9.9V9|the picture has more than one decimal point",
        "S9.99|S is not permitted in an edited picture. Use '+', '-', CR, or DB",
        "ZZXX|unexpected or unsupported character 'X' in the edited picture",
        "ZZ9P|unexpected or unsupported character 'P' in the edited picture",
        "ZZ9C|unexpected or unsupported character 'C' in the edited picture",
        "ZZ9D|unexpected or unsupported character 'D' in the edited picture",
        "ZZD9|unexpected or unsupported character 'D' in the edited picture",
        "9CB|unexpected or unsupported character 'C' in the edited picture",
        "Z(-1)9|the repeat count '-1' must be a number from 1 to 999999999",
        "ZZ9(|a '(' must have a ')' after it",
        "ZZ9()|the repeat count '' must be a number from 1 to 999999999",
        "Z(0)9|the repeat count '0' must be a number from 1 to 999999999",
        "Z(1A)9|the repeat count '1A' must be a number from 1 to 999999999",
        "Z(1234567890)9|the repeat count '1234567890' must be a number from 1 to 999999999",
        "(3)9|a repeat count must come after a symbol",
        "CR(2)9|a repeat count must come after a symbol",
        "Z(999)9(2)|the edited picture has more than 1000 characters",
        "9(1000).9|the edited picture has more than 1000 characters",
    })
    void rejectsAnIncorrectPicture(String text, String message) {
        assertThatThrownBy(() -> EditedPicture.parse(text, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(message);
    }

    @Test
    void acceptsTheMaximumLength() {
        assertThat(EditedPicture.parse("Z(999)9", false).byteLength()).isEqualTo(1000);
    }

    @Test
    void rejectsBlankWhenZeroWithCheckProtection() {
        assertThatThrownBy(() -> EditedPicture.parse("**9.99", true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("BLANK WHEN ZERO is not permitted with S or '*'");
    }

    @Test
    void comparesByTextAndBlankWhenZero() {
        EditedPicture picture = EditedPicture.parse("ZZ9", false);

        assertThat(picture).isEqualTo(EditedPicture.parse("zz9", false))
            .hasSameHashCodeAs(EditedPicture.parse("ZZ9", false))
            .isNotEqualTo(EditedPicture.parse("ZZ9", true))
            .isNotEqualTo(EditedPicture.parse("ZZZ9", false))
            .isNotEqualTo("ZZ9");
        assertThat(picture.hashCode()).isNotEqualTo(EditedPicture.parse("ZZ9", true).hashCode());
    }
}
