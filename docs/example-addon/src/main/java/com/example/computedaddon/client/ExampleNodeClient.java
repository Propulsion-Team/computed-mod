package com.example.computedaddon.client;

import com.example.computedaddon.ExampleNodes;
import dev.propulsionteam.computed.api.node.client.ComputedNodeClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Invoke only from the addon's client initialization path. */
public final class ExampleNodeClient {
    private ExampleNodeClient() {}

    public static void registerPresentations() {
        ComputedNodeClientApi.registerPresentation(ExampleNodes.ACCUMULATOR, context -> {
            context.renderGenericPropertyControls();
            context.graphics().drawString(
                    Minecraft.getInstance().font,
                    Component.literal("Keeps its total between steps"),
                    context.x() + 6,
                    context.y() + context.height() - 14,
                    0xFFB8C4D8,
                    false);
        });
    }
}
