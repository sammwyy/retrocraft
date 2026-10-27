package com.sammwy.retrocraft.compat;

import java.util.Set;

import com.sammwy.retrocraft.platform.Platform;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ForgeTypes {
    public static <T extends BlockEntity> BlockEntityType<T> create(
            Platform.BlockEntityFactory<T> factory, Block... blocks) {
        return new BlockEntityType<>(factory::create, Set.of(blocks));
    }

    private ForgeTypes() {}
}
