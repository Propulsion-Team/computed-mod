package dev.propulsionteam.computed.client.editor;

/**
 * Rendering detail selected from the editor's raw zoom value. Transitions use hysteresis so a
 * trackpad or animated camera cannot rapidly alternate render paths at a boundary.
 */
public enum EditorDetailLevel {
    FULL,
    COMPACT,
    OVERVIEW;

    public static final float ENTER_COMPACT_ZOOM = 0.65f;
    public static final float EXIT_COMPACT_ZOOM = 0.72f;
    public static final float ENTER_OVERVIEW_ZOOM = 0.25f;
    public static final float EXIT_OVERVIEW_ZOOM = 0.32f;

    /** Returns the next stable level for {@code rawZoom}. */
    public EditorDetailLevel update(float rawZoom) {
        return switch (this) {
            case FULL -> rawZoom < ENTER_OVERVIEW_ZOOM
                    ? OVERVIEW
                    : rawZoom < ENTER_COMPACT_ZOOM ? COMPACT : FULL;
            case COMPACT -> rawZoom < ENTER_OVERVIEW_ZOOM
                    ? OVERVIEW
                    : rawZoom >= EXIT_COMPACT_ZOOM ? FULL : COMPACT;
            case OVERVIEW -> rawZoom >= EXIT_COMPACT_ZOOM
                    ? FULL
                    : rawZoom >= EXIT_OVERVIEW_ZOOM ? COMPACT : OVERVIEW;
        };
    }

    public int curveSamples() {
        return switch (this) {
            case FULL -> 24;
            case COMPACT -> 8;
            case OVERVIEW -> 3;
        };
    }

    public boolean rendersComponents() {
        return this == FULL;
    }
}
