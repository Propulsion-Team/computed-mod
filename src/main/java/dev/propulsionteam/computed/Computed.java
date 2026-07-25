package dev.propulsionteam.computed;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import dev.propulsionteam.computed.content.ComputedRegistries;
import dev.propulsionteam.computed.lua.endpoint.BuiltinEndpoints;
import dev.propulsionteam.computed.network.ComputedNetworking;

@Mod(Computed.MODID)
public class Computed {
    public static final String MODID = "computed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Computed(IEventBus modEventBus, ModContainer modContainer) {
        BuiltinEndpoints.register();
        ComputedRegistries.register(modEventBus);
        ComputedNetworking.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
