package com.sammwy.retrocraft.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.ResourceLocation;

public final class Materials {
    public static RenderType solid() {
        return RenderTypes.solidMovingBlock();
    }

    public static RenderType entitySolid(ResourceLocation texture) {
        return RenderTypes.entitySolid(texture);
    }

    public static RenderType cutout(ResourceLocation texture) {
        return RenderTypes.entityCutoutNoCull(texture);
    }

    private Materials() {}
}
