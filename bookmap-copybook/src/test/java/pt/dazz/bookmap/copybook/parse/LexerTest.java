package pt.dazz.bookmap.copybook.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.copybook.diag.Diagnostic;
import pt.dazz.bookmap.copybook.diag.Diagnostics;
import pt.dazz.bookmap.copybook.diag.Severity;

class LexerTest {

    private final Diagnostics diagnostics = new Diagnostics();

    private List<Token> fixed(String... lines) {
        return Lexer.tokenize(SourceReader.read("ORDER.cpy", String.join("\n", lines),
            SourceFormat.FIXED, diagnostics), diagnostics);
    }

    private List<Token> free(String... lines) {
        return Lexer.tokenize(SourceReader.read("ORDER.cpy", String.join("\n", lines),
            SourceFormat.FREE, diagnostics), diagnostics);
    }

    @Test
    void wordsAreUpperCase() {
        assertThat(free("05 order-id pic x(10).")).extracting(Token::type, Token::text)
            .containsExactly(tuple(TokenType.NUMBER, "05"), tuple(TokenType.WORD, "ORDER-ID"),
                tuple(TokenType.WORD, "PIC"), tuple(TokenType.PICTURE, "X(10)"),
                tuple(TokenType.PERIOD, "."));
    }

    @Test
    void tagWordIsOneToken() {
        assertThat(free("05 :PREFIX:-ID PIC X.")).extracting(Token::text)
            .contains(":PREFIX:-ID");
    }

    @Test
    void pictureIsOneTokenWithoutTheFinalPeriod() {
        assertThat(free("05 AMOUNT PIC S9(7)V99.")).extracting(Token::type, Token::text)
            .endsWith(tuple(TokenType.PICTURE, "S9(7)V99"), tuple(TokenType.PERIOD, "."));
    }

    @Test
    void pictureIsAfterIs() {
        assertThat(free("05 A PICTURE IS X(10) DISPLAY.")).extracting(Token::type, Token::text)
            .containsExactly(tuple(TokenType.NUMBER, "05"), tuple(TokenType.WORD, "A"),
                tuple(TokenType.WORD, "PICTURE"), tuple(TokenType.WORD, "IS"),
                tuple(TokenType.PICTURE, "X(10)"), tuple(TokenType.WORD, "DISPLAY"),
                tuple(TokenType.PERIOD, "."));
    }

    @Test
    void pictureWithDecimalPoint() {
        assertThat(free("05 A PIC 9.99.")).extracting(Token::type, Token::text)
            .endsWith(tuple(TokenType.PICTURE, "9.99"), tuple(TokenType.PERIOD, "."));
    }

    @Test
    void aCommaInAPictureIsNotASeparator() {
        assertThat(free("05 A PIC $Z,ZZ9.99CR, VALUE 0; 05 B PIC 9;9, VALUE 1,5.")).extracting(
                Token::type, Token::text)
            .containsExactly(tuple(TokenType.NUMBER, "05"), tuple(TokenType.WORD, "A"),
                tuple(TokenType.WORD, "PIC"), tuple(TokenType.PICTURE, "$Z,ZZ9.99CR"),
                tuple(TokenType.WORD, "VALUE"), tuple(TokenType.NUMBER, "0"),
                tuple(TokenType.NUMBER, "05"), tuple(TokenType.WORD, "B"),
                tuple(TokenType.WORD, "PIC"), tuple(TokenType.PICTURE, "9;9"),
                tuple(TokenType.WORD, "VALUE"), tuple(TokenType.NUMBER, "1"),
                tuple(TokenType.NUMBER, "5"), tuple(TokenType.PERIOD, "."));
    }

    @Test
    void aCommaAtTheEndOfAPictureLineIsASeparator() {
        assertThat(free("05 A PIC ZZ9,")).extracting(Token::type, Token::text)
            .endsWith(tuple(TokenType.PICTURE, "ZZ9"));
    }

    @Test
    void pictureAtTheEndOfTheLine() {
        assertThat(free("05 A PIC", "X(3)", ".")).extracting(Token::type)
            .endsWith(TokenType.PICTURE, TokenType.PERIOD);
    }

    @Test
    void numbersAndSeparators() {
        assertThat(free("05 A OCCURS 1 TO 10, DEPENDING ON N; VALUE -1.5 +2.")).extracting(
                Token::type, Token::text)
            .containsExactly(tuple(TokenType.NUMBER, "05"), tuple(TokenType.WORD, "A"),
                tuple(TokenType.WORD, "OCCURS"), tuple(TokenType.NUMBER, "1"),
                tuple(TokenType.WORD, "TO"), tuple(TokenType.NUMBER, "10"),
                tuple(TokenType.WORD, "DEPENDING"), tuple(TokenType.WORD, "ON"),
                tuple(TokenType.WORD, "N"), tuple(TokenType.WORD, "VALUE"),
                tuple(TokenType.NUMBER, "-1.5"), tuple(TokenType.NUMBER, "+2"),
                tuple(TokenType.PERIOD, "."));
    }

