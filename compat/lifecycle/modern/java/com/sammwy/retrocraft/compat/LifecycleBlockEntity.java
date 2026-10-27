package com.sammwy.retrocraft.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class LifecycleBlockEntity extends BlockEntity {
    protected LifecycleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState previous) {
        if (getLevel() != null)
            ((LifecycleBlock) getBlockState().getBlock()).removed(getLevel(), getBlockPos());
        super.preRemoveSideEffects(pos, previous);
    }
}
