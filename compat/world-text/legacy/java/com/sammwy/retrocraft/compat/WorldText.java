package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;

public final class WorldText {
    public static void draw(
            Font font,
            String text,
            float x,
            float y,
            int color,
            PoseStack poses,
            MultiBufferSource buffers,
            int background) {
        font.drawInBatch(
                text,
                x,
                y,
                color,
                false,
                poses.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                background,
                0xf000f0);
    }

    private WorldText() {}
}
