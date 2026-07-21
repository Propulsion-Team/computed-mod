package dev.propulsionteam.computed.internal.node.api.elements;

import dev.propulsionteam.computed.internal.node.api.WElement;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Two item frequency slots (picture only), tinted red and blue, drawn side by side and centered in this
 * element's width.
 */
public class WFrequencySlotPair extends WElement {

    private static final int SLOT = 22;
    private static final int GAP = 8;
    private static final int RED_BG = 0x99CC3030;
    private static final int BLUE_BG = 0x993030CC;
    private static final int RED_EDGE = 0xFFFF6666;
    private static final int BLUE_EDGE = 0xFF8888FF;

    private ItemStack red = ItemStack.EMPTY;
    private ItemStack blue = ItemStack.EMPTY;

    public WFrequencySlotPair() {
        this.width = 160;
        this.height = SLOT;
        this.padding = 2;
        this.margin = 2;
    }

    public ItemStack getRed() {
        return red;
    }

    public ItemStack getBlue() {
        return blue;
    }

    private int slotsTotalWidth() {
        return SLOT + GAP + SLOT;
    }

    private int slotOriginX() {
        return padding + Math.max(0, (width - slotsTotalWidth()) / 2);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        int ox = x + slotOriginX();
        drawSlot(graphics, ox, y, red, true, mouseX, mouseY);
        drawSlot(graphics, ox + SLOT + GAP, y, blue, false, mouseX, mouseY);
    }

    private void drawSlot(
            GuiGraphics graphics,
            int ox,
            int oy,
            ItemStack stack,
            boolean isRed,
            int mouseX,
            int mouseY) {
        boolean hovered = mouseX >= ox && mouseX < ox + SLOT && mouseY >= oy && mouseY < oy + SLOT;
        int fill = isRed ? RED_BG : BLUE_BG;
        int edge = isRed ? RED_EDGE : BLUE_EDGE;
        graphics.fill(ox, oy, ox + SLOT, oy + SLOT, hovered ? (fill | 0xFF000000) : fill);
        graphics.renderOutline(
                ox,
                oy,
                SLOT,
                SLOT,
                hovered ? ComputedEditorTheme.TEXT_HEADER : edge);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, ox + 2, oy + 2);
        }
    }

    @Override
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int ox = slotOriginX();
        Minecraft mc = Minecraft.getInstance();
        if (mouseX >= ox && mouseX < ox + SLOT && mouseY >= 0 && mouseY < SLOT) {
            playClick(mc);
            dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen.requestItemPick(this::setRed);
            return true;
        }
        int bx = ox + SLOT + GAP;
        if (mouseX >= bx && mouseX < bx + SLOT && mouseY >= 0 && mouseY < SLOT) {
            playClick(mc);
            dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen.requestItemPick(this::setBlue);
            return true;
        }
        return false;
    }

    private static void playClick(Minecraft mc) {
        // No-op in shared code: keep item slot pair server-loadable during graph deserialization.
    }

    private void setRed(ItemStack st) {
        red = st.isEmpty() ? ItemStack.EMPTY : st.copyWithCount(1);
    }

    private void setBlue(ItemStack st) {
        blue = st.isEmpty() ? ItemStack.EMPTY : st.copyWithCount(1);
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        if (!red.isEmpty()) {
            tag.putString("Red", BuiltInRegistries.ITEM.getKey(red.getItem()).toString());
        }
        if (!blue.isEmpty()) {
            tag.putString("Blue", BuiltInRegistries.ITEM.getKey(blue.getItem()).toString());
        }
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        red = ItemStack.EMPTY;
        blue = ItemStack.EMPTY;
        if (tag.contains("Red")) {
            ResourceLocation id = ResourceLocation.parse(tag.getString("Red"));
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                red = new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
        }
        if (tag.contains("Blue")) {
            ResourceLocation id = ResourceLocation.parse(tag.getString("Blue"));
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                blue = new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
        }
    }
}
