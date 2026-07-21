package dev.propulsionteam.computed.internal.node.client.editor;

/**
 * Computed-owned editor palette. The neutral surfaces and restrained borders mirror Pathmind's
 * visual language while Computed keeps its existing green identity and status colors.
 */
public final class ComputedEditorTheme {
    private ComputedEditorTheme() {}

    public static final int BACKGROUND_PRIMARY = 0xFF111111;
    public static final int BACKGROUND_SECONDARY = 0xFF1A1A1A;
    public static final int BACKGROUND_TERTIARY = 0xFF262626;
    public static final int BACKGROUND_SECTION = 0xFF171717;
    public static final int BACKGROUND_INPUT = 0xFF101010;
    public static final int BACKGROUND_MODAL_SCRIM = 0x88000000;

    public static final int BORDER_SUBTLE = 0xFF2E2E2E;
    public static final int BORDER_DEFAULT = 0xFF3E3E3E;
    public static final int BORDER_MENU = 0xFF4A4A4A;
    public static final int BORDER_HIGHLIGHT = 0xFF676767;
    public static final int BORDER_DRAGGING = 0xFF888888;
    public static final int BORDER_INNER = 0xFF202020;

    public static final int TEXT_PRIMARY = 0xFFE7E7E0;
    public static final int TEXT_HEADER = 0xFFF5F4EE;
    public static final int TEXT_SECONDARY = 0xFFA0A39C;
    public static final int TEXT_TERTIARY = 0xFF6A6A6A;
    public static final int TEXT_DISABLED = 0xFF555555;

    public static final int ACCENT = 0xFF00FF88;
    public static final int ACCENT_MUTED = 0xFF669966;
    public static final int ACCENT_DARK = 0xFF1F6B4B;
    public static final int ACCENT_HEADER = 0x8000FF88;
    public static final int ACCENT_HOVER = 0x3300FF88;
    public static final int ACCENT_SELECTION_FILL = 0x4000FF88;

    public static final int MENU_BACKGROUND = 0xFF1C1C1C;
    public static final int MENU_HOVER = 0xFF2B2B2B;
    public static final int MENU_SELECTED = 0xFF243129;
    public static final int MENU_SEPARATOR = 0xFF363636;

    public static final int BUTTON_BACKGROUND = 0xFF252525;
    public static final int BUTTON_HOVER = 0xFF323232;
    public static final int BUTTON_ACTIVE = 0xFF313131;
    public static final int DANGER_BACKGROUND = 0xFF4F1F1F;
    public static final int DANGER_HOVER = 0xFF642828;

    public static final int STATUS_ERROR = 0xFFFF5555;
    public static final int STATUS_WARNING = 0xFFFFBB55;
    public static final int STATUS_LOCKED = 0xFFFF6666;
    public static final int STATUS_ERROR_TEXT = 0xFFFF9999;
    public static final int STATUS_WARNING_TEXT = 0xFFFFD27A;
    public static final int STATUS_LOCKED_TEXT = 0xFFFFAAAA;

    public static final int SOCKET_BORDER = 0xFF242424;
    public static final int SOCKET_CENTER = TEXT_PRIMARY;
    public static final int SELECTION_TEXT_BACKGROUND = 0x6633AAFF;

    public static int nodeBody(boolean hovered, boolean selected, boolean locked) {
        if (locked) {
            return 0xFF261A1A;
        }
        return hovered || selected ? BACKGROUND_TERTIARY : BACKGROUND_SECONDARY;
    }

    public static int nodeOutline(
            boolean selected,
            boolean dragged,
            boolean diagnosticError,
            boolean diagnosticWarning,
            boolean locked) {
        if (diagnosticError) {
            return STATUS_ERROR;
        }
        if (diagnosticWarning) {
            return STATUS_WARNING;
        }
        if (locked) {
            return STATUS_LOCKED;
        }
        if (dragged) {
            return BORDER_DRAGGING;
        }
        return selected ? TEXT_HEADER : ACCENT;
    }

    public static int nodeLabel(boolean diagnosticError, boolean diagnosticWarning, boolean locked) {
        if (diagnosticError) {
            return STATUS_ERROR_TEXT;
        }
        if (diagnosticWarning) {
            return STATUS_WARNING_TEXT;
        }
        if (locked) {
            return STATUS_LOCKED_TEXT;
        }
        return TEXT_PRIMARY;
    }
}
