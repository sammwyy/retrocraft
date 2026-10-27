package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;

import net.minecraft.client.renderer.texture.DynamicTexture;

public final class TextureImages {
    private static final class PixelTexture extends DynamicTexture {
        PixelTexture(NativeImage image) {
            super(() -> "Retrocraft screen", image);
            sampler =
                    RenderSystem.getSamplerCache()
                            .getSampler(
                                    AddressMode.REPEAT,
                                    AddressMode.REPEAT,
                                    FilterMode.NEAREST,
                                    FilterMode.NEAREST,
                                    false);
        }
    }

    public static DynamicTexture create(NativeImage image) {
        return new PixelTexture(image);
    }

    public static void beginBlend() {}

    public static void endBlend() {}

    private TextureImages() {}
}
