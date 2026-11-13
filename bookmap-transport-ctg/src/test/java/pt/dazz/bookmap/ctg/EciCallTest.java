package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class EciCallTest {

    @Test
    void rejectsNullProgram() {
        assertThatNullPointerException()
            .isThrownBy(() -> new EciCall(null, null, null, null, null, new byte[1], 1, 0))
            .withMessage("program");
    }

    @Test
    void rejectsNullCommarea() {
        assertThatNullPointerException()
            .isThrownBy(() -> new EciCall(null, null, null, "PGMORD01", null, null, 1, 0))
            .withMessage("commarea");
    }

    @Test
    void acceptsNullOptionalValues() {
        EciCall call = new EciCall(null, null, null, "PGMORD01", null, new byte[2], 10, 0);
        assertThat(call.server()).isNull();
        assertThat(call.userid()).isNull();
        assertThat(call.password()).isNull();
        assertThat(call.transaction()).isNull();
        assertThat(call.commareaLength()).isEqualTo(10);
    }
}
