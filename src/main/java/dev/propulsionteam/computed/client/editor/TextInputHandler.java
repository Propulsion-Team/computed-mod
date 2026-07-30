package dev.propulsionteam.computed.client.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Shared single-line text editing state for editor controls. */
public final class TextInputHandler {
    private final int maximumLength;
    private String text = "";
    private int cursor;
    private int selectionAnchor;
    private boolean focused;

    public TextInputHandler(int maximumLength) {
        this.maximumLength = maximumLength;
    }

    public void focus(String text) {
        setText(text);
        focused = true;
    }

    public void blur() {
        focused = false;
    }

    public boolean focused() {
        return focused;
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        cursor = this.text.length();
        selectionAnchor = cursor;
    }

    public int cursor() {
        return cursor;
    }

    public int selectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    public int selectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    public boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    public void click(int position, boolean extendSelection) {
        moveTo(position, extendSelection);
    }

    public void dragTo(int position) {
        cursor = clamp(position);
    }

    public boolean keyPressed(int keyCode) {
        if (!focused) {
            return false;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
            selectionAnchor = 0;
            cursor = text.length();
        } else if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd()));
            }
        } else if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_X) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selectionStart(), selectionEnd()));
                replaceSelection("");
            }
        } else if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            replaceSelection(Minecraft.getInstance().keyboardHandler.getClipboard());
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            moveTo(Math.max(0, cursor - 1), Screen.hasShiftDown());
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            moveTo(Math.min(text.length(), cursor + 1), Screen.hasShiftDown());
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            moveTo(0, Screen.hasShiftDown());
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            moveTo(text.length(), Screen.hasShiftDown());
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                replaceSelection("");
            } else if (cursor > 0) {
                selectionAnchor = cursor - 1;
                replaceSelection("");
            }
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                replaceSelection("");
            } else if (cursor < text.length()) {
                selectionAnchor = cursor + 1;
                replaceSelection("");
            }
        } else {
            return false;
        }
        return true;
    }

    public boolean charTyped(char character) {
        if (!focused || Character.isISOControl(character)) {
            return false;
        }
        replaceSelection(Character.toString(character));
        return true;
    }

    private void moveTo(int position, boolean extendSelection) {
        cursor = clamp(position);
        if (!extendSelection) {
            selectionAnchor = cursor;
        }
    }

    private void replaceSelection(String replacement) {
        String inserted = replacement == null ? "" : replacement.replace("\r", "").replace("\n", "");
        int start = selectionStart();
        int end = selectionEnd();
        int available = maximumLength - (text.length() - (end - start));
        if (available <= 0) {
            inserted = "";
        } else if (inserted.length() > available) {
            inserted = inserted.substring(0, available);
        }
        text = text.substring(0, start) + inserted + text.substring(end);
        cursor = start + inserted.length();
        selectionAnchor = cursor;
    }

    private int clamp(int position) {
        return Math.max(0, Math.min(text.length(), position));
    }
}
