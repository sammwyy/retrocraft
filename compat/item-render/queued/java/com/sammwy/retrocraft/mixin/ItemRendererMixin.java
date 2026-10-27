package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.client.render.Thumbnails;
import com.sammwy.retrocraft.compat.FrameBuffers;
import com.sammwy.retrocraft.compat.ThumbnailItem;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Render one LCD after all model layers, using the same item transform as the first layer. */
@Mixin(ItemStackRenderState.class)
abstract class ItemRendererMixin implements ThumbnailItem {
    @Shadow ItemDisplayContext displayContext;

    @Shadow private ItemStackRenderState.LayerRenderState[] layers;

    @Unique private ItemStack retrocraft$stack = ItemStack.EMPTY;

    @Override
    public void retrocraft$stack(ItemStack stack) {
        retrocraft$stack = stack;
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void retrocraft$clear(CallbackInfo ci) {
        retrocraft$stack = ItemStack.EMPTY;
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void retrocraft$screen(
            PoseStack matrices,
            SubmitNodeCollector collector,
            int light,
            int overlay,
            int outline,
            CallbackInfo ci) {
        if (retrocraft$stack.isEmpty() || ((ItemStackRenderState) (Object) this).isEmpty()) return;
        matrices.pushPose();
        com.sammwy.retrocraft.compat.LayerTransforms.apply(
                layers[0], matrices.last(), displayContext.leftHand());
        matrices.translate(-0.5, -0.5, -0.5);
        var vertices = new FrameBuffers();
        Thumbnails.render(retrocraft$stack, matrices, vertices);
        FrameBuffers.submit(vertices.snapshot(), new PoseStack(), collector);
        matrices.popPose();
    }
}
