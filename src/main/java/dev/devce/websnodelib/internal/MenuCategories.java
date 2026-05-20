package dev.devce.websnodelib.internal;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MenuCategories {
    public static final ResourceLocation MATH = WsId.of("menu_math");
    public static final ResourceLocation MATH_BINARY = WsId.of("menu_math_binary");
    public static final ResourceLocation MATH_UNARY = WsId.of("menu_math_unary");
    public static final ResourceLocation MATH_TRIG = WsId.of("menu_math_trig");
    public static final ResourceLocation SOURCES = WsId.of("menu_sources");
    public static final ResourceLocation IO = WsId.of("menu_io");
    public static final ResourceLocation VISUALS = WsId.of("menu_visuals");
    public static final ResourceLocation ORGANIZATION = WsId.of("menu_organization");
    public static final ResourceLocation LOGIC = WsId.of("menu_logic");
    public static final ResourceLocation LOGIC_BINARY = WsId.of("menu_logic_binary");
    public static final ResourceLocation LOGIC_UNARY = WsId.of("menu_logic_unary");
    public static final ResourceLocation LOGIC_COMPARISON = WsId.of("menu_logic_comparison");
    public static final ResourceLocation LOGIC_MEMORY = WsId.of("menu_logic_memory");

    /** Sentinel: nodes whose MENU is this are hidden from the add menu. */
    public static final ResourceLocation HIDDEN = WsId.of("menu_hidden_sentinel");

    private MenuCategories() {}

    public static void registerAll() {
        NodeMenuRegistry.registerCategory(MATH, Component.literal("Math"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(MATH_BINARY, Component.literal("Binary"), MATH);
        NodeMenuRegistry.registerCategory(MATH_UNARY, Component.literal("Unary & rounding"), MATH);
        NodeMenuRegistry.registerCategory(MATH_TRIG, Component.literal("Trig"), MATH);
        NodeMenuRegistry.registerCategory(SOURCES, Component.literal("Sources"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(IO, Component.literal("I/O"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(VISUALS, Component.literal("Visuals"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(ORGANIZATION, Component.literal("Organization"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(LOGIC, Component.literal("Logic"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(LOGIC_BINARY, Component.literal("Binary"), LOGIC);
        NodeMenuRegistry.registerCategory(LOGIC_UNARY, Component.literal("Unary"), LOGIC);
        NodeMenuRegistry.registerCategory(LOGIC_COMPARISON, Component.literal("Comparison"), LOGIC);
        NodeMenuRegistry.registerCategory(LOGIC_MEMORY, Component.literal("Memory"), LOGIC);
    }
}
