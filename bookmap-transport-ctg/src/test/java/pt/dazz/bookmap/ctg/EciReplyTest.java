package pt.dazz.bookmap.ctg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EciReplyTest {

    @Test
    void keepsTheValues() {
        byte[] commarea = new byte[4];
        EciReply reply = new EciReply(-7, "ASRA", commarea);
        assertThat(reply.returnCode()).isEqualTo(-7);
        assertThat(reply.abendCode()).isEqualTo("ASRA");
        assertThat(reply.commarea()).isSameAs(commarea);
    }
}
