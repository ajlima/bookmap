package pt.dazz.bookmap.http;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class HeaderRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"Host", "Content-Length", "Connection", "Upgrade"})
    void restrictedHeaderIsRejected(String name) {
        assertThatThrownBy(() -> HeaderRules.check(name, "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameThatIsNotATokenIsRejected() {
        assertThatThrownBy(() -> HeaderRules.check("X Bad", "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\r\nb", "a\nb", "a\rb"})
    void valueWithALineBreakIsRejected(String value) {
        assertThatThrownBy(() -> HeaderRules.check("X-Client-Id", value))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullNameAndValueAreRejected() {
        assertThatThrownBy(() -> HeaderRules.check(null, "x"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> HeaderRules.check("X-Client-Id", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void correctHeaderIsAccepted() {
        assertThatCode(() -> HeaderRules.check("X-Client-Id", "order-service"))
            .doesNotThrowAnyException();
    }

    @Test
    void methodRules() {
        assertThatCode(() -> HeaderRules.checkMethod("PUT")).doesNotThrowAnyException();
        assertThatThrownBy(() -> HeaderRules.checkMethod(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HeaderRules.checkMethod(null))
            .isInstanceOf(NullPointerException.class);
    }
}
