package dev.propulsionteam.computed;

import dev.devce.websnodelib.api.FunctionCardNode;
import dev.devce.websnodelib.api.FunctionDefinitionStore;
import dev.devce.websnodelib.api.WGraph;
import dev.propulsionteam.computed.client.ComputerEditorScreen;
import dev.propulsionteam.computed.client.ComputerPeripheralScreen;
import dev.propulsionteam.computed.client.ComputedClientCommands;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import dev.propulsionteam.computed.client.MonitorBlockEntityRenderer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Computed.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Computed.MODID, value = Dist.CLIENT)
public class ComputedClient {
    static {
        ComputerEditorBridge.install((pos, tag) -> {
            WGraph graph = new WGraph();
            FunctionDefinitionStore functions = new FunctionDefinitionStore();
            if (tag.contains("ComputerGraph")) {
                graph.load(tag.getCompound("ComputerGraph"));
                if (tag.contains("ComputerFunctions")) {
                    functions.load(tag.getList("ComputerFunctions", net.minecraft.nbt.Tag.TAG_COMPOUND));
                }
            } else {
                graph.load(tag);
            }
            FunctionCardNode.applyLibraryToInnerGraphs(graph, functions);
            Set<ResourceLocation> unlock = new HashSet<>();
            if (tag.contains(Peripherals.NBT_EDITOR_PERIPHERAL_UNLOCK, Tag.TAG_LIST)) {
                for (Tag t : tag.getList(Peripherals.NBT_EDITOR_PERIPHERAL_UNLOCK, Tag.TAG_STRING)) {
                    unlock.add(ResourceLocation.parse(t.getAsString()));
                }
            }
            Minecraft.getInstance()
                    .setScreen(
                            new ComputerEditorScreen(
                                    pos,
                                    graph,
                                    functions,
                                    unlock,
                                    Peripherals.readPlacedPeripheralHudLines(tag)));
        });
    }

    public ComputedClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(ComputedClient::onRegisterClientCommands);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        dev.devce.websnodelib.internal.WebsNodeCommands.register(event.getDispatcher());
        ComputedClientCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Computed.LOGGER.info("Computed client setup");
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        MenuType<ComputerPeripheralMenu> type =
                (MenuType<ComputerPeripheralMenu>) ComputedRegistries.COMPUTER_PERIPHERAL_MENU.get();
        event.register(type, ComputerPeripheralScreen::new);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ComputedRegistries.MONITOR_BLOCK_ENTITY.get(), MonitorBlockEntityRenderer::new);
    }
}
