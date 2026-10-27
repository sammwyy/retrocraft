package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ConsoleRenderer<T extends BlockEntity> extends BlockEntityRenderer<T> {
    @Override
    default boolean shouldRenderOffScreen(T entity) {
        return true;
    }

    void draw(
            T entity,
            float tick,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay);

    @Override
    default void render(
            T entity,
            float tick,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        draw(entity, tick, poses, buffers, light, overlay);
    }
}
