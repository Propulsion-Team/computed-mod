package dev.propulsionteam.computed.internal.node.api.elements;

import dev.propulsionteam.computed.internal.node.api.WElement;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorStyle;
import dev.propulsionteam.computed.internal.node.client.editor.ComputedEditorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Small row with label + clickable slot that opens the editor item picker (when a {@link dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen} is active).
 */
public class WItemPickSlot extends WElement {
    private final String label;
    private ItemStack stack = ItemStack.EMPTY;

    public WItemPickSlot(String label) {
        this.label = label;
        this.width = 100;
        this.height = 22;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        graphics.drawString(mc.font, label, x, y - 1, ComputedEditorTheme.TEXT_SECONDARY, false);
        int sx = x;
        int sy = y + mc.font.lineHeight - 2;
        boolean hovered = mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
        ComputedEditorStyle.drawField(graphics, sx, sy, 18, 18, false, hovered);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, sx + 1, sy + 1);
        } else {
            graphics.drawString(mc.font, "∅", sx + 5, sy + 5, ComputedEditorTheme.TEXT_DISABLED, false);
        }
    }

    @Override
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        int slotY = mc.font.lineHeight - 2;
        if (mouseX >= 0 && mouseX < 18 && mouseY >= slotY && mouseY < slotY + 18) {
            dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen.requestItemPick(this::setStack);
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        if (!stack.isEmpty()) {
            tag.putString("Item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("Item")) {
            ResourceLocation id = ResourceLocation.parse(tag.getString("Item"));
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            } else {
                stack = ItemStack.EMPTY;
            }
        } else {
            stack = ItemStack.EMPTY;
        }
    }
}
