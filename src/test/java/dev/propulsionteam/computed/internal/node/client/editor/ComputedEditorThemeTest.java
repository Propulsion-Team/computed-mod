package dev.propulsionteam.computed.internal.node.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ComputedEditorThemeTest {
    @Test
    void pathmindNeutralPaletteAndComputedAccentStayStable() {
        assertEquals(0xFF111111, ComputedEditorTheme.BACKGROUND_PRIMARY);
        assertEquals(0xFF1A1A1A, ComputedEditorTheme.BACKGROUND_SECONDARY);
        assertEquals(0xFF262626, ComputedEditorTheme.BACKGROUND_TERTIARY);
        assertEquals(0xFF101010, ComputedEditorTheme.BACKGROUND_INPUT);
        assertEquals(0xFF4A4A4A, ComputedEditorTheme.BORDER_MENU);
        assertEquals(0xFFE7E7E0, ComputedEditorTheme.TEXT_PRIMARY);
        assertEquals(0xFF00FF88, ComputedEditorTheme.ACCENT);
        assertEquals(0xFF669966, ComputedEditorTheme.ACCENT_MUTED);
    }

    @Test
    void nodeBodyKeepsLockedStateAboveSelectionAndHover() {
        assertEquals(
                ComputedEditorTheme.BACKGROUND_SECONDARY,
                ComputedEditorTheme.nodeBody(false, false, false));
        assertEquals(
                ComputedEditorTheme.BACKGROUND_TERTIARY,
                ComputedEditorTheme.nodeBody(true, false, false));
        assertEquals(
                ComputedEditorTheme.BACKGROUND_TERTIARY,
                ComputedEditorTheme.nodeBody(false, true, false));
        assertEquals(0xFF261A1A, ComputedEditorTheme.nodeBody(true, true, true));
    }

    @Test
    void nodeOutlineUsesDiagnosticLockDragAndSelectionPriority() {
        assertEquals(
                ComputedEditorTheme.STATUS_ERROR,
                ComputedEditorTheme.nodeOutline(true, true, true, true, true));
        assertEquals(
                ComputedEditorTheme.STATUS_WARNING,
                ComputedEditorTheme.nodeOutline(true, true, false, true, true));
        assertEquals(
                ComputedEditorTheme.STATUS_LOCKED,
                ComputedEditorTheme.nodeOutline(true, true, false, false, true));
        assertEquals(
                ComputedEditorTheme.BORDER_DRAGGING,
                ComputedEditorTheme.nodeOutline(true, true, false, false, false));
        assertEquals(
                ComputedEditorTheme.TEXT_HEADER,
                ComputedEditorTheme.nodeOutline(true, false, false, false, false));
        assertEquals(
                ComputedEditorTheme.ACCENT,
                ComputedEditorTheme.nodeOutline(false, false, false, false, false));
    }

    @Test
    void nodeLabelUsesSameDiagnosticPriority() {
        assertEquals(
                ComputedEditorTheme.STATUS_ERROR_TEXT,
                ComputedEditorTheme.nodeLabel(true, true, true));
        assertEquals(
                ComputedEditorTheme.STATUS_WARNING_TEXT,
                ComputedEditorTheme.nodeLabel(false, true, true));
        assertEquals(
                ComputedEditorTheme.STATUS_LOCKED_TEXT,
                ComputedEditorTheme.nodeLabel(false, false, true));
        assertEquals(
                ComputedEditorTheme.TEXT_PRIMARY,
                ComputedEditorTheme.nodeLabel(false, false, false));
    }
}
