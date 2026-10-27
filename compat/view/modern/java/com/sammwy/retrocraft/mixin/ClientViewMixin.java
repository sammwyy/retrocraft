package com.sammwy.retrocraft.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import com.sammwy.retrocraft.compat.ClientView;

import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
abstract class ClientViewMixin {
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"))
    private void retrocraft$projection(CallbackInfoReturnable<Matrix4f> ci) {
        ClientView.projection.set(ci.getReturnValue());
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void retrocraft$hand(CallbackInfo ci) {
        if (!ClientView.hand) ci.cancel();
    }
}
