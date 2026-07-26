package dev.propulsionteam.computed.lua.endpoint;

import java.util.List;

public interface BuiltinEndpointHost {
    double worldTime();

    default double[] position() {
        return new double[] {0, 0, 0};
    }

    default double[] rotation() {
        return new double[] {0, 0, 0};
    }

    default int redstoneInput(String face) {
        return 0;
    }

    default int comparatorInput(String face) {
        return 0;
    }

    default boolean blockPresent(String face) {
        return false;
    }

    default void redstoneOutput(String face, int level) {}

    default void showWidgets(String target, List<BuiltinWidget> widgets) {}

    void runCommand(String command);
}
