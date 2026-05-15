package dev.propulsionteam.computed.content;

import dev.devce.websnodelib.api.NodeMenuRegistry;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkReceiverNode;
import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkSenderNode;
import dev.propulsionteam.computed.content.nodes.simulated.TypewriterReceiverNode;
import dev.propulsionteam.computed.content.nodes.vanilla.RedstonePortNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ComputedNodes {
    private static final ResourceLocation MENU_VANILLA =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_vanilla");
    private static final ResourceLocation MENU_CREATE =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_create");
    private static final ResourceLocation MENU_CREATE_REDSTONE_LINK =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_create_redstone_link");
    private static final ResourceLocation MENU_SIMULATED =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "menu_simulated");

    private ComputedNodes() {}

    public static void register() {
        NodeMenuRegistry.registerCategory(
                MENU_VANILLA, Component.literal("Vanilla"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(MENU_CREATE, Component.literal("Create"), NodeMenuRegistry.ROOT);
        NodeMenuRegistry.registerCategory(
                MENU_CREATE_REDSTONE_LINK, Component.literal("Redstone Link"), MENU_CREATE);

        NodeMenuRegistry.registerCategory(MENU_SIMULATED, Component.literal("Simulated"), NodeMenuRegistry.ROOT);

        NodeRegistry.register(CreateRedstoneLinkSenderNode.TYPE_ID, CreateRedstoneLinkSenderNode::new);
        NodeMenuRegistry.addNodeEntry(
                MENU_CREATE_REDSTONE_LINK, CreateRedstoneLinkSenderNode.TYPE_ID, Component.literal("Sender"));

        NodeRegistry.register(CreateRedstoneLinkReceiverNode.TYPE_ID, CreateRedstoneLinkReceiverNode::new);
        NodeMenuRegistry.addNodeEntry(
                MENU_CREATE_REDSTONE_LINK, CreateRedstoneLinkReceiverNode.TYPE_ID, Component.literal("Receiver"));

        NodeRegistry.register(TypewriterReceiverNode.TYPE_ID, TypewriterReceiverNode::new);
        NodeMenuRegistry.addNodeEntry(MENU_SIMULATED, TypewriterReceiverNode.TYPE_ID, Component.literal("Typewriter RX"));

        NodeRegistry.register(RedstonePortNode.TYPE_ID, RedstonePortNode::new);
        NodeMenuRegistry.addNodeEntry(
                MENU_VANILLA, RedstonePortNode.TYPE_ID, Component.literal("Redstone"));
    }
}
