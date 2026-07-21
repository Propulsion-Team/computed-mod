package dev.devce.websnodelib.internal.nodes.visuals;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WElement;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WLabel;
import dev.devce.websnodelib.internal.MenuCategories;
import dev.devce.websnodelib.internal.WsId;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class RgbPreviewNode extends WNode {
    public static final ResourceLocation TYPE_ID = WsId.of("rgb_preview");
    public static final ResourceLocation MENU = MenuCategories.VISUALS;
    public static final Component LABEL = Component.literal("RGB Preview");

    public RgbPreviewNode(int x, int y) {
        super(TYPE_ID, "RGB Preview", x, y);
        addInput("R", 0xFFFF0000);
        addInput("G", 0xFF00FF00);
        addInput("B", 0xFF0000FF);
        addElement(new WLabel("Color Result:"));
        WNode self = this;
        addElement(new WElement() {
            {
                this.width = 60;
                this.height = 30;
            }

            @Override
            public void render(GuiGraphics g, int x, int y, int mx, int my, float pt) {
                int r = (int) Mth.clamp(self.getInputs().get(0).getValue(), 0, 255);
                int g1 = (int) Mth.clamp(self.getInputs().get(1).getValue(), 0, 255);
                int b = (int) Mth.clamp(self.getInputs().get(2).getValue(), 0, 255);
                g.fill(x, y, x + width, y + height, 0xFF000000 | (r << 16) | (g1 << 8) | b);
                g.renderOutline(x, y, width, height, 0xFFFFFFFF);
            }
        });
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, RgbPreviewNode::new);
        NodeMenuRegistry.addNodeEntry(MENU, TYPE_ID, LABEL);
    }
}
