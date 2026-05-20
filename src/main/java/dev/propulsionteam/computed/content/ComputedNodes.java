package dev.propulsionteam.computed.content;

import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkReceiverNode;
import dev.propulsionteam.computed.content.nodes.create.CreateRedstoneLinkSenderNode;
import dev.propulsionteam.computed.content.nodes.vanilla.BlockLocationNode;
import dev.propulsionteam.computed.content.nodes.vanilla.BlockPresenceNode;
import dev.propulsionteam.computed.content.nodes.vanilla.BlockRotationNode;
import dev.propulsionteam.computed.content.nodes.vanilla.ComparatorReadNode;
import dev.propulsionteam.computed.content.nodes.vanilla.ConcatenateTextNode;
import dev.propulsionteam.computed.content.nodes.vanilla.IfNode;
import dev.propulsionteam.computed.content.nodes.vanilla.RedstoneInputNode;
import dev.propulsionteam.computed.content.nodes.vanilla.RedstonePortNode;
import dev.propulsionteam.computed.content.nodes.vanilla.SwitchNode;
import dev.propulsionteam.computed.content.nodes.vanilla.WorldTimeNode;
import dev.propulsionteam.computed.content.nodes.widgets.ButtonWidgetNode;
import dev.propulsionteam.computed.content.nodes.widgets.ClockWidgetNode;
import dev.propulsionteam.computed.content.nodes.widgets.ColorSourceNode;
import dev.propulsionteam.computed.content.nodes.widgets.PeripheralNode;
import dev.propulsionteam.computed.content.nodes.widgets.ProgressBarWidgetNode;
import dev.propulsionteam.computed.content.nodes.widgets.SliderWidgetNode;
import dev.propulsionteam.computed.content.nodes.widgets.TextSourceNode;
import dev.propulsionteam.computed.content.nodes.widgets.TextWidgetNode;

public final class ComputedNodes {

    private ComputedNodes() {}

    public static void register() {
        ComputedMenuCategories.registerAll();

        // vanilla
        RedstonePortNode.register();
        RedstoneInputNode.register();
        WorldTimeNode.register();
        ComparatorReadNode.register();
        BlockPresenceNode.register();
        BlockLocationNode.register();
        ConcatenateTextNode.register();
        BlockRotationNode.register();

        // logic > comparison (under websnodelib's existing Logic category)
        IfNode.register();
        SwitchNode.register();

        // sources
        TextSourceNode.register();
        ColorSourceNode.register();

        // peripherals
        PeripheralNode.register();

        // widgets
        TextWidgetNode.register();
        ClockWidgetNode.register();
        ButtonWidgetNode.register();
        SliderWidgetNode.register();
        ProgressBarWidgetNode.register();

        // create — types always registered (for save compat); menu entries only when Create is loaded
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            ComputedMenuCategories.registerCreateCategories();
            CreateRedstoneLinkSenderNode.register();
            CreateRedstoneLinkReceiverNode.register();
        } else {
            CreateRedstoneLinkSenderNode.registerType();
            CreateRedstoneLinkReceiverNode.registerType();
        }
    }
}
