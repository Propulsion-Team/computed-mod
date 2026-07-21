package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;

public class WTextField extends WElement {
    private String value = "";
    private boolean focused = false;
    private int cursorPos = 0;
    private int selectionPos = 0;
    private final int minWidth;
    /** Last {@code value} string we measured against the font. Reference compare keeps {@link #render} hot-path allocation-free. */
    private String measuredValue = null;

    public WTextField(int width) {
        this.minWidth = width;
        this.width = width;
        this.height = 12;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.renderOutline(x, y, width, height, focused ? 0xFF00FF88 : 0xFF888888);

        Minecraft mc = Minecraft.getInstance();
        int tx = x + 2;
        int ty = y + 2;

        int selStart = Math.min(cursorPos, selectionPos);
        int selEnd = Math.max(cursorPos, selectionPos);
        if (selStart != selEnd) {
            int left = tx + mc.font.width(value.substring(0, selStart));
            int right = tx + mc.font.width(value.substring(0, selEnd));
            graphics.fill(left, ty, right, ty + mc.font.lineHeight, 0x6633AAFF);
        }

        graphics.drawString(mc.font, value, tx, ty, 0xFFFFFFFF);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + mc.font.width(value.substring(0, cursorPos));
            graphics.fill(cx, ty - 1, cx + 1, ty + mc.font.lineHeight + 1, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean handleMouseClick(double localX, double localY, int button) {
        focused = localX >= 0 && localX <= width && localY >= 0 && localY <= height;
        if (focused && button == 0) {
            int px = Math.max(0, (int) localX - 2);
            cursorPos = indexForPixel(px);
            selectionPos = cursorPos;
        }
        return focused;
    }

    @Override
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
                || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL
                || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT
                || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SUPER
                || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER) {
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectionPos = 0;
            cursorPos = value.length();
            playTypingSound(0.97f);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            copySelection();
            playTypingSound(1.02f);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            if (hasSelection()) {
                copySelection();
                deleteSelection();
                playTypingSound(0.9f);
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) {
                replaceSelection(clip);
                playTypingSound(1.04f);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (cursorPos > 0) {
                int start = ctrl ? previousWordBoundary(cursorPos) : cursorPos - 1;
                value = value.substring(0, start) + value.substring(cursorPos);
                cursorPos = start;
                selectionPos = cursorPos;
                updateWidthForValue();
            }
            playTypingSound(0.9f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (cursorPos < value.length()) {
                int end = ctrl ? nextWordBoundary(cursorPos) : cursorPos + 1;
                value = value.substring(0, cursorPos) + value.substring(end);
                updateWidthForValue();
            }
            playTypingSound(0.9f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            int next = ctrl ? previousWordBoundary(cursorPos) : Math.max(0, cursorPos - 1);
            moveCursor(next, shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            int next = ctrl ? nextWordBoundary(cursorPos) : Math.min(value.length(), cursorPos + 1);
            moveCursor(next, shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            moveCursor(0, shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            moveCursor(value.length(), shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focused = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!focused) return false;
        if (Character.isISOControl(codePoint)) {
            return true;
        }
        replaceSelection(String.valueOf(codePoint));
        playTypingSound(1.0f);
        return true;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
        cursorPos = this.value.length();
        selectionPos = cursorPos;
        updateWidthForValue();
    }

    @Override
    public int getWidth() {
        return super.getWidth();
    }
    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("value", value);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        this.value = tag.getString("value");
        cursorPos = this.value.length();
        selectionPos = cursorPos;
        updateWidthForValue();
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    private boolean hasSelection() {
        return cursorPos != selectionPos;
    }

    private void moveCursor(int nextPos, boolean keepSelection) {
        cursorPos = Math.max(0, Math.min(value.length(), nextPos));
        if (!keepSelection) {
            selectionPos = cursorPos;
        }
    }

    private int indexForPixel(int pixelX) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i <= value.length(); i++) {
            if (mc.font.width(value.substring(0, i)) >= pixelX) {
                return i;
            }
        }
        return value.length();
    }

    private int previousWordBoundary(int from) {
        int i = Math.max(0, from);
        while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(value.charAt(i - 1))) i--;
        return i;
    }

    private int nextWordBoundary(int from) {
        int i = Math.min(value.length(), from);
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        while (i < value.length() && !Character.isWhitespace(value.charAt(i))) i++;
        return i;
    }

    private void deleteSelection() {
        int start = Math.min(cursorPos, selectionPos);
        int end = Math.max(cursorPos, selectionPos);
        value = value.substring(0, start) + value.substring(end);
        cursorPos = start;
        selectionPos = start;
        updateWidthForValue();
    }

    private void replaceSelection(String text) {
        deleteSelection();
        value = value.substring(0, cursorPos) + text + value.substring(cursorPos);
        cursorPos += text.length();
        selectionPos = cursorPos;
        updateWidthForValue();
    }

    private void updateWidthForValue() {
        if (value == measuredValue || (measuredValue != null && measuredValue.equals(value))) {
            return;
        }
        measuredValue = value;
        int textWidth;
        if (FMLEnvironment.dist.isDedicatedServer()) {
            textWidth = value.length() * 6;
        } else {
            Minecraft mc = Minecraft.getInstance();
            textWidth = mc == null ? value.length() * 6 : mc.font.width(value);
        }
        int newWidth = Math.max(minWidth, textWidth + 8);
        if (newWidth != width) {
            width = newWidth;
            markLayoutDirty();
        }
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        int start = Math.min(cursorPos, selectionPos);
        int end = Math.max(cursorPos, selectionPos);
        Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(start, end));
    }

    private static void playTypingSound(float pitch) {
        // No-op in shared code: keep text field server-loadable during graph deserialization.
    }
}
