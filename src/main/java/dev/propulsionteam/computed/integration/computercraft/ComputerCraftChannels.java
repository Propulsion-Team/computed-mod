package dev.propulsionteam.computed.integration.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class ComputerCraftChannels {
    private static final Map<ComputerBlockEntity, Store> STORES = new WeakHashMap<>();

    private ComputerCraftChannels() {}

    static synchronized Store store(ComputerBlockEntity computer) {
        return STORES.computeIfAbsent(computer, ignored -> new Store());
    }

    static synchronized void remove(ComputerBlockEntity computer) {
        STORES.remove(computer);
    }

    static final class Store {
        private final Map<String, Object> inputs = new LinkedHashMap<>();
        private final Map<String, Object> outputs = new LinkedHashMap<>();
        private final Set<IComputerAccess> attached = new LinkedHashSet<>();

        synchronized void attach(IComputerAccess computer) {
            attached.add(computer);
        }

        synchronized void detach(IComputerAccess computer) {
            attached.remove(computer);
        }

        synchronized void write(String channel, Object value) throws LuaException {
            inputs.put(requireChannel(channel), ComputerCraftValueCodec.normalize(value));
        }

        synchronized Object input(String channel) {
            return inputs.get(requireChannel(channel));
        }

        synchronized Object output(String channel) {
            return outputs.get(requireChannel(channel));
        }

        synchronized void publish(String channel, Object value) throws LuaException {
            String checked = requireChannel(channel);
            Object normalized = ComputerCraftValueCodec.normalize(value);
            if (java.util.Objects.deepEquals(outputs.put(checked, normalized), normalized)) {
                return;
            }
            List<IComputerAccess> listeners = new ArrayList<>(attached);
            for (IComputerAccess listener : listeners) {
                listener.queueEvent("computed_output_changed", checked, normalized);
            }
        }

        synchronized List<String> channels() {
            Set<String> names = new LinkedHashSet<>(inputs.keySet());
            names.addAll(outputs.keySet());
            return List.copyOf(names);
        }

        private static String requireChannel(String channel) {
            String checked = channel == null ? "" : channel.strip();
            if (checked.isEmpty() || checked.length() > 64) {
                throw new IllegalArgumentException("Channel names must contain 1 to 64 characters");
            }
            return checked;
        }
    }
}
