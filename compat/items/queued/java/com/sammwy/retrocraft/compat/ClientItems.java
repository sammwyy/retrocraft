package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ClientItems {
    public static void draw(
            ItemStack stack,
            Level level,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            int seed) {
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        Minecraft.getInstance()
                .getItemModelResolver()
                .updateForTopItem(state, stack, ItemDisplayContext.FIXED, level, null, seed);
        ((FrameBuffers) buffers)
                .record(
                        poses,
                        (base, collector) -> state.submit(base, collector, light, overlay, 0));
    }

    private ClientItems() {}
}
