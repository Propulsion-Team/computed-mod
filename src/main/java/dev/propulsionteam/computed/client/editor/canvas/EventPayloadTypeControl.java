package dev.propulsionteam.computed.client.editor.canvas;

import dev.propulsionteam.computed.graph.PortDirection;
import dev.propulsionteam.computed.graph.PortSnapshot;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import dev.propulsionteam.computed.lua.node.ConnectionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Compact per-port payload type selector used by Event Sender and Event Receiver nodes. */
final class EventPayloadTypeControl {
    private static final int WIDTH = 48;
    private static final int HEIGHT = 10;

    private final PortSnapshot port;
    private final int directionIndex;

    EventPayloadTypeControl(PortSnapshot port, int directionIndex) {
        this.port = port;
        this.directionIndex = directionIndex;
    }

    String portId() {
        return port.id();
    }

    void render(
            GuiGraphics graphics,
            int nodeX,
            int nodeY,
            int nodeWidth,
            int mouseX,
            int mouseY) {
        int x = nodeX + localX(nodeWidth);
        int y = nodeY + localY();
        boolean hovered = contains(mouseX - nodeX, mouseY - nodeY, nodeWidth);
        ComputedEditorStyle.drawButton(graphics, x, y, WIDTH, HEIGHT, hovered, false);
        graphics.fill(x + 3, y + 3, x + 7, y + 7, color(port.type()));
        graphics.drawString(
                Minecraft.getInstance().font,
                label(port.type()),
                x + 10,
                y + 1,
                ComputedEditorTheme.TEXT_PRIMARY,
                false);
    }

    boolean contains(double localX, double localY, int nodeWidth) {
        int x = localX(nodeWidth);
        int y = localY();
        return localX >= x
                && localX < x + WIDTH
                && localY >= y
                && localY < y + HEIGHT;
    }

    private int localX(int nodeWidth) {
        return port.direction() == PortDirection.INPUT ? nodeWidth - WIDTH - 8 : 8;
    }

    private int localY() {
        return 15 + directionIndex * 12;
    }

    private static String label(ConnectionType type) {
        return switch (type) {
            case NUMBER -> "NUM";
            case BOOLEAN -> "BOOL";
            case STRING -> "TEXT";
            case TABLE -> "TABLE";
            case WIDGET -> "WIDGET";
            case EVENT -> "EVENT";
        };
    }

    private static int color(ConnectionType type) {
        return switch (type) {
            case NUMBER -> 0xFF4E86E8;
            case BOOLEAN -> 0xFF985AD6;
            case STRING -> 0xFFD653B5;
            case EVENT -> 0xFF27C7D9;
            case WIDGET -> 0xFF9BCB45;
            case TABLE -> 0xFF8A9099;
        };
    }
}
