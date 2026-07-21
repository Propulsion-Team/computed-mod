package dev.propulsionteam.computed;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import dev.propulsionteam.computed.internal.node.ComputedNodeSystem;
import dev.propulsionteam.computed.content.ComputedNodes;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.network.ComputedNetworking;

@Mod(Computed.MODID)
public class Computed {
    public static final String MODID = "computed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Computed(IEventBus modEventBus, ModContainer modContainer) {
        ComputedNodeSystem.bootstrap();
        ComputedNodes.register();

        ComputedRegistries.register(modEventBus);
        ComputedNetworking.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ComputedNodeSystem.finalizeRegistrations();
            LOGGER.info("Computed node registry frozen with {} public node types",
                    dev.propulsionteam.computed.api.node.ComputedNodeApi.nodeTypes().size());
        });
    }
}
