package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Horizontally tiles same-sized UI textures (typically 16×16) then optional text, for inline shortcut
 * hints inside nodes.
 */
public class WIconStrip extends WElement {
    private static final int TEX = 16;

    private final List<ResourceLocation> icons;
    private final String suffix;
    private final int suffixColor;
    private final int iconDraw;
    private final int iconGap;

    public WIconStrip(List<ResourceLocation> icons, String suffix, int suffixColor, int iconDraw) {
        this(icons, suffix, suffixColor, iconDraw, 2);
    }

    public WIconStrip(List<ResourceLocation> icons, String suffix, int suffixColor, int iconDraw, int iconGap) {
        this.icons = List.copyOf(icons);
        this.suffix = suffix;
        this.suffixColor = suffixColor;
        this.iconDraw = iconDraw;
        this.iconGap = iconGap;
        int suffixWidth = measureTextWidth(suffix);
        int lineHeight = measureLineHeight();
        int w = 0;
        for (int i = 0; i < this.icons.size(); i++) {
            w += iconDraw + (i < this.icons.size() - 1 ? iconGap : 0);
        }
        if (!suffix.isEmpty()) {
            w += 3 + suffixWidth;
        }
        this.width = w;
        this.height = Math.max(iconDraw, lineHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int cx = x;
        for (int i = 0; i < icons.size(); i++) {
            ResourceLocation icon = icons.get(i);
            graphics.pose().pushPose();
            graphics.pose().translate(cx, y, 0);
            float s = iconDraw / (float) TEX;
            graphics.pose().scale(s, s, 1.0f);
            graphics.blit(icon, 0, 0, 0, 0, TEX, TEX, TEX, TEX);
            graphics.pose().popPose();
            cx += iconDraw;
            if (i < icons.size() - 1) {
                cx += iconGap;
            }
        }
        if (!suffix.isEmpty()) {
            cx += 3;
            int ty = y + (iconDraw - font.lineHeight) / 2 + 1;
            graphics.drawString(font, suffix, cx, ty, suffixColor, false);
        }
    }

    private static int measureTextWidth(String text) {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            return Math.max(8, text.length() * 6);
        }
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? Math.max(8, text.length() * 6) : mc.font.width(text);
    }

    private static int measureLineHeight() {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            return 9;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? 9 : mc.font.lineHeight;
    }
}
