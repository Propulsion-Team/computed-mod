package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen;
import dev.propulsionteam.computed.lua.node.FieldControl;
import dev.propulsionteam.computed.lua.node.FieldType;
import dev.propulsionteam.computed.lua.node.LuaFieldSchema;
import dev.propulsionteam.computed.lua.node.LuaFieldValues;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;
import org.luaj.vm2.LuaValue;

final class LuaNodeFieldControl {
    static final int ROW_HEIGHT = 18;
    static final int CONTROL_WIDTH = 76;

    private static final List<String> DIRECTIONS =
            List.of("front", "back", "left", "right", "up", "down");

    private final LuaFieldSchema schema;
    private LuaValue value;
    private String editBuffer = "";
    private boolean focused;
    private boolean expanded;
    private boolean dragging;

    LuaNodeFieldControl(LuaFieldSchema schema, LuaValue value) {
        this.schema = schema;
        this.value = LuaFieldValues.normalize(schema, value);
    }

    LuaFieldSchema schema() {
        return schema;
    }

    LuaValue value() {
        return value;
    }

    void render(
            GuiGraphics graphics,
            int nodeX,
            int rowY,
            int nodeWidth,
            int mouseX,
            int mouseY,
            int accent) {
        var font = Minecraft.getInstance().font;
        int controlX = nodeX + nodeWidth - CONTROL_WIDTH - 9;
        int controlY = rowY + 2;
        int controlHeight = 14;
        graphics.drawString(
                font,
                schema.label(),
                nodeX + 10,
                rowY + 5,
                ComputedEditorTheme.TEXT_SECONDARY,
                false);
        boolean hovered = mouseX >= controlX
                && mouseX < controlX + CONTROL_WIDTH
                && mouseY >= controlY
                && mouseY < controlY + controlHeight;
        if (schema.type() == FieldType.BOOLEAN) {
            renderBoolean(graphics, controlX, controlY, hovered, accent);
        } else if (schema.type() == FieldType.NUMBER
                && schema.control() == FieldControl.SLIDER) {
            renderSlider(graphics, controlX, controlY, hovered, accent);
        } else if (schema.type() == FieldType.CHOICE
                || schema.type() == FieldType.DIRECTION) {
            renderDropdown(graphics, controlX, controlY, hovered, accent);
        } else {
            renderValueBox(graphics, controlX, controlY, hovered, accent);
        }
        if (expanded) {
            renderDropdownOverlay(
                    graphics,
                    controlX,
                    controlY + controlHeight,
                    mouseX,
                    mouseY,
                    accent);
        }
    }

