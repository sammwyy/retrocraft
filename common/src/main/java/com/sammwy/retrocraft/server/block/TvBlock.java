package com.sammwy.retrocraft.server.block;

import com.mojang.serialization.MapCodec;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.compat.LifecycleBlock;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A television a docked console can be bound to. The screen is a dynamic texture drawn by the
 * client renderer, so one block entity covers the whole set even when the cabinet spans several
 * blocks visually.
 */
public final class TvBlock extends LifecycleBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /**
     * Cabinet footprint (extra blocks sideways and upwards) and screen glass rectangle in model
     * units, front = north.
     */
    public enum Size {
        SMALL("tv_small", 0, 0, 1, 15, 13, 3, 13, 2.5f, 13.5f, 3, 11.25f, 2.7f),
        MEDIUM("tv_medium", 1, 1, -8, 24, 24, 2, 14, -5, 21, 3.25f, 22.75f, 1.8f),
        LARGE("tv_large", 1, 1, -16, 32, 32, 0, 16, -7, 23, 4.5f, 27, -.2f);
        public final String id;
        public final int sideways, upwards;
        public final float x0, x1, height, z0, z1, screenX0, screenX1, screenY0, screenY1, screenZ;

        Size(
                String id,
                int sideways,
                int upwards,
                float x0,
                float x1,
                float height,
                float z0,
                float z1,
                float sx0,
                float sx1,
                float sy0,
                float sy1,
                float sz) {
            this.id = id;
            this.sideways = sideways;
            this.upwards = upwards;
            this.x0 = x0;
            this.x1 = x1;
            this.height = height;
            this.z0 = z0;
            this.z1 = z1;
            screenX0 = sx0;
            screenX1 = sx1;
            screenY0 = sy0;
            screenY1 = sy1;
            screenZ = sz;
        }
    }

    public final Size size;

    public TvBlock(Size size, Properties settings) {
        super(settings);
        this.size = size;
        registerDefaultState(
                getStateDefinition()
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(POWERED, false));
    }

    public BlockEntityType<TvBlockEntity> entityType() {
        return ConsoleRegistry.televisionEntityType(size.id);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected MapCodec<? extends LifecycleBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TvBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide()
                ? null
                : createTickerHelper(type, entityType(), TvBlockEntity::tick);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // Mirrored like the model: viewer's left is +x when facing north.
        double x0 = 16 - size.x1, x1 = 16 - size.x0;
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(16 - x1, 0, 16 - size.z1, 16 - x0, size.height, 16 - size.z0);
            case EAST -> Block.box(16 - size.z1, 0, x0, 16 - size.z0, size.height, x1);
            case WEST -> Block.box(size.z0, 0, 16 - x1, size.z1, size.height, 16 - x0);
            default -> Block.box(x0, 0, size.z0, x1, size.height, size.z1);
        };
    }

    /** Multi-block cabinets need the neighbouring columns they overlap to be free. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        for (int side = -size.sideways; side <= size.sideways; side++)
            for (int up = 0; up <= size.upwards; up++) {
                BlockPos target = pos.relative(facing.getClockWise(), side).above(up);
                if (!target.equals(pos) && !world.getBlockState(target).canBeReplaced())
                    return false;
            }
        return true;
    }

    @Override
    public void removed(Level world, BlockPos pos) {
        if (!world.isClientSide()
                && world.getBlockEntity(pos) instanceof TvBlockEntity tv
                && Retrocraft.instances() != null) Retrocraft.instances().televisionRemoved(tv);
    }
}
