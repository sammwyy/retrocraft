package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

public final class ClientGraphics {
    private static final java.util.Map<GuiGraphics, Integer> COLORS = new java.util.WeakHashMap<>();

    public static int tint(GuiGraphics graphics, int color) {
        int tint = COLORS.getOrDefault(graphics, -1);
        return ((color >>> 24) * (tint >>> 24) / 255 << 24)
                | ((color >> 16 & 255) * (tint >> 16 & 255) / 255 << 16)
                | ((color >> 8 & 255) * (tint >> 8 & 255) / 255 << 8)
                | ((color & 255) * (tint & 255) / 255);
    }

    public static void color(GuiGraphics graphics, float r, float g, float b, float a) {
        int color =
                Math.round(a * 255) << 24
                        | Math.round(r * 255) << 16
                        | Math.round(g * 255) << 8
                        | Math.round(b * 255);
        if (color == -1) COLORS.remove(graphics);
        else COLORS.put(graphics, color);
    }

    public static void blit(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            float u,
            float v,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                u,
                v,
                width,
                height,
                sourceWidth,
                sourceHeight,
                textureWidth,
                textureHeight,
                tint(graphics, -1));
    }

    public static void pixel(NativeImage image, int x, int y, int abgr) {
        image.setPixel(x, y, (abgr & 0xff00ff00) | ((abgr & 0xff) << 16) | ((abgr >>> 16) & 0xff));
    }

    private ClientGraphics() {}
}
