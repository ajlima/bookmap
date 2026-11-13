package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
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

    @Test
    void toStringHidesThePassword() {
        EciCall call = new EciCall("CICSPROD", "ORDUSER", "s3cr3t", "PGMORD01", "TX01",
            new byte[2], 10, 30);
        assertThat(call.toString()).doesNotContain("s3cr3t").contains("password=***")
            .contains("ORDUSER").contains("PGMORD01").contains("commareaLength=10");
        assertThat(new EciCall(null, null, null, "PGMORD01", null, new byte[2], 10, 0).toString())
            .contains("password=null");
    }

    @Test
    void rejectsCommareaLongerThanLength() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EciCall(null, null, null, "PGMORD01", null, new byte[11], 10, 0))
            .withMessageContaining("commareaLength");
    }

    @Test
    void rejectsBadTimeout() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EciCall(null, null, null, "PGMORD01", null, new byte[1], 1, -1));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new EciCall(null, null, null, "PGMORD01", null, new byte[1], 1, 32_768));
    }
}