    boolean mouseClicked(
            double localX,
            double localY,
            int button,
            int nodeWidth,
            int rowY) {
        int controlX = nodeWidth - CONTROL_WIDTH - 9;
        int controlY = rowY + 2;
        List<String> options = options();
        if (expanded) {
            int option = (int) ((localY - controlY - 14) / 14);
            if (localX >= controlX
                    && localX < controlX + CONTROL_WIDTH
                    && option >= 0
                    && option < options.size()) {
                value = LuaValue.valueOf(options.get(option));
                expanded = false;
                focused = false;
                return true;
            }
            expanded = false;
            focused = false;
            return true;
        }
        if (button != 0
                || localX < controlX
                || localX >= controlX + CONTROL_WIDTH
                || localY < controlY
                || localY >= controlY + 14) {
            if (focused) {
                commitBuffer();
                focused = false;
                dragging = false;
                return true;
            }
            focused = false;
            dragging = false;
            return false;
        }
        if (schema.type() == FieldType.BOOLEAN) {
            value = LuaValue.valueOf(!value.toboolean());
            return true;
        }
        if (schema.type() == FieldType.NUMBER
                && schema.control() == FieldControl.SLIDER) {
            dragging = true;
            setSlider(localX, controlX);
            return true;
        }
        if (schema.type() == FieldType.CHOICE
                || schema.type() == FieldType.DIRECTION) {
            expanded = true;
            focused = true;
            return true;
        }
        if (schema.type() == FieldType.ITEM) {
            focused = false;
            WNodeScreen.requestItemPick(stack -> value = LuaValue.valueOf(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
            return true;
        }
        focused = true;
        editBuffer = displayValue();
        return true;
    }

    boolean mouseDragged(double localX, int button, int nodeWidth) {
        if (!dragging || button != 0) {
            return false;
        }
        setSlider(localX, nodeWidth - CONTROL_WIDTH - 9);
        return true;
    }

    boolean mouseReleased(int button) {
        if (button != 0 || !dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    boolean keyPressed(int keyCode) {
        if (!focused) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focused = false;
            expanded = false;
            return true;
        }
        if (expanded) {
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
            editBuffer = "";
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(editBuffer);
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            editBuffer += Minecraft.getInstance().keyboardHandler.getClipboard();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !editBuffer.isEmpty()) {
            editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitBuffer();
            focused = false;
            return true;
        }
        return true;
    }

    boolean charTyped(char character) {
        if (!focused || expanded || Character.isISOControl(character)) {
            return false;
        }
        if (editBuffer.length() < 256) {
            editBuffer += character;
        }
        return true;
    }

    boolean focused() {
        return focused || expanded || dragging;
    }

    void clearFocus() {
        if (focused && !expanded) {
            commitBuffer();
        }
        focused = false;
        expanded = false;
        dragging = false;
    }

    int overlayBottom(int rowY) {
        return expanded ? rowY + 16 + options().size() * 14 : rowY + ROW_HEIGHT;
    }

    private void renderBoolean(
            GuiGraphics graphics, int x, int y, boolean hovered, int accent) {
        ComputedEditorStyle.drawField(
                graphics, x, y, CONTROL_WIDTH, 14, focused, hovered, accent);
        int boxX = x + CONTROL_WIDTH - 13;
        graphics.renderOutline(boxX, y + 2, 10, 10, ComputedEditorTheme.BORDER_DEFAULT);
        if (value.toboolean()) {
            graphics.fill(boxX + 2, y + 4, boxX + 8, y + 10, accent);
        }
        graphics.drawString(
                Minecraft.getInstance().font,
                value.toboolean() ? "On" : "Off",
                x + 4,
                y + 3,
                ComputedEditorTheme.TEXT_PRIMARY,
                false);
    }

    private void renderSlider(
            GuiGraphics graphics, int x, int y, boolean hovered, int accent) {
        ComputedEditorStyle.drawField(
                graphics, x, y, CONTROL_WIDTH, 14, dragging, hovered, accent);
        double minimum = schema.minimum();
        double maximum = schema.maximum();
        double ratio = (value.todouble() - minimum) / (maximum - minimum);
        int trackX = x + 4;
        int trackWidth = CONTROL_WIDTH - 8;
        graphics.fill(trackX, y + 9, trackX + trackWidth, y + 11, ComputedEditorTheme.BORDER_SUBTLE);
        int knob = trackX + (int) Math.round(ratio * trackWidth);
        graphics.fill(knob - 2, y + 7, knob + 2, y + 13, accent);
        graphics.drawString(
                Minecraft.getInstance().font,
                formatNumber(value.todouble()),
                x + 4,
                y + 2,
                ComputedEditorTheme.TEXT_PRIMARY,
                false);
    }

    private void renderDropdown(
            GuiGraphics graphics, int x, int y, boolean hovered, int accent) {
        ComputedEditorStyle.drawField(
                graphics, x, y, CONTROL_WIDTH, 14, expanded, hovered, accent);
        graphics.drawString(
                Minecraft.getInstance().font,
                value.tojstring(),
                x + 4,
                y + 3,
                ComputedEditorTheme.TEXT_PRIMARY,
                false);
        graphics.drawString(
                Minecraft.getInstance().font,
                expanded ? "▴" : "▾",
                x + CONTROL_WIDTH - 10,
                y + 3,
                ComputedEditorTheme.TEXT_SECONDARY,
                false);
    }

    private void renderValueBox(
            GuiGraphics graphics, int x, int y, boolean hovered, int accent) {
        ComputedEditorStyle.drawField(
                graphics, x, y, CONTROL_WIDTH, 14, focused, hovered, accent);
        if (schema.type() == FieldType.COLOR) {
            graphics.fill(x + 2, y + 2, x + 13, y + 12, (int) (long) value.todouble());
        }
        String display = focused ? editBuffer + "_" : displayValue();
        int textX = schema.type() == FieldType.COLOR ? x + 16 : x + 4;
        String visible = Minecraft.getInstance().font.plainSubstrByWidth(
                display,
                x + CONTROL_WIDTH - 3 - textX);
        graphics.drawString(
                Minecraft.getInstance().font,
                visible,
                textX,
                y + 3,
                ComputedEditorTheme.TEXT_PRIMARY,
                false);
    }

    private void renderDropdownOverlay(
            GuiGraphics graphics,
            int x,
            int y,
            int mouseX,
            int mouseY,
            int accent) {
        List<String> options = options();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 4500);
        graphics.fill(
                x,
                y,
                x + CONTROL_WIDTH,
                y + options.size() * 14,
                ComputedEditorTheme.MENU_BACKGROUND);
        graphics.renderOutline(
                x,
                y,
                CONTROL_WIDTH,
                options.size() * 14,
                accent);
        for (int index = 0; index < options.size(); index++) {
            int rowY = y + index * 14;
            boolean hovered = mouseX >= x
                    && mouseX < x + CONTROL_WIDTH
                    && mouseY >= rowY
                    && mouseY < rowY + 14;
            if (hovered) {
                graphics.fill(
                        x + 1,
                        rowY,
                        x + CONTROL_WIDTH - 1,
                        rowY + 14,
                        0x66000000 | (accent & 0x00FFFFFF));
            }
            graphics.drawString(
                    Minecraft.getInstance().font,
                    options.get(index),
                    x + 4,
                    rowY + 3,
                    ComputedEditorTheme.TEXT_PRIMARY,
                    false);
        }
        graphics.pose().popPose();
    }

    private void setSlider(double localX, int controlX) {
        double ratio = Math.max(0, Math.min(1, (localX - controlX - 4) / (CONTROL_WIDTH - 8.0)));
        double number = schema.minimum() + ratio * (schema.maximum() - schema.minimum());
        value = LuaFieldValues.normalize(schema, LuaValue.valueOf(number));
    }

    private void commitBuffer() {
        try {
            LuaValue parsed = switch (schema.type()) {
                case NUMBER -> LuaValue.valueOf(Double.parseDouble(editBuffer.replace(',', '.')));
                case COLOR -> LuaValue.valueOf((double) parseColor(editBuffer));
                default -> LuaValue.valueOf(editBuffer);
            };
            value = LuaFieldValues.normalize(schema, parsed);
        } catch (RuntimeException ignored) {
            editBuffer = displayValue();
        }
    }

    private String displayValue() {
        return switch (schema.type()) {
            case NUMBER -> formatNumber(value.todouble());
            case COLOR -> String.format(Locale.ROOT, "%08X", (long) value.todouble());
            default -> value.tojstring();
        };
    }

    private List<String> options() {
        return schema.type() == FieldType.CHOICE ? schema.choices() : DIRECTIONS;
    }

    private static long parseColor(String text) {
        String normalized = text.strip();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return Long.parseUnsignedLong(normalized, 16) & 0xffffffffL;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
