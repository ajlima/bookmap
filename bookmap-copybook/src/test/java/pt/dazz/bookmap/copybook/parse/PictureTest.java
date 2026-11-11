package pt.dazz.bookmap.copybook.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    @CsvSource(delimiter = '|', value = {
        "9.99|edited picture 9.99 is not supported by BookMap",
        "ZZ9|edited picture ZZ9 is not supported by BookMap",
        "-9(3)|edited picture -9(3) is not supported by BookMap",
        "9(5)CR|edited picture 9(5)CR is not supported by BookMap",
        "99/99|edited picture 99/99 is not supported by BookMap",
        "9(5)P|picture symbol P is not supported by BookMap",
        "A(3)|picture symbol A is not supported by BookMap",
        "N(3)|picture symbol N is not supported by BookMap",
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
        "9(5)DB|edited picture 9(5)DB is not supported by BookMap",
        "9X|picture 9X is not valid",
        "S(2)9|picture S(2)9 is not valid",
        "XV|picture XV is not valid",
        "9V(2)9|picture 9V(2)9 is not valid",
    })
    void rejectsUnsupportedPictures(String text, String message) {
        assertThatThrownBy(() -> Picture.parse(text))
            .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }
}
