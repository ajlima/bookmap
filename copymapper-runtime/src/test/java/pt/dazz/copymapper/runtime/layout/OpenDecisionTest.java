package pt.dazz.copymapper.runtime.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.copybook.spec.SourceLocation;

class OpenDecisionTest {

    @Test
    void formatsSourceLineColumnPathAndMessage() {
        var decision = new OpenDecision(OpenDecisionKind.INVALID_TYPE, "GET-ACCOUNT.AMOUNT",
                new SourceLocation("GET-ACCOUNT.cpy", 4, 12), "AMOUNT has a problem.");
        assertThat(decision.format())
                .isEqualTo("GET-ACCOUNT.cpy:4:12: GET-ACCOUNT.AMOUNT: AMOUNT has a problem.");
    }

    @Test
    void omitsTheLocationWhenThereIsNone() {
        var decision = new OpenDecision(OpenDecisionKind.INVALID_TYPE, "REC.A", null, "Bad.");
        assertThat(decision.format()).isEqualTo("REC.A: Bad.");
    }
}
