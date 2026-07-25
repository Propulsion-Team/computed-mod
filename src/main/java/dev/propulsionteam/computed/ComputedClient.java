package dev.propulsionteam.computed;

import dev.propulsionteam.computed.client.ComputerEditorScreen;
import dev.propulsionteam.computed.client.ComputerPeripheralScreen;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.content.Peripherals;
import dev.propulsionteam.computed.menu.ComputerPeripheralMenu;
import net.minecraft.client.Minecraft;
import dev.propulsionteam.computed.persistence.ProgramV3Codec;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import dev.propulsionteam.computed.client.MonitorBlockEntityRenderer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Computed.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Computed.MODID, value = Dist.CLIENT)
public class ComputedClient {
    static {
        ComputerEditorBridge.install((pos, serverRevision, tag) -> {
            var program = ProgramV3Codec.decode(tag, pos.toShortString(), Computed.LOGGER::warn).program();
            Minecraft.getInstance()
                    .setScreen(
                            new ComputerEditorScreen(
                                    pos,
                                    program,
                                    serverRevision));
        }, (pos, accepted, serverRevision, editorRevision, message) -> {
            if (Minecraft.getInstance().screen instanceof ComputerEditorScreen screen
                    && screen.editsComputer(pos)) {
                screen.onServerSaveResult(accepted, serverRevision, editorRevision, message);
            }
        });
    }

    public ComputedClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
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
