package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.DynamicTexture;

public final class TextureImages {
    public static DynamicTexture create(NativeImage image) {
        var texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        return texture;
    }

    public static void beginBlend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public static void endBlend() {
        RenderSystem.disableBlend();
    }

    private TextureImages() {}
}
