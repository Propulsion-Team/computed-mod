package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ComputedTags {
    public static final class Items {
        public static final TagKey<Item> PERIPHERAL =
                TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Computed.MODID, "peripheral"));

        /** Legacy tag; datapacks usually pull in {@link #PERIPHERAL} via tag json. */
        public static final TagKey<Item> PERIPHERALS =
                TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Computed.MODID, "peripherals"));

        /**
         * Items that may be pre-bound to a computer before placement when right-clicking a computer block.
         * Use with {@link net.minecraft.world.item.BlockItem}s whose blocks are not yet in
         * {@link ComputedTags.Blocks#PLACED_PERIPHERAL_LINK_TARGETS}, or for non-standard item types.
         */
        public static final TagKey<Item> BINDABLE_PLACED_PERIPHERALS =
                TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Computed.MODID, "bindable_placed_peripherals"));
    }

    public static final class Blocks {
        /**
         * Peripheral-capable blocks: right-click with a peripheral-tagged bindable item can complete linking instead of
         * normal block GUIs where applicable.
         */
        public static final TagKey<Block> PERIPHERAL =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Computed.MODID, "peripheral"));

        /**
         * Blocks that can be linked to a computer (in-world) or pre-bound on their {@link net.minecraft.world.item.BlockItem}
         * before placement. Pack authors add mod thrusters, sensors, etc. here.
         */
        public static final TagKey<Block> PLACED_PERIPHERAL_LINK_TARGETS =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Computed.MODID, "placed_peripheral_link_targets"));
    }

    private ComputedTags() {}
}
