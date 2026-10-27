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
        Minecraft.getInstance()
                .getItemRenderer()
                .renderStatic(
                        stack,
                        ItemDisplayContext.FIXED,
                        light,
                        overlay,
                        poses,
                        buffers,
                        level,
                        seed);
    }

    private ClientItems() {}
}
