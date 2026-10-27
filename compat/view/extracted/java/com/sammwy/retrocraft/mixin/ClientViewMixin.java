package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sammwy.retrocraft.compat.ClientView;

@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
abstract class ClientViewMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void retrocraft$hand(CallbackInfo ci) {
        if (!ClientView.hand) ci.cancel();
    }
}
