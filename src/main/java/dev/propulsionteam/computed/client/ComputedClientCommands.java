package dev.propulsionteam.computed.client;

import com.mojang.brigadier.CommandDispatcher;
import dev.propulsionteam.computed.customnodes.ComputedCustomNodes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ComputedClientCommands {
    private ComputedClientCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("computed")
                .then(Commands.literal("reload")
                        .executes(context -> {
                            var summary = ComputedCustomNodes.reload();
                            context.getSource()
                                    .sendSuccess(
                                            () -> Component.literal("Custom nodes reloaded: loaded="
                                                    + summary.loaded() + ", skipped=" + summary.skipped()
                                                    + ", warnings=" + summary.warnings() + ", errors="
                                                    + summary.errors()),
                                            false);
                            return summary.errors() == 0 ? 1 : 0;
                        })));
    }
}
