package pt.dazz.bookmap.copybook.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.dazz.bookmap.copybook.ast.Usage;

class PictureTest {

    @ParameterizedTest
    @CsvSource({
        "X(10), false, false, 10, 0, 10",
        "XXX, false, false, 3, 0, 3",
        "x(2)x, false, false, 3, 0, 3",
        "9(5), true, false, 5, 0, 5",
        "S9(13)V99, true, true, 13, 2, 15",
        "S9(7)V9(2), true, true, 7, 2, 9",
        "V99, true, false, 0, 2, 2",
        "99V, true, false, 2, 0, 2",
    })
    void parsesSupportedPictures(String text, boolean numeric, boolean signed, int integerDigits,
                                 int scale, int length) {
        Picture picture = Picture.parse(text);
        assertThat(picture.text()).isEqualTo(text.toUpperCase(java.util.Locale.ROOT));
        assertThat(picture.numeric()).isEqualTo(numeric);
        assertThat(picture.signed()).isEqualTo(signed);
        assertThat(picture.integerDigits()).isEqualTo(integerDigits);
        assertThat(picture.scale()).isEqualTo(scale);
        assertThat(picture.length()).isEqualTo(length);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "Z*9|edited picture Z*9: Z, *, and a floating string are mutually exclusive",
        "S9.99|edited picture S9.99: S is not permitted in an edited picture. Use '+', '-', CR, or DB",
        "9CR9|edited picture 9CR9: CR or DB must be the last symbol",
        "9(5)P|picture symbol P is not supported by BookMap",
        "A(3)|picture symbol A is not supported by BookMap",
        "N(3)|picture symbol N is not supported by BookMap. See issue #49.",
        "X9|picture X9 is not valid",
        "9S|picture 9S is not valid",
        "9V9V9|picture 9V9V9 is not valid",
        "S|picture S is not valid",
        "X(|picture X( is not valid",
        "X(0)|picture X(0) is not valid",
        "X(A)|picture X(A) is not valid",
        "SX|picture SX is not valid",
        "VX|picture VX is not valid",
        "Q|picture Q is not valid",
        "9X|picture 9X is not valid",
        "S(2)9|picture S(2)9 is not valid",
        "XV|picture XV is not valid",
        "9V(2)9|picture 9V(2)9 is not valid",
    })
    void rejectsUnsupportedPictures(String text, String message) {
        assertThatThrownBy(() -> Picture.parse(text))
            .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "zz9.99cr|ZZ9.99CR|true|3|2|8",
        "$$,$$9|$$,$$9|false|4|0|6",
        "-9(3)|-9(3)|true|3|0|4",
    })
    void parsesEditedPictures(String text, String upper, boolean signed, int integerDigits,
                              int scale, int bytes) {
        Picture picture = Picture.parse(text);

        assertThat(picture.text()).isEqualTo(upper);
        assertThat(picture.numeric()).isTrue();
        assertThat(picture.signed()).isEqualTo(signed);
        assertThat(picture.integerDigits()).isEqualTo(integerDigits);
        assertThat(picture.scale()).isEqualTo(scale);
        assertThat(picture.edited()).hasToString(upper);
        assertThat(picture.byteLength(Usage.DISPLAY, null)).isEqualTo(bytes);
        assertThat(picture.byteLength(Usage.COMP3, null)).isEqualTo(bytes);
    }

    @Test
    void blankWhenZeroMakesAnEditedPicture() {
        Picture picture = Picture.parse("9(3)V99").withBlankWhenZero();

        assertThat(picture.edited()).hasToString("9(3)V99 BLANK WHEN ZERO");
        assertThat(picture.length()).isEqualTo(5);
        assertThat(picture.scale()).isEqualTo(2);
        assertThat(picture.byteLength(Usage.DISPLAY, null)).isEqualTo(5);
        assertThat(Picture.parse("ZZ9").withBlankWhenZero().edited().blankWhenZero()).isTrue();
        assertThat(Picture.parse("9(3)").edited()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "X(3)|BLANK WHEN ZERO needs a numeric picture",
        "S9(3)|edited picture S9(3): BLANK WHEN ZERO is not permitted with S or '*'",
        "**9|edited picture **9: BLANK WHEN ZERO is not permitted with S or '*'",
    })
    void rejectsBlankWhenZero(String text, String message) {
        assertThatThrownBy(() -> Picture.parse(text).withBlankWhenZero())
            .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }
}
