package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CtgRouteTest {

    @Test
    void acceptsProgramAndTransaction() {
        CtgRoute route = new CtgRoute("PGMORD01", "TX01");
        assertThat(route.program()).isEqualTo("PGMORD01");
        assertThat(route.transaction()).isEqualTo("TX01");
    }

    @Test
    void transactionIsOptional() {
        assertThat(new CtgRoute("PGMINV01", null).transaction()).isNull();
    }

    @Test
    void rejectsNullProgram() {
        assertThatNullPointerException().isThrownBy(() -> new CtgRoute(null, "TX01"))
            .withMessage("program");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "PGMORDER1"})
    void rejectsBadProgram(String program) {
        assertThatIllegalArgumentException().isThrownBy(() -> new CtgRoute(program, null))
            .withMessageContaining("program");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "TX001"})
    void rejectsBadTransaction(String transaction) {
        assertThatIllegalArgumentException().isThrownBy(() -> new CtgRoute("PGMORD01", transaction))
            .withMessageContaining("transaction");
    }
}
