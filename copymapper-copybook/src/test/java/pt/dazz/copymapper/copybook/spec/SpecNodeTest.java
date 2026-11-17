package pt.dazz.copymapper.copybook.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecNodeTest {

    private static SpecNode node(String name, List<ConditionSpec> conditions,
            List<SpecNode> children) {
        return new SpecNode(name, 5, name, null, null, null, null, null, null, conditions, null,
                false, false, null, null, null, null, null, null, null, null, children);
    }

    @Test
    void copiesLists() {
        var children = new ArrayList<SpecNode>();
        var conditions = new ArrayList<ConditionSpec>();
        var n = node("A", conditions, children);
        children.add(node("B", List.of(), List.of()));
        conditions.add(new ConditionSpec("C", List.of()));
        assertTrue(n.children().isEmpty());
        assertTrue(n.conditions().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> n.children().add(n));
    }

    @Test
    void isGroupAndIsFiller() {
        var leaf = node("FILLER", List.of(), List.of());
        var group = node("G", List.of(), List.of(leaf));
        assertTrue(leaf.isFiller());
        assertFalse(leaf.isGroup());
        assertTrue(group.isGroup());
        assertFalse(group.isFiller());
    }

    @Test
    void rejectsNullNames() {
        assertThrows(NullPointerException.class, () -> node(null, List.of(), List.of()));
    }

    @Test
    void tableSpecNeedsPathForCountAndKey() {
        assertThrows(IllegalArgumentException.class, () -> new TableSpec(TableMode.COUNT, null));
        assertThrows(IllegalArgumentException.class, () -> new TableSpec(TableMode.KEY, null));
        assertEquals("X", new TableSpec(TableMode.COUNT, "X").path());
        assertEquals("X", new TableSpec(TableMode.KEY, "X").path());
    }

    @Test
    void tableSpecRejectsPathForMarkers() {
        assertThrows(IllegalArgumentException.class,
                () -> new TableSpec(TableMode.LOW_VALUES, "X"));
        assertThrows(IllegalArgumentException.class, () -> new TableSpec(TableMode.SPACES, "X"));
        assertEquals(TableMode.SPACES, new TableSpec(TableMode.SPACES, null).mode());
        assertEquals(TableMode.LOW_VALUES, new TableSpec(TableMode.LOW_VALUES, null).mode());
    }

    @Test
    void directionSides() {
        assertTrue(FieldDirection.INPUT.inRequest());
        assertFalse(FieldDirection.INPUT.inResponse());
        assertFalse(FieldDirection.OUTPUT.inRequest());
        assertTrue(FieldDirection.OUTPUT.inResponse());
        assertTrue(FieldDirection.INPUT_OUTPUT.inRequest());
        assertTrue(FieldDirection.INPUT_OUTPUT.inResponse());
        for (var d : List.of(FieldDirection.IGNORE, FieldDirection.DERIVED)) {
            assertFalse(d.inRequest());
            assertFalse(d.inResponse());
        }
    }

    @Test
    void jsonTypesExist() {
        assertEquals(6, JsonType.values().length);
    }

    @Test
    void variantsAndValueRecords() {
        var values = new LinkedHashMap<String, String>();
        values.put("B", "P.B");
        values.put("A", "P.A");
        var d = new VariantSpec.Discriminator("P.T", values);
        values.put("C", "P.C");
        assertEquals(List.of("B", "A"), List.copyOf(d.values().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> d.values().put("Z", "Z"));
        VariantSpec v = new VariantSpec.FixedSelection("R", null);
        var fixed = assertInstanceOf(VariantSpec.FixedSelection.class, v);
        assertNull(fixed.response());
        assertEquals("R", fixed.request());
        var from = new ValueSpec("NUMERIC", "1");
        var range = new ConditionRange(from, null);
        var c = new ConditionSpec("OK", List.of(range));
        assertEquals("OK", c.name());
        assertEquals(1, c.values().size());
        assertEquals(new OccursSpec(1, 3, null), new OccursSpec(1, 3, null));
        assertEquals(2, new SourceLocation("a", 1, 2).column());
    }
}
