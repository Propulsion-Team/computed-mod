package dev.propulsionteam.computed.internal.node.internal;

import com.mojang.brigadier.CommandDispatcher;
import dev.propulsionteam.computed.internal.node.api.NodeRegistry;
import dev.propulsionteam.computed.internal.node.api.WGraph;
import dev.propulsionteam.computed.internal.node.api.WNode;
import dev.propulsionteam.computed.internal.node.client.ui.WNodeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;

/** https://github.com/webyep-art/webs_node_lib (MIT, webyep). */
public final class ComputedNodeCommands {
    private ComputedNodeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("webu")
            .then(Commands.literal("node_editor")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> {
                        WGraph demoGraph = new WGraph();
                        WNode mathNode = NodeRegistry.createNode(ResourceLocation.fromNamespaceAndPath("computed", "math_add"), 100, 100);
                        WNode displayNode = NodeRegistry.createNode(ResourceLocation.fromNamespaceAndPath("computed", "display"), 300, 150);
                        if (mathNode != null) {
                            demoGraph.addNode(mathNode);
                        }
                        if (displayNode != null) {
                            demoGraph.addNode(displayNode);
                        }
                        Minecraft.getInstance().setScreen(new WNodeScreen(demoGraph));
                    });
                    return 1;
                })
            )
        );
    }
}
