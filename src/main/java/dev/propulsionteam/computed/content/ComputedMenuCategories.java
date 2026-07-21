package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.Computed;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ComputedMenuCategories {
    public static final ResourceLocation VANILLA =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_vanilla");
    public static final ResourceLocation CREATE =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_create");
    public static final ResourceLocation CREATE_REDSTONE_LINK =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_create_redstone_link");
    public static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_widgets");
    public static final ResourceLocation PERIPHERALS =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_peripherals");
    public static final ResourceLocation CREATIVE =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_creative");

    private ComputedMenuCategories() {}

    public static void registerAll() {
        NodeMenuRegistry.registerCategory(VANILLA, Component.literal("Vanilla"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(WIDGETS, Component.literal("Widgets"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(PERIPHERALS, Component.literal("Peripherals"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(CREATIVE, Component.literal("Creative"), NodeMenuRegistry.ROOT);
    }

    public static void registerCreateCategories() {
        NodeMenuRegistry.registerCategory(CREATE, Component.literal("Create"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(CREATE_REDSTONE_LINK, Component.literal("Redstone Link"), CREATE);
    }
}
