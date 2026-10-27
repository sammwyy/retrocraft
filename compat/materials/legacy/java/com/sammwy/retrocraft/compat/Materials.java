package com.sammwy.retrocraft.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class Materials {
    public static RenderType solid() {
        return RenderType.solid();
    }

    public static RenderType entitySolid(ResourceLocation texture) {
        return RenderType.entitySolid(texture);
    }

    public static RenderType cutout(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    private Materials() {}
}
