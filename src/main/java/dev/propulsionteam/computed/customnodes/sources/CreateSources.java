package dev.propulsionteam.computed.customnodes.sources;

import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.content.nodes.vanilla.RelativeFace;
import dev.propulsionteam.computed.customnodes.expr.FunctionRegistry;
import dev.propulsionteam.computed.customnodes.expr.Value;
import dev.propulsionteam.computed.integration.CreateKineticBridge;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class CreateSources {
    private CreateSources() {}

    public static void register(FunctionRegistry reg) {
        reg.register("create_kinetic", (args, ctx) -> {
            BlockPos pos = neighborPos(args); if (pos == null) return Value.ZERO;
            Level lvl = hostLevel(); if (lvl == null) return Value.ZERO;
            return Value.ofBool(CreateKineticBridge.isKinetic(lvl, pos));
        });
        reg.register("create_speed", (args, ctx) -> {
            BlockPos pos = neighborPos(args); if (pos == null) return Value.ZERO;
            Level lvl = hostLevel(); if (lvl == null) return Value.ZERO;
            return Value.of(CreateKineticBridge.getSpeed(lvl, pos));
        });
        reg.register("create_stress", (args, ctx) -> {
            BlockPos pos = neighborPos(args); if (pos == null) return Value.ZERO;
            Level lvl = hostLevel(); if (lvl == null) return Value.ZERO;
            return Value.of(CreateKineticBridge.getStress(lvl, pos));
        });
        reg.register("create_capacity", (args, ctx) -> {
            BlockPos pos = neighborPos(args); if (pos == null) return Value.ZERO;
            Level lvl = hostLevel(); if (lvl == null) return Value.ZERO;
            return Value.of(CreateKineticBridge.getCapacity(lvl, pos));
        });
    }

    private static Level hostLevel() {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return null;
        Level lvl = host.getLevel();
        return (lvl == null || lvl.isClientSide) ? null : lvl;
    }

    private static BlockPos neighborPos(List<Value> args) {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return null;
        BlockState selfState = host.getBlockState();
        if (!selfState.hasProperty(ComputerBlock.FACING)) return null;
        Direction facing = selfState.getValue(ComputerBlock.FACING);
        String faceName = args.isEmpty() ? "front" : args.get(0).asString();
        RelativeFace rel = RelativeFace.byName(faceName);
        if (rel == null) rel = RelativeFace.FRONT;
        return host.getBlockPos().relative(rel.toWorld(facing));
    }
}
