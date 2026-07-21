package dev.propulsionteam.computed.api.node.client;

import dev.propulsionteam.computed.api.node.NodeProperty;
import dev.propulsionteam.computed.api.node.NodePropertyBag;
import dev.propulsionteam.computed.api.node.NodeType;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;

/** Client editor services supplied to a custom node presentation for one render pass. */
public interface NodePresentationContext {
    UUID nodeId();

    NodeType<?> nodeType();

    NodePropertyBag properties();

    GuiGraphics graphics();

    int x();

    int y();

    int width();

    int height();

    int mouseX();

    int mouseY();

    float partialTick();

    <T> void setProperty(NodeProperty<T> property, T value);

    /** Draws the standard controls generated from the node type's property definitions. */
    void renderGenericPropertyControls();
}
