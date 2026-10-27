package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.util.RandomSource;

public final class ModelGeometry {
    public static void draw(
            BlockStateModel model,
            DockedConsoleBlockEntity console,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            boolean ambient) {
        var parts =
                new java.util.ArrayList<
                        net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
        model.collectParts(RandomSource.create(), parts);
        var snapshot = java.util.List.copyOf(parts);
        ((FrameBuffers) buffers)
                .record(
                        poses,
                        (base, collector) ->
                                collector.submitBlockModel(
                                        base,
                                        Materials.solid(),
                                        snapshot,
                                        new int[] {-1},
                                        light,
                                        overlay,
                                        0));
    }

    public static BlockStateModel missing() {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().missingModel();
    }

    private ModelGeometry() {}
}
