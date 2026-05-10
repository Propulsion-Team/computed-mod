package dev.propulsionteam.computed;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import dev.devce.websnodelib.WebsNodeLib;
import dev.propulsionteam.computed.content.ComputedNodes;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.network.ComputedNetworking;

@Mod(Computed.MODID)
public class Computed {
    public static final String MODID = "computed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Computed(IEventBus modEventBus, ModContainer modContainer) {
        WebsNodeLib.bootstrap();
        ComputedNodes.register();

        ComputedRegistries.register(modEventBus);
        ComputedNetworking.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Computed common setup");
    }
}
