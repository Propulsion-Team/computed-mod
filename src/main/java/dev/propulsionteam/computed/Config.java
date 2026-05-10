package dev.propulsionteam.computed;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