    @Test
    void literals() {
        assertThat(free("88 OPEN VALUE 'O''K' \"X\".")).extracting(Token::type, Token::text)
            .contains(tuple(TokenType.LITERAL, "O'K"), tuple(TokenType.LITERAL, "X"));
    }

    @Test
    void literalContinuesOnNextLine() {
        List<Token> tokens = fixed(
            "       05 NOTE PIC X(12) VALUE 'ABC",
            "      -    'DEF'.");
        assertThat(tokens).extracting(Token::type, Token::text)
            .endsWith(tuple(TokenType.LITERAL, "ABC" + " ".repeat(65 - 28) + "DEF"),
                tuple(TokenType.PERIOD, "."));
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    void continuationWithoutOpenLiteralIsCode() {
        assertThat(fixed("       05 A PIC X(3)", "      -    .")).extracting(Token::type)
            .endsWith(TokenType.PICTURE, TokenType.PERIOD);
    }

    @Test
    void pseudoTextAcrossLines() {
        assertThat(free("COPY ADDR REPLACING ==:PFX:", "== BY == SHIP ==.")).extracting(
                Token::type, Token::text)
            .containsExactly(tuple(TokenType.WORD, "COPY"), tuple(TokenType.WORD, "ADDR"),
                tuple(TokenType.WORD, "REPLACING"), tuple(TokenType.PSEUDO_TEXT, ":PFX:"),
                tuple(TokenType.WORD, "BY"), tuple(TokenType.PSEUDO_TEXT, "SHIP"),
                tuple(TokenType.PERIOD, "."));
    }

    @Test
    void unterminatedLiteralIsAnError() {
        free("05 A VALUE 'ABC");
        assertThat(diagnostics.all()).containsExactly(new Diagnostic(Severity.ERROR,
            "ORDER.cpy", 1, 12, "the literal has no closing quote"));
    }

    @Test
    void unterminatedLiteralBeforeACodeLineIsAnError() {
        fixed("       05 A VALUE 'ABC", "       05 B PIC X.");
        assertThat(diagnostics.all()).extracting(Diagnostic::message)
            .containsExactly("the literal has no closing quote");
    }

    @Test
    void unterminatedPseudoTextIsAnError() {
        free("COPY A REPLACING ==X");
        assertThat(diagnostics.all()).containsExactly(new Diagnostic(Severity.ERROR,
            "ORDER.cpy", 1, 18, "the pseudo-text has no closing =="));
    }

    @Test
    void positionsAreOneBased() {
        List<Token> tokens = fixed("       05  ORDER-ID PIC X(10).");
        assertThat(tokens).extracting(Token::line, Token::column)
            .containsExactly(tuple(1, 8), tuple(1, 12), tuple(1, 21), tuple(1, 25),
                tuple(1, 30));
        assertThat(tokens.get(0).source()).isEqualTo("ORDER.cpy");
    }

    @Test
    void separatorsEndWords() {
        assertThat(free("A,B;C")).extracting(Token::text).containsExactly("A", "B", "C");
    }

    @Test
    void literalClosesAtTheEndOfTheLine() {
        assertThat(free("VALUE 'AB'")).extracting(Token::type, Token::text)
            .endsWith(tuple(TokenType.LITERAL, "AB"));
        assertThat(diagnostics.all()).isEmpty();
    }

    @Test
    void tokenIsComparesWords() {
        Token word = new Token(TokenType.WORD, "PIC", "A", 1, 1);
        assertThat(word.is("PIC")).isTrue();
        assertThat(word.is("PICTURE")).isFalse();
        assertThat(new Token(TokenType.LITERAL, "PIC", "A", 1, 1).is("PIC")).isFalse();
    }

    @Test
    void twoEntriesOnOneLine() {
        assertThat(free("05 A PIC X. 05 B VALUE .5.")).extracting(Token::type, Token::text)
            .containsExactly(tuple(TokenType.NUMBER, "05"), tuple(TokenType.WORD, "A"),
                tuple(TokenType.WORD, "PIC"), tuple(TokenType.PICTURE, "X"),
                tuple(TokenType.PERIOD, "."), tuple(TokenType.NUMBER, "05"),
                tuple(TokenType.WORD, "B"), tuple(TokenType.WORD, "VALUE"),
                tuple(TokenType.WORD, ".5"), tuple(TokenType.PERIOD, "."));
    }
}
