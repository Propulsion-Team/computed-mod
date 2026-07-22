package dev.propulsionteam.computed.internal.node.client.editor;

/** Shared, render-independent pointer thresholds used by the Pathmind-style editor controls. */
public final class PointerGestureClassifier {
    public static final int DRAG_THRESHOLD_PX = 5;
    public static final long CLICK_TIME_THRESHOLD_MS = 250L;

    private PointerGestureClassifier() {}

    public static boolean exceededDragThreshold(int startX, int startY, int x, int y) {
        return Math.abs(x - startX) > DRAG_THRESHOLD_PX || Math.abs(y - startY) > DRAG_THRESHOLD_PX;
    }

    public static boolean isContextClick(
            int startX, int startY, long startedAtMs, int x, int y, long releasedAtMs) {
        return !exceededDragThreshold(startX, startY, x, y)
                && Math.max(0L, releasedAtMs - startedAtMs) <= CLICK_TIME_THRESHOLD_MS;
    }
}
