package pt.dazz.copymapper.runtime.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.dazz.copymapper.runtime.layout.Specs.draft;

import org.junit.jupiter.api.Test;
import pt.dazz.copymapper.copybook.spec.MappingSpec;

class LayoutTest {

    private static Layout layout() {
        MappingSpec spec = draft("REC", "01 REC.", "05 G.", "10 A PIC X(2).", "05 B PIC 9(3).");
        return LayoutCompiler.compile(spec).layout();
    }

    @Test
    void findsANodeByPath() {
        Layout layout = layout();
        assertThat(layout.node("REC").orElseThrow()).isSameAs(layout.root());
        LayoutNode a = layout.node("REC.G.A").orElseThrow();
        assertThat(a.offset()).isZero();
        assertThat(a.length()).isEqualTo(2);
        assertThat(a.spec().path()).isEqualTo("REC.G.A");
        assertThat(layout.node("REC.B").orElseThrow().offset()).isEqualTo(2);
    }

    @Test
    void unknownPathIsEmpty() {
        assertThat(layout().node("REC.NOPE")).isEmpty();
    }

    @Test
    void exposesCharset() {
        assertThat(layout().charset()).isEqualTo("IBM037");
        assertThat(layout().spec().root().path()).isEqualTo("REC");
    }
}
