package com.sammwy.retrocraft.compat;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.client.render.ScreenProjection;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public final class ProjectionCapture {
    public static void capture(
            MultiBufferSource buffers,
            ResourceLocation texture,
            Matrix4f local,
            float x0,
            float y0,
            float x1,
            float y1,
            float z) {
        var poses = new PoseStack();
        poses.mulPose(local);
        ((FrameBuffers) buffers)
                .record(
                        poses,
                        (base, collector) ->
                                ScreenProjection.capture(
                                        texture, base.last().pose(), x0, y0, x1, y1, z));
    }

    private ProjectionCapture() {}
}
