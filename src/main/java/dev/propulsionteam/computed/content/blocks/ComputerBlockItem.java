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
import dev.propulsionteam.computed.internal.node.ProgramBridge;

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
        CompoundTag program = tag.contains(ProgramBridge.PROGRAM_TAG, Tag.TAG_COMPOUND)
                ? tag.getCompound(ProgramBridge.PROGRAM_TAG)
                : tag;
        int nodes;
        int funcs;
        if (program.contains("formatVersion") && program.contains("graph", Tag.TAG_COMPOUND)) {
            nodes = program.getCompound("graph").getList("nodes", Tag.TAG_COMPOUND).size();
            funcs = program.getList("functions", Tag.TAG_COMPOUND).size();
        } else {
            nodes = program.getCompound("ComputerGraph").getList("nodes", Tag.TAG_COMPOUND).size();
            funcs = program.getList("ComputerFunctions", Tag.TAG_COMPOUND).size();
        }
        if (nodes == 0 && funcs == 0) {
            return;
        }
        tooltip.add(Component.translatable("item.computed.computer.stored", nodes, funcs)
                .withStyle(ChatFormatting.GRAY));
    }
}
