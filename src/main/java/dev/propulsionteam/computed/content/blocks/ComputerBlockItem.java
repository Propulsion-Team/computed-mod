package dev.propulsionteam.computed.content.blocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class ComputerBlockItem extends BlockItem {

    public ComputerBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        CompoundTag program = tag.contains(ComputerBlockEntity.PROGRAM_TAG, Tag.TAG_COMPOUND)
                ? tag.getCompound(ComputerBlockEntity.PROGRAM_TAG)
                : tag;
        int nodes = program.getCompound("graph").getList("nodes", Tag.TAG_COMPOUND).size();
        int definitions = program.getList("library", Tag.TAG_COMPOUND).size();
        if (nodes == 0 && definitions == 0) {
            return;
        }
        tooltip.add(Component.translatable("item.computed.computer.stored", nodes, definitions)
                .withStyle(ChatFormatting.GRAY));
    }
}
