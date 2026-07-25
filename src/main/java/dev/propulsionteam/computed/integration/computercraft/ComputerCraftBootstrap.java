package dev.propulsionteam.computed.integration.computercraft;

import dev.propulsionteam.computed.Computed;
import java.lang.reflect.InvocationTargetException;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

public final class ComputerCraftBootstrap {
    private ComputerCraftBootstrap() {}

    public static boolean available() {
        return ModList.get().isLoaded("computercraft");
    }

    public static void register(IEventBus modBus) {
        if (!available()) {
            return;
        }
        try {
            Class.forName(
                            "dev.propulsionteam.computed.integration.computercraft.ComputerCraftIntegration",
                            true,
                            ComputerCraftBootstrap.class.getClassLoader())
                    .getMethod("register", IEventBus.class)
                    .invoke(null, modBus);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("CC:Tweaked API bridge could not be loaded", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            Computed.LOGGER.error("CC:Tweaked API bridge failed during registration", cause);
            throw new IllegalStateException("CC:Tweaked API bridge failed during registration", cause);
        }
    }
}
