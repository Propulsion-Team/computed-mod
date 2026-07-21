package dev.propulsionteam.computed.internal.node.api.elements;

import dev.propulsionteam.computed.internal.node.api.WElement;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class WSlider extends WElement {
    private static final int INPUT_W = 38;
    private static final int GAP = 4;

    private double value;
    private double min, max;
    private String label;
    private boolean dragging;
    private double dragStartValue;

    private final int barWidth;
    private boolean inputFocused;
    private String inputBuffer = "";

    public WSlider(String label, double min, double max, int width) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.barWidth = width;
        this.width = width + GAP + INPUT_W;
        this.height = 14;
        this.value = min;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        boolean barHovered = mouseX >= x && mouseX <= x + barWidth && mouseY >= y && mouseY <= y + height;

        if (dragging) {
            double relX = (mouseX - x) / (double) barWidth;
            value = min + Mth.clamp(relX, 0, 1) * (max - min);
            if (!inputFocused) {
                inputBuffer = formatValue(value);
            }
        }

        // Background
        graphics.fill(x, y + 4, x + barWidth, y + 10, ComputedEditorTheme.BACKGROUND_INPUT);
        graphics.renderOutline(x, y + 4, barWidth, 6, ComputedEditorTheme.BORDER_DEFAULT);

        // Fill
        int fillW = (int) ((value - min) / (max - min) * barWidth);
        graphics.fill(x, y + 4, x + fillW, y + 10, ComputedEditorTheme.ACCENT_HEADER);

        // Knob
        graphics.fill(
                x + fillW - 2,
                y + 2,
                x + fillW + 2,
                y + 12,
                barHovered || dragging ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.ACCENT);

        // Label (above)
        Minecraft mc = Minecraft.getInstance();
        String text = String.format("%s: %s", label, formatValue(value));
        graphics.drawString(mc.font, text, x, y - 8, ComputedEditorTheme.TEXT_SECONDARY, false);

        // Input box (right)
        int ix = x + barWidth + GAP;
        int iy = y + 2;
        int ih = 10;
        ComputedEditorStyle.drawField(graphics, ix, iy, INPUT_W, ih, inputFocused, false);
        String shown = inputFocused ? inputBuffer : formatValue(value);
        int textW = mc.font.width(shown);
        int tx = ix + Math.max(2, INPUT_W - 3 - textW);
        int ty = iy + 1;
        graphics.drawString(
                mc.font,
                shown,
                tx,
                ty,
                inputFocused ? ComputedEditorTheme.TEXT_HEADER : ComputedEditorTheme.TEXT_PRIMARY,
                false);
        if (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + textW;
            graphics.fill(cx, ty - 1, cx + 1, ty + mc.font.lineHeight, ComputedEditorTheme.TEXT_HEADER);
        }
    }

    @Override
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        boolean onBar = mouseX >= 0 && mouseX <= barWidth && mouseY >= 0 && mouseY <= height;
        int ix = barWidth + GAP;
        boolean onInput = mouseX >= ix && mouseX <= ix + INPUT_W && mouseY >= 0 && mouseY <= height;

        if (onBar) {
            commitInputIfFocused();
            inputFocused = false;
            dragging = true;
            dragStartValue = value;
            playClick(1.03f);
            return true;
        }
        if (onInput) {
            if (!inputFocused) {
                inputBuffer = formatValue(value);
            }
            inputFocused = true;
            playClick(1.0f);
            return true;
        }
        if (inputFocused) {
            commitInputIfFocused();
            inputFocused = false;
        }
        return false;
    }

    @Override
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
        if (dragging && Math.abs(value - dragStartValue) > 1.0e-6) {
            playClick(0.95f);
        }
        dragging = false;
        return false;
    }

    @Override
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!inputFocused) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!inputBuffer.isEmpty()) {
                inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitInputIfFocused();
            inputFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            inputBuffer = formatValue(value);
            inputFocused = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!inputFocused) {
            return false;
        }
        if ((codePoint >= '0' && codePoint <= '9') || codePoint == '.' || codePoint == '-') {
            inputBuffer = inputBuffer + codePoint;
            return true;
        }
        return false;
    }

    @Override
    public boolean isFocused() {
        return inputFocused;
    }

    @Override
    public void clearFocus() {
        commitInputIfFocused();
        inputFocused = false;
        dragging = false;
    }

    private void commitInputIfFocused() {
        if (!inputFocused) {
            return;
        }
        String s = inputBuffer.trim().replace(',', '.');
        if (s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.")) {
            inputBuffer = formatValue(value);
            return;
        }
        try {
            double parsed = Double.parseDouble(s);
            value = Mth.clamp(parsed, min, max);
        } catch (NumberFormatException ignored) {
            // keep current value
        }
        inputBuffer = formatValue(value);
    }

    private static String formatValue(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0e-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format("%.2f", v);
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = Mth.clamp(value, min, max);
        if (!inputFocused) {
            inputBuffer = formatValue(this.value);
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putDouble("value", value);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("value")) {
            value = Mth.clamp(tag.getDouble("value"), min, max);
            inputBuffer = formatValue(value);
        }
    }

    private static void playClick(float pitch) {
        // No-op in shared code: keep slider server-loadable during graph deserialization.
    }
}
