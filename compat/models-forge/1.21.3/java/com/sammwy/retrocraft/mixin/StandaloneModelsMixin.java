package com.sammwy.retrocraft.mixin;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sammwy.retrocraft.compat.ExtraModels;

import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;

@Mixin(ModelBakery.class)
abstract class StandaloneModelsMixin {
    @Shadow @Final @Mutable private Map<ModelResourceLocation, UnbakedModel> topModels;
    @Shadow @Final private Map<ResourceLocation, UnbakedModel> unbakedModels;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void retrocraft$models(CallbackInfo ci) {
        topModels = new HashMap<>(topModels);
        for (var id : ExtraModels.SOURCE.get()) {
            var model = unbakedModels.get(id);
            if (model != null) topModels.put(new ModelResourceLocation(id, "standalone"), model);
        }
    }
}
