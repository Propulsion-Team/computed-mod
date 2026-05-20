package dev.propulsionteam.computed.customnodes.sources;

import dev.propulsionteam.computed.content.blocks.ComputedGraphExecution;
import dev.propulsionteam.computed.content.blocks.ComputerBlock;
import dev.propulsionteam.computed.content.blocks.ComputerBlockEntity;
import dev.propulsionteam.computed.customnodes.expr.FunctionRegistry;
import dev.propulsionteam.computed.customnodes.expr.Value;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import dev.propulsionteam.computed.content.nodes.vanilla.RelativeFace;

public final class WorldSources {
    private WorldSources() {}

    public static void register(FunctionRegistry reg) {
        // --- Environment ---
        reg.register("light_level", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            int sky   = wc.level.getBrightness(LightLayer.SKY,   wc.pos);
            int block = wc.level.getBrightness(LightLayer.BLOCK, wc.pos);
            return Value.of(Math.max(sky, block));
        });
        reg.register("light_sky", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            return Value.of(wc.level.getBrightness(LightLayer.SKY, wc.pos));
        });
        reg.register("light_block", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            return Value.of(wc.level.getBrightness(LightLayer.BLOCK, wc.pos));
        });
        reg.register("is_raining", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            return Value.ofBool(wc.level.isRaining());
        });
        reg.register("is_thundering", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            return Value.ofBool(wc.level.isThundering());
        });
        reg.register("is_day", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            return Value.ofBool(wc.level.isDay());
        });
        reg.register("biome_temp", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            return Value.of(wc.level.getBiome(wc.pos).value().getBaseTemperature());
        });
        reg.register("biome_downfall", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.ZERO;
            return Value.of(wc.level.getBiome(wc.pos).value().getModifiedClimateSettings().downfall());
        });
        reg.register("biome_name", (args, ctx) -> {
            Context wc = worldCtx(); if (wc == null) return Value.EMPTY_STRING;
            Holder<Biome> holder = wc.level.getBiome(wc.pos);
            String name = holder.unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("unknown");
            return Value.of(name);
        });

        // --- Block ---
        reg.register("block_id", (args, ctx) -> {
            Context wc = neighborCtx(args); if (wc == null) return Value.EMPTY_STRING;
            Block block = wc.level.getBlockState(wc.pos).getBlock();
            return Value.of(BuiltInRegistries.BLOCK.getKey(block).toString());
        });
        reg.register("block_is", (args, ctx) -> {
            if (args.isEmpty()) return Value.ZERO;
            Context wc = neighborCtx(args.subList(1, args.size())); if (wc == null) return Value.ZERO;
            Block block = wc.level.getBlockState(wc.pos).getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            return Value.ofBool(id.equals(args.get(0).asString()));
        });

        // --- Fluid ---
        reg.register("fluid_present", (args, ctx) -> {
            Context wc = neighborCtx(args); if (wc == null) return Value.ZERO;
            return Value.ofBool(!wc.level.getFluidState(wc.pos).isEmpty());
        });
        reg.register("fluid_level", (args, ctx) -> {
            Context wc = neighborCtx(args); if (wc == null) return Value.ZERO;
            FluidState fs = wc.level.getFluidState(wc.pos);
            return Value.of(fs.isEmpty() ? 0 : fs.getAmount());
        });
        reg.register("fluid_type", (args, ctx) -> {
            Context wc = neighborCtx(args); if (wc == null) return Value.EMPTY_STRING;
            FluidState fs = wc.level.getFluidState(wc.pos);
            if (fs.isEmpty()) return Value.EMPTY_STRING;
            if (fs.getType() == Fluids.WATER || fs.getType() == Fluids.FLOWING_WATER) return Value.of("water");
            if (fs.getType() == Fluids.LAVA   || fs.getType() == Fluids.FLOWING_LAVA)  return Value.of("lava");
            return Value.of("unknown");
        });

        // --- Inventory / container ---
        reg.register("container_slots", (args, ctx) -> {
            CapCtx<IItemHandler> cc = itemHandler(args); if (cc == null) return Value.ZERO;
            return Value.of(cc.cap.getSlots());
        });
        reg.register("container_count", (args, ctx) -> {
            CapCtx<IItemHandler> cc = itemHandler(args); if (cc == null) return Value.ZERO;
            int total = 0;
            for (int i = 0; i < cc.cap.getSlots(); i++) total += cc.cap.getStackInSlot(i).getCount();
            return Value.of(total);
        });
        reg.register("container_fill", (args, ctx) -> {
            CapCtx<IItemHandler> cc = itemHandler(args); if (cc == null) return Value.ZERO;
            int slots = cc.cap.getSlots();
            if (slots == 0) return Value.ZERO;
            double used = 0, capacity = 0;
            for (int i = 0; i < slots; i++) {
                var stack = cc.cap.getStackInSlot(i);
                int limit = cc.cap.getSlotLimit(i);
                used     += stack.getCount();
                capacity += limit > 0 ? limit : 64;
            }
            return Value.of(capacity > 0 ? used / capacity : 0.0);
        });
        reg.register("comparator", (args, ctx) -> {
            Context wc = neighborCtx(args); if (wc == null) return Value.ZERO;
            BlockState target = wc.level.getBlockState(wc.pos);
            if (target.hasAnalogOutputSignal()) {
                return Value.of(target.getAnalogOutputSignal(wc.level, wc.pos));
            }
            // Fallback to weak redstone from that face
            Direction face = neighborDir(args);
            return Value.of(face != null ? wc.level.getSignal(wc.pos, face) : 0);
        });
    }

    // --- Internal helpers ---

    private record Context(Level level, BlockPos pos) {}
    private record CapCtx<T>(T cap, Context wc) {}

    /** Context for queries at the computer's own position. */
    private static Context worldCtx() {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return null;
        Level lvl = host.getLevel();
        if (lvl == null || lvl.isClientSide) return null;
        return new Context(lvl, host.getBlockPos());
    }

    /** Context for queries at a neighboring block; face arg is first arg (string). */
    private static Context neighborCtx(List<Value> args) {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return null;
        Level lvl = host.getLevel();
        if (lvl == null || lvl.isClientSide) return null;
        Direction dir = resolveDir(host, args);
        if (dir == null) return null;
        return new Context(lvl, host.getBlockPos().relative(dir));
    }

    /** Resolved world Direction for the face arg on a neighbor query. */
    private static Direction neighborDir(List<Value> args) {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return null;
        return resolveDir(host, args);
    }

    private static Direction resolveDir(ComputerBlockEntity host, List<Value> args) {
        BlockState selfState = host.getBlockState();
        if (!selfState.hasProperty(ComputerBlock.FACING)) return null;
        Direction facing = selfState.getValue(ComputerBlock.FACING);
        String faceName = args.isEmpty() ? "front" : args.get(0).asString();
        RelativeFace rel = RelativeFace.byName(faceName);
        if (rel == null) rel = RelativeFace.FRONT;
        return rel.toWorld(facing);
    }

    private static CapCtx<IItemHandler> itemHandler(List<Value> args) {
        ComputerBlockEntity host = ComputedGraphExecution.hostOrNull();
        if (host == null) return null;
        Level lvl = host.getLevel();
        if (lvl == null || lvl.isClientSide) return null;
        Direction dir = resolveDir(host, args);
        if (dir == null) return null;
        BlockPos neighbor = host.getBlockPos().relative(dir);
        IItemHandler handler = lvl.getCapability(Capabilities.ItemHandler.BLOCK, neighbor, dir.getOpposite());
        if (handler == null) return null;
        return new CapCtx<>(handler, new Context(lvl, neighbor));
    }
}
