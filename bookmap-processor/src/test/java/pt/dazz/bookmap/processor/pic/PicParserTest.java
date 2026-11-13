package pt.dazz.bookmap.processor.pic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.dazz.bookmap.annotation.Sign;
import pt.dazz.bookmap.annotation.Usage;
import pt.dazz.bookmap.processor.pic.PicSpec.Category;

class PicParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // pic                        | category | length | scale | signed | usage
        "X(10)                        | TEXT     | 10 | 0 | false | ",
        "XXX                          | TEXT     | 3  | 0 | false | ",
        "pic x(4).                    | TEXT     | 4  | 0 | false | ",
        "9(5)                         | NUMBER   | 5  | 0 | false | ",
        "999                          | NUMBER   | 3  | 0 | false | ",
        "S9(13)V99                    | NUMBER   | 15 | 2 | true  | ",
        "S9(7)V9(2) COMP-3            | NUMBER   | 9  | 2 | true  | COMP3",
        "PIC S9(5) USAGE IS PACKED-DECIMAL | NUMBER | 5 | 0 | true | COMP3",
        "9(4) COMP                    | NUMBER   | 4  | 0 | false | COMP",
        "S9(9) COMP-4                 | NUMBER   | 9  | 0 | true  | COMP",
        "S9(18) BINARY                | NUMBER   | 18 | 0 | true  | COMP",
        "V99                          | NUMBER   | 2  | 2 | false | ",
        "9(8) DISPLAY                 | NUMBER   | 8  | 0 | false | DISPLAY",
        "S9(5) COMPUTATIONAL-3        | NUMBER   | 5  | 0 | true  | COMP3",
        "9(4) COMPUTATIONAL            | NUMBER   | 4  | 0 | false | COMP",
        "9(4) COMPUTATIONAL-4          | NUMBER   | 4  | 0 | false | COMP",
    })
    void parsesValidPictures(String pic, Category category, int length, int scale, boolean signed,
        Usage usage) {
        assertThat(PicParser.parse(pic))
            .isEqualTo(new PicSpec(category, length, scale, signed, Optional.ofNullable(usage),
                Optional.empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "X(0)", "X(", "9(3", "ZZ9", "9(5).99", "-9(3)", "9(3)CR", "S9(5) COMP-1", "S9(5) COMP-2",
        "S9(5) COMP-5", "A(3)", "N(3)", "9(3)P", "X(3) COMP-3", "SX(3)", "9(3)V", "9(3) FOO"
    })
    void rejectsInvalidOrUnsupportedPictures(String pic) {
        assertThatThrownBy(() -> PicParser.parse(pic))
            .isInstanceOf(PicSyntaxException.class)
            .hasMessageContaining(pic);
    }

    @Test
    void rejectsEmptyPicture() {
        assertThatThrownBy(() -> PicParser.parse(""))
            .isInstanceOf(PicSyntaxException.class)
            .hasMessageContaining("empty");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // pic                          | category | length | scale | signed | usage
        "PICTURE X(2)X                  | TEXT     | 3  | 0 | false | ",
        "X(3) DISPLAY                   | TEXT     | 3  | 0 | false | DISPLAY",
        "x(3) usage is display .        | TEXT     | 3  | 0 | false | DISPLAY",
        "9(3) USAGE COMP                | NUMBER   | 3  | 0 | false | COMP",
        "pic s9(5)v9 comp-3             | NUMBER   | 6  | 1 | true  | COMP3",
        "S99V9(3)                       | NUMBER   | 5  | 3 | true  | ",
        "SV9                            | NUMBER   | 1  | 1 | true  | ",
    })
    void parsesOtherValidForms(String pic, Category category, int length, int scale,
        boolean signed, Usage usage) {
        assertThat(PicParser.parse(pic))
            .isEqualTo(new PicSpec(category, length, scale, signed, Optional.ofNullable(usage),
                Optional.empty()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // pic                                   | problem
        "'   '                                   | the string is empty",
        ".                                       | the string is empty",
        "PIC                                     | the picture is missing",
        "PICTURE .                               | the picture is missing",
        "S                                       | the picture has no digits",
        "V                                       | a 'V' must have decimal digits after it",
        "X()                                     | the repeat count '' must be",
        "X(1234567890)                           | the repeat count '1234567890' must be",
        "X(1A)                                   | the repeat count '1A' must be",
        "X(-1)                                   | the repeat count '-1' must be",
        "9(0)                                    | the repeat count must be greater than 0",
        "X(999999999)X(999999999)X(999999999)    | the length is too large",
        "9(999999999)9(999999999)V9(999999999)   | the length is too large",
        "9(3)X                                   | unexpected or unsupported character 'X'",
        "ZZ9                                     | unexpected or unsupported character 'Z'",
        "SX(3)                                   | unexpected or unsupported character 'X'",
        "A(3)                                    | unexpected or unsupported character 'A'",
        "-9(3)                                   | unexpected or unsupported character '-'",
        "VZ                                      | unexpected or unsupported character 'Z'",
        "9(3) USAGE                              | the usage is missing after USAGE",
        "9(3) USAGE IS                           | the usage is missing after USAGE",
        "9(3) COMP-5                             | unknown or unsupported usage 'COMP-5'",
        "9(3) COMP SYNC                          | unexpected text 'SYNC' after the usage",
        "X(3) BINARY                             | a text picture must not have a numeric usage",
        "S9(3) SIGN                              | SIGN needs LEADING or TRAILING",
        "S9(3) SIGN IS                           | SIGN needs LEADING or TRAILING",
        "S9(3) SIGN IS SEPARATE                  | SIGN needs LEADING or TRAILING",
        "S9(3) LEADING SIGN TRAILING             | unexpected text 'SIGN' after the sign clause",
        "S9(3) LEADING FOO                       | unexpected text 'FOO' after the sign clause",
        "S9(3) LEADING CHARACTER                 | unexpected text 'CHARACTER' after the sign",
        "S9(3) COMP-3 COMP                       | unexpected text 'COMP' after the usage",
        "9(3) LEADING                            | a SIGN clause needs a signed numeric picture",
        "X(3) SIGN TRAILING                      | a SIGN clause needs a signed numeric picture",
        "S9(3) COMP-3 SIGN LEADING               | a SIGN clause is permitted only with usage DISPLAY",
        "S9(3) LEADING BINARY                    | a SIGN clause is permitted only with usage DISPLAY",
    })
    void namesTheProblemAndTheOriginalString(String pic, String problem) {
        assertThatThrownBy(() -> PicParser.parse(pic))
            .isInstanceOf(PicSyntaxException.class)
            .hasMessageStartingWith("Invalid PIC string \"" + pic + "\": ")
            .hasMessageContaining(problem);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // pic                                          | usage   | sign
        "S9(5)V99 SIGN LEADING                          |         | LEADING",
        "S9(5)V99 SIGN IS TRAILING                      |         | TRAILING",
        "S9(5)V99 LEADING SEPARATE                      |         | LEADING_SEPARATE",
        "S9(5)V99 SIGN TRAILING SEPARATE CHARACTER      |         | TRAILING_SEPARATE",
        "PIC S9(5)V99 DISPLAY SIGN IS LEADING SEPARATE. | DISPLAY | LEADING_SEPARATE",
        "s9(5)v99 sign leading separate usage display   | DISPLAY | LEADING_SEPARATE",
        "S9(5)V99 TRAILING                              |         | TRAILING",
    })
    void parsesTheSignClause(String pic, Usage usage, Sign sign) {
        assertThat(PicParser.parse(pic)).isEqualTo(new PicSpec(Category.NUMBER, 7, 2, true,
            Optional.ofNullable(usage), Optional.of(sign)));
    }
}
