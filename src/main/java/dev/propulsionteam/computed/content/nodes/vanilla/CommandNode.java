package dev.propulsionteam.computed.content.nodes.vanilla;

import dev.propulsionteam.computed.internal.node.api.NodeMenuRegistry;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.api.WPin;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.ComputedMenuCategories;
import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class CommandNode extends WNode {
    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Computed.MODID, "command");

    public CommandNode(int x, int y) {
        super(TYPE_ID, "Command", x, y);
        addInput("Tick", 0xFFFF5555);
        addInput("Command", WPin.DataType.STRING, 0xFF00FF88);
        updateLayout();

        setEvaluator(n -> {
            if (n.getInputs().size() < 2) {
                return;
            }
            // Safety gate: only run when a tick source is explicitly wired and active.
            if (!n.getInputs().get(0).isConnected() || n.getInputs().get(0).getValue() <= 0.5) {
                return;
            }
            String command = n.getInputs().get(1).getStringValue();
            runCommand(command);
        });
    }

    private static int runCommand(String commandText) {
        if (commandText == null || commandText.isBlank()) {
            return 0;
        }
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) {
            return 0;
        }
        if (!(host.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return 0;
        }
        String command = commandText.startsWith("/") ? commandText.substring(1) : commandText;
        if (command.isBlank()) {
            return 0;
        }
        Vec3 center = Vec3.atCenterOf(host.getBlockPos());
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level)
                .withPosition(center)
                .withPermission(4)
                .withSuppressedOutput();
        try {
            server.getCommands().performPrefixedCommand(source, command);
            return 1;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    public static void register() {
        NodeRegistry.register(TYPE_ID, CommandNode::new);
        NodeMenuRegistry.addNodeEntry(ComputedMenuCategories.CREATIVE, TYPE_ID, Component.literal("Command"));
    }
}
