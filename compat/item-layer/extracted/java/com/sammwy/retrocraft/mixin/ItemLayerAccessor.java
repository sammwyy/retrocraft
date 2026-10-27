package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.item.ItemStackRenderState;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerAccessor {
    @Invoker("applyTransform")
    void retrocraft$applyTransform(PoseStack.Pose pose);
}
