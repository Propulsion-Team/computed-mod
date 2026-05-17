package dev.propulsionteam.computed.content.monitors;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.state.BlockBehaviour.propertiesCodec;

public class MonitorBlock extends BaseEntityBlock {
    public static final MapCodec<MonitorBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(propertiesCodec()).apply(instance, MonitorBlock::new));

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty ORIENTATION = DirectionProperty.create("orientation",
        Direction.UP, Direction.DOWN, Direction.NORTH);
    public static final EnumProperty<MonitorEdgeState> STATE = EnumProperty.create("state", MonitorEdgeState.class);

    public MonitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(ORIENTATION, Direction.NORTH)
            .setValue(STATE, MonitorEdgeState.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ORIENTATION, STATE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        float pitch = context.getPlayer() == null ? 0 : context.getPlayer().getXRot();
        Direction orientation;
        if (pitch > 66.5f) {
            orientation = Direction.UP;
        } else if (pitch < -66.5f) {
            orientation = Direction.DOWN;
        } else {
            orientation = Direction.NORTH;
        }

        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(ORIENTATION, orientation);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, moved);
            return;
        }

        BlockEntity tile = level.getBlockEntity(pos);
        super.onRemove(state, level, pos, newState, moved);
        if (tile instanceof MonitorBlockEntity monitor) monitor.destroy();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof MonitorBlockEntity monitor && !level.isClientSide) {
            if (placer == null) {
                monitor.updateNeighborsDeferred();
            } else {
                monitor.expand();
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }
}
