package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;

public final class TextureImages {
    public static DynamicTexture create(NativeImage image) {
        var texture = new DynamicTexture(() -> "Retrocraft screen", image);
        texture.setFilter(false, false);
        return texture;
    }

    public static void beginBlend() {
        /* The render pipeline owns blending. */
    }

    public static void endBlend() {
        /* The render pipeline owns blending. */
    }

    private TextureImages() {}
}
