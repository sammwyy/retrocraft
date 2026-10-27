package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;

/** Minimal geometry sink retained when Minecraft removes immediate buffer sources. */
public interface GeometryBuffers {
    VertexConsumer getBuffer(RenderType type);
}
