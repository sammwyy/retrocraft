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
        var line =
                net.minecraft.locale.Language.getInstance()
                        .getVisualOrder(net.minecraft.network.chat.Component.literal(text));
        ((FrameBuffers) buffers)
                .record(
                        poses,
                        (base, collector) ->
                                collector.submitText(
                                        base,
                                        x,
                                        y,
                                        line,
                                        false,
                                        Font.DisplayMode.NORMAL,
                                        0xf000f0,
                                        color,
                                        background,
                                        0));
    }

    private WorldText() {}
}
