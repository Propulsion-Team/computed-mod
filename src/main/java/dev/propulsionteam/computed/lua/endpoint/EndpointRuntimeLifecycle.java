package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EndpointRuntimeLifecycle {
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private EndpointRuntimeLifecycle() {}

    public static void register(Listener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void tick(UUID computerId, Object host) {
        LISTENERS.forEach(listener -> listener.tick(computerId, host));
    }

    public static void unload(UUID computerId, Object host) {
        LISTENERS.forEach(listener -> listener.unload(computerId, host));
    }

    public interface Listener {
        default void tick(UUID computerId, Object host) {}

        default void unload(UUID computerId, Object host) {}
    }
}
