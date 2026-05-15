package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

public class WCheckbox extends WElement {
    private boolean checked;
    private String label;
    private Runnable onToggle;

    public WCheckbox(String label) {
        this.label = label;
        this.width = 100;
        this.height = 12;
    }

    /** Called after the checked state changes (box click). */
    public void setOnToggle(Runnable onToggle) {
        this.onToggle = onToggle;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        
        // Box
        graphics.fill(x, y + 1, x + 10, y + 11, 0xFF1A1A1A);
        graphics.renderOutline(x, y + 1, 10, 10, hovered ? 0xFF00FF88 : 0xFF444444);
        
        if (checked) {
            graphics.fill(x + 2, y + 3, x + 8, y + 9, 0xFF00FF88);
        }
        
        // Label
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, label, x + 15, y + 2, 0xFFCCCCCC);
    }

    @Override
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 0 && mouseX < getWidth() && mouseY >= 0 && mouseY < getHeight()) {
            checked = !checked;
            if (onToggle != null) {
                onToggle.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putBoolean("Checked", checked);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("Checked")) {
            checked = tag.getBoolean("Checked");
        }
    }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
