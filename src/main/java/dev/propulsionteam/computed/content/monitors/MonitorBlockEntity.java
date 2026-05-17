package dev.propulsionteam.computed.content.monitors;

import dev.propulsionteam.computed.content.ComputedRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorBlockEntity extends BlockEntity {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorBlockEntity.class);

    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 6;

    private static final String NBT_X = "XIndex";
    private static final String NBT_Y = "YIndex";
    private static final String NBT_WIDTH = "Width";
    private static final String NBT_HEIGHT = "Height";

    private int width = 1;
    private int height = 1;
    private int xIndex = 0;
    private int yIndex = 0;

    private boolean needsUpdate = false;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ComputedRegistries.MONITOR_BLOCK_ENTITY.get(), pos, state);
    }

    void destroy() {
        if (level != null && !level.isClientSide) contractNeighbours();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt(NBT_X, xIndex);
        tag.putInt(NBT_Y, yIndex);
        tag.putInt(NBT_WIDTH, width);
        tag.putInt(NBT_HEIGHT, height);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        xIndex = tag.getInt(NBT_X);
        yIndex = tag.getInt(NBT_Y);
        width = Math.max(1, tag.getInt(NBT_WIDTH));
        height = Math.max(1, tag.getInt(NBT_HEIGHT));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        tag.putInt(NBT_X, xIndex);
        tag.putInt(NBT_Y, yIndex);
        tag.putInt(NBT_WIDTH, width);
        tag.putInt(NBT_HEIGHT, height);
        return tag;
    }

    public void blockTick() {
        if (needsUpdate) {
            needsUpdate = false;
            expand();
        }
    }

    void updateNeighborsDeferred() {
        needsUpdate = true;
    }

    // region Sizing and placement

    public Direction getDirection() {
        BlockState state = getBlockState();
        return state.hasProperty(MonitorBlock.FACING) ? state.getValue(MonitorBlock.FACING) : Direction.NORTH;
    }

    public Direction getOrientation() {
        BlockState state = getBlockState();
        return state.hasProperty(MonitorBlock.ORIENTATION) ? state.getValue(MonitorBlock.ORIENTATION) : Direction.NORTH;
    }

    public Direction getFront() {
        Direction orientation = getOrientation();
        return orientation == Direction.NORTH ? getDirection() : orientation;
    }

    public Direction getRight() {
        return getDirection().getCounterClockWise();
    }

    public Direction getDown() {
        Direction orientation = getOrientation();
        if (orientation == Direction.NORTH) return Direction.UP;
        return orientation == Direction.DOWN ? getDirection() : getDirection().getOpposite();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getXIndex() {
        return xIndex;
    }

    public int getYIndex() {
        return yIndex;
    }

    boolean isCompatible(MonitorBlockEntity other) {
        return getOrientation() == other.getOrientation() && getDirection() == other.getDirection();
    }

    private MonitorState getLoadedMonitor(int x, int y) {
        if (x == xIndex && y == yIndex) return MonitorState.present(this);
        BlockPos pos = toWorldPos(x, y);

        var world = getLevel();
        if (world == null || !world.isLoaded(pos)) return MonitorState.UNLOADED;

        var tile = world.getBlockEntity(pos);
        if (!(tile instanceof MonitorBlockEntity monitor)) return MonitorState.MISSING;

        return isCompatible(monitor) ? MonitorState.present(monitor) : MonitorState.MISSING;
    }

    @Nullable
    private MonitorBlockEntity getOrigin() {
        return getLoadedMonitor(0, 0).getMonitor();
    }

    BlockPos toWorldPos(int x, int y) {
        if (xIndex == x && yIndex == y) return getBlockPos();
        return getBlockPos().relative(getRight(), -xIndex + x).relative(getDown(), -yIndex + y);
    }

    private void updateBlockState() {
        if (level == null) return;
        level.setBlock(getBlockPos(), getBlockState()
            .setValue(MonitorBlock.STATE, MonitorEdgeState.fromConnections(
                yIndex < height - 1, yIndex > 0,
                xIndex > 0, xIndex < width - 1)), Block.UPDATE_CLIENTS);
    }

    void resize(int width, int height) {
        xIndex = 0;
        yIndex = 0;
        this.width = width;
        this.height = height;

        BlockPos pos = getBlockPos();
        Direction down = getDown(), right = getRight();
        for (var x = 0; x < width; x++) {
            for (var y = 0; y < height; y++) {
                var other = getLevel().getBlockEntity(pos.relative(right, x).relative(down, y));
                if (!(other instanceof MonitorBlockEntity monitor) || !isCompatible(monitor)) continue;

                monitor.xIndex = x;
                monitor.yIndex = y;
                monitor.width = width;
                monitor.height = height;
                monitor.needsUpdate = false;
                monitor.updateBlockState();
                monitor.setChanged();
                if (level != null) {
                    level.sendBlockUpdated(monitor.getBlockPos(), monitor.getBlockState(), monitor.getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    void expand() {
        var monitor = getOrigin();
        if (monitor != null && monitor.xIndex == 0 && monitor.yIndex == 0) new Expander(monitor).expand();
    }

    private void contractNeighbours() {
        if (width == 1 && height == 1) return;

        BlockPos pos = getBlockPos();
        Direction down = getDown(), right = getRight();
        BlockPos origin = toWorldPos(0, 0);

        MonitorBlockEntity toLeft = null, toAbove = null, toRight = null, toBelow = null;
        if (xIndex > 0) toLeft = tryResizeAt(pos.relative(right, -xIndex), xIndex, 1);
        if (yIndex > 0) toAbove = tryResizeAt(origin, width, yIndex);
        if (xIndex < width - 1) toRight = tryResizeAt(pos.relative(right, 1), width - xIndex - 1, 1);
        if (yIndex < height - 1) {
            toBelow = tryResizeAt(origin.relative(down, yIndex + 1), width, height - yIndex - 1);
        }

        if (toLeft != null) toLeft.expand();
        if (toAbove != null) toAbove.expand();
        if (toRight != null) toRight.expand();
        if (toBelow != null) toBelow.expand();
    }

    @Nullable
    private MonitorBlockEntity tryResizeAt(BlockPos pos, int width, int height) {
        var tile = getLevel().getBlockEntity(pos);
        if (tile instanceof MonitorBlockEntity monitor && isCompatible(monitor)) {
            monitor.resize(width, height);
            return monitor;
        }
        return null;
    }
    // endregion
}
