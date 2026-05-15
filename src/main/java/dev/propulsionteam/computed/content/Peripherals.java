package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkReceiverNode;
import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkSenderNode;
import dev.propulsionteam.computed.content.nodes.simulated.TypewriterReceiverNode;
import dev.propulsionteam.computed.integration.SimulatedLinkedTypewriterBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class Peripherals {
    private Peripherals() {}

    public static final ResourceLocation REDSTONE_LINK_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "redstone_link");

    private static boolean isKnownPeripheralItemId(ResourceLocation id) {
        return REDSTONE_LINK_ITEM_ID.equals(id)
                || (Computed.MODID.equals(id.getNamespace()) && "redstone_emitter".equals(id.getPath()));
    }

    /**
     * Which hardware token (inventory item id or in-world linked {@linkplain ComputedTags.Blocks#PLACED_PERIPHERAL_LINK_TARGETS block} id)
     * is required for this graph node type, or {@code null} if not gated.
     */
    @Nullable
    public static ResourceLocation peripheralItemRequiredForNodeType(ResourceLocation nodeTypeId) {
        if (CreateRedstoneLinkSenderNode.TYPE_ID.equals(nodeTypeId)
                || CreateRedstoneLinkReceiverNode.TYPE_ID.equals(nodeTypeId)) {
            return REDSTONE_LINK_ITEM_ID;
        }
        if (TypewriterReceiverNode.TYPE_ID.equals(nodeTypeId)) {
            return SimulatedLinkedTypewriterBridge.LINKED_TYPEWRITER_ID;
        }
        if (!isPeripheralNodeType(nodeTypeId)) {
            return null;
        }
        if (BuiltInRegistries.ITEM.containsKey(nodeTypeId)) {
            return nodeTypeId;
        }
        if (BuiltInRegistries.BLOCK.containsKey(nodeTypeId)) {
            return nodeTypeId;
        }
        return null;
    }

    public static Predicate<ResourceLocation> hardwareMissingPredicate(Set<ResourceLocation> equippedItemIds) {
        Set<ResourceLocation> eq = Set.copyOf(equippedItemIds);
        return nodeTypeId -> {
            ResourceLocation req = peripheralItemRequiredForNodeType(nodeTypeId);
            return req != null && !eq.contains(req);
        };
    }

    public static boolean graphNbtUsesMissingPeripheral(CompoundTag graphBody, Set<ResourceLocation> equippedItemIds) {
        if (graphBody == null || !graphBody.contains("nodes", Tag.TAG_LIST)) {
            return false;
        }
        ListTag nodes = graphBody.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag n = nodes.getCompound(i);
            if (n.contains("typeId", Tag.TAG_STRING)) {
                ResourceLocation tid = ResourceLocation.parse(n.getString("typeId"));
                ResourceLocation req = peripheralItemRequiredForNodeType(tid);
                if (req != null && !equippedItemIds.contains(req)) {
                    return true;
                }
            }
            if (n.contains("inner", Tag.TAG_COMPOUND)
                    && graphNbtUsesMissingPeripheral(n.getCompound("inner"), equippedItemIds)) {
                return true;
            }
        }
        return false;
    }

    public static final String NBT_EDITOR_PERIPHERAL_UNLOCK = "ComputedPeripheralUnlock";
    /** Client-only list for the node editor HUD (positions and instance ids). Stripped before applying graph saves. */
    public static final String NBT_EDITOR_PLACED_PERIPHERALS = "ComputedPlacedPeripherals";

    public static void writePeripheralUnlockTag(Container computer, CompoundTag out) {
        ListTag list = new ListTag();
        for (int i = 0; i < computer.getContainerSize(); i++) {
            ItemStack st = computer.getItem(i);
            if (!st.isEmpty() && isPeripheral(st)) {
                list.add(StringTag.valueOf(nodeTypeFor(st).toString()));
            }
        }
        if (computer instanceof ComputerBlockEntity be) {
            for (PlacedPeripheralLink link : be.placedPeripheralLinksView()) {
                if (be.isPlacedPeripheralLinkActive(link)) {
                    list.add(StringTag.valueOf(link.kind().toString()));
                }
            }
            writePlacedPeripheralEditorHud(be, out);
        }
        out.put(NBT_EDITOR_PERIPHERAL_UNLOCK, list);
    }

    private static void writePlacedPeripheralEditorHud(ComputerBlockEntity be, CompoundTag out) {
        ListTag hud = new ListTag();
        for (PlacedPeripheralLink link : be.placedPeripheralLinksView()) {
            if (!be.isPlacedPeripheralLinkActive(link)) {
                continue;
            }
            CompoundTag row = new CompoundTag();
            row.putString("Kind", link.kind().toString());
            row.putInt("Id", link.instanceId());
            row.putInt("X", link.pos().getX());
            row.putInt("Y", link.pos().getY());
            row.putInt("Z", link.pos().getZ());
            hud.add(row);
        }
        if (!hud.isEmpty()) {
            out.put(NBT_EDITOR_PLACED_PERIPHERALS, hud);
        }
    }

    public static List<net.minecraft.network.chat.Component> readPlacedPeripheralHudLines(CompoundTag editorBundle) {
        if (!editorBundle.contains(NBT_EDITOR_PLACED_PERIPHERALS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag hud = editorBundle.getList(NBT_EDITOR_PLACED_PERIPHERALS, Tag.TAG_COMPOUND);
        List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
        for (int i = 0; i < hud.size(); i++) {
            CompoundTag row = hud.getCompound(i);
            if (!row.contains("Kind", Tag.TAG_STRING) || !row.contains("Id", Tag.TAG_INT)) {
                continue;
            }
            lines.add(
                    net.minecraft.network.chat.Component.translatable(
                            "gui.computed.placed_peripheral_line",
                            row.getInt("Id"),
                            row.getString("Kind"),
                            row.getInt("X"),
                            row.getInt("Y"),
                            row.getInt("Z")));
        }
        return lines;
    }

    public static void stripEditorOnlyTags(CompoundTag tag) {
        tag.remove(NBT_EDITOR_PERIPHERAL_UNLOCK);
        tag.remove(NBT_EDITOR_PLACED_PERIPHERALS);
    }

    /**
     * Blocks that may be registered with a computer via the connector / bind flow. Uses the datapack tag first, then
     * known integration ids when tags miss entries (e.g. load order).
     */
    public static boolean isPlacedPeripheralLinkTargetState(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(ComputedTags.Blocks.PLACED_PERIPHERAL_LINK_TARGETS)) {
            return true;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return SimulatedLinkedTypewriterBridge.LINKED_TYPEWRITER_ID.equals(blockId);
    }

    public static boolean isBindableHeldPeripheral(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof PeripheralConnectorItem) {
            return false;
        }
        if (!stack.is(ComputedTags.Items.PERIPHERAL)) {
            return false;
        }
        if (stack.is(ComputedTags.Items.BINDABLE_PLACED_PERIPHERALS)) {
            return true;
        }
        if (stack.getItem() instanceof BlockItem bi) {
            return isPlacedPeripheralLinkTargetState(bi.getBlock().defaultBlockState());
        }
        return false;
    }

    public static boolean isPeripheral(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ComputedTags.Items.PERIPHERAL)) {
            return true;
        }
        if (stack.is(ComputedTags.Items.PERIPHERALS)) {
            return true;
        }
        return isKnownPeripheralItemId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static ResourceLocation nodeTypeFor(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static boolean isPeripheralNodeType(ResourceLocation typeId) {
        if (CreateRedstoneLinkSenderNode.TYPE_ID.equals(typeId) || CreateRedstoneLinkReceiverNode.TYPE_ID.equals(typeId)) {
            return true;
        }
        if (TypewriterReceiverNode.TYPE_ID.equals(typeId)) {
            return true;
        }
        if (isKnownPeripheralItemId(typeId)) {
            return true;
        }
        if (!BuiltInRegistries.ITEM.containsKey(typeId)) {
            if (BuiltInRegistries.BLOCK.containsKey(typeId)) {
                Block b = BuiltInRegistries.BLOCK.get(typeId);
                return isPlacedPeripheralLinkTargetState(b.defaultBlockState());
            }
            return false;
        }
        return BuiltInRegistries.ITEM.get(typeId).getDefaultInstance().is(ComputedTags.Items.PERIPHERAL)
                || BuiltInRegistries.ITEM.get(typeId).getDefaultInstance().is(ComputedTags.Items.PERIPHERALS);
    }

    public static boolean mayPlaceInComputer(Container container, int slot, ItemStack stack) {
        if (stack.isEmpty() || !isPeripheral(stack)) {
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (i == slot) {
                continue;
            }
            ItemStack other = container.getItem(i);
            if (!other.isEmpty() && ItemStack.isSameItemSameComponents(stack, other)) {
                return false;
            }
        }
        return true;
    }
}
