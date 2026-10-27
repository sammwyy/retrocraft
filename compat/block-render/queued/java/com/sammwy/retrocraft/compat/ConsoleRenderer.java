package com.sammwy.retrocraft.compat;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public interface ConsoleRenderer<T extends BlockEntity>
        extends BlockEntityRenderer<T, ConsoleRenderer.State> {
    final class State extends BlockEntityRenderState {
        List<FrameBuffers.Command> frame = List.of();
    }

    void draw(
            T entity,
            float tick,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay);

    @Override
    default State createRenderState() {
        return new State();
    }

    @Override
    default boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    default void extractRenderState(
            T entity,
            State state,
            float tick,
            Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tick, camera, breaking);
        var buffers = new FrameBuffers();
        draw(entity, tick, new PoseStack(), buffers, state.lightCoords, OverlayTexture.NO_OVERLAY);
        state.frame = buffers.snapshot();
    }

    @Override
    default void submit(
            State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        FrameBuffers.submit(state.frame, poses, collector);
    }
}
