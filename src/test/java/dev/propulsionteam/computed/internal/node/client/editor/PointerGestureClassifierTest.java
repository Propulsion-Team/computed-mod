package dev.propulsionteam.computed.internal.node.client.editor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PointerGestureClassifierTest {
    @Test void pathmindThresholdsAreInclusiveForClicks() {
        assertFalse(PointerGestureClassifier.exceededDragThreshold(10, 10, 15, 15));
        assertTrue(PointerGestureClassifier.exceededDragThreshold(10, 10, 16, 10));
        assertTrue(PointerGestureClassifier.isContextClick(10, 10, 100, 15, 15, 350));
        assertFalse(PointerGestureClassifier.isContextClick(10, 10, 100, 15, 15, 351));
        assertFalse(PointerGestureClassifier.isContextClick(10, 10, 100, 16, 10, 200));
    }
}
