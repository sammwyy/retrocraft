package com.sammwy.retrocraft.platform;

import com.sammwy.retrocraft.compat.ConsoleRenderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

@FunctionalInterface
public interface RendererFactory<T extends BlockEntity> {
    ConsoleRenderer<T> create(BlockEntityRendererProvider.Context context);
}
