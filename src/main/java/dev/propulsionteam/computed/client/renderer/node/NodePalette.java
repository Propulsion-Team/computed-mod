package dev.propulsionteam.computed.client.renderer.node;

import java.util.Locale;

public enum NodePalette {
    FLOW(0xFF27C7D9),
    LOGIC(0xFF985AD6),
    MATH(0xFF4E86E8),
    WORLD(0xFF54A968),
    STATE(0xFFE0A23D),
    TEXT(0xFFD653B5),
    WIDGETS(0xFF9BCB45),
    IO(0xFFDA5252),
    LUA(0xFF36A99A),
    INTEGRATION(0xFFE1813B),
    UTILITY(0xFF8A9099);

    public static final int SELECTION = 0xFFFFFFFF;
    public static final int ERROR = 0xFFE65050;
    public static final int WARNING = 0xFFF0B44C;

    private final int frameArgb;

    NodePalette(int frameArgb) {
        this.frameArgb = frameArgb;
    }

    public int frameArgb() {
        return frameArgb;
    }

    public static NodePalette category(String category) {
        if (category == null) {
            return UTILITY;
        }
        String normalized = category.toUpperCase(Locale.ROOT).replace('/', '_');
        if (normalized.equals("I_O")) {
            normalized = "IO";
        }
        try {
            return valueOf(normalized);
        } catch (RuntimeException exception) {
            return normalized.startsWith("INTEGRATION") ? INTEGRATION : UTILITY;
        }
    }
}
