package dev.propulsionteam.computed.lua.endpoint;

import java.util.Map;
import java.util.UUID;

public record BuiltinWidget(
        UUID id,
        String type,
        int x,
        int y,
        int width,
        int height,
        int color,
        Map<String, Object> properties) {

    public BuiltinWidget {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
