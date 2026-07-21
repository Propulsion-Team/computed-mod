package dev.propulsionteam.computed.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditorDetailLevelTest {
    @Test
    void detailTransitionsUseHysteresis() {
        EditorDetailLevel level = EditorDetailLevel.FULL;
        assertEquals(EditorDetailLevel.FULL, level.update(0.65f));
        level = level.update(0.64f);
        assertEquals(EditorDetailLevel.COMPACT, level);
        assertEquals(EditorDetailLevel.COMPACT, level.update(0.71f));
        level = level.update(0.72f);
        assertEquals(EditorDetailLevel.FULL, level);

        level = EditorDetailLevel.COMPACT.update(0.24f);
        assertEquals(EditorDetailLevel.OVERVIEW, level);
        assertEquals(EditorDetailLevel.OVERVIEW, level.update(0.31f));
        assertEquals(EditorDetailLevel.COMPACT, level.update(0.32f));
    }

    @Test
    void largeZoomJumpsSelectTheExpectedLevel() {
        assertEquals(EditorDetailLevel.OVERVIEW, EditorDetailLevel.FULL.update(0.1f));
        assertEquals(EditorDetailLevel.FULL, EditorDetailLevel.OVERVIEW.update(1.0f));
        assertEquals(24, EditorDetailLevel.FULL.curveSamples());
        assertEquals(8, EditorDetailLevel.COMPACT.curveSamples());
        assertEquals(3, EditorDetailLevel.OVERVIEW.curveSamples());
        assertTrue(EditorDetailLevel.FULL.rendersComponents());
        assertFalse(EditorDetailLevel.COMPACT.rendersComponents());
        assertFalse(EditorDetailLevel.OVERVIEW.rendersComponents());
    }
}
