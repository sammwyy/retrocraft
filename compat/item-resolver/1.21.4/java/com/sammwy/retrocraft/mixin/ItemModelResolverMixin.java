package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sammwy.retrocraft.compat.ThumbnailItem;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void retrocraft$capture(
            ItemStackRenderState state,
            ItemStack stack,
            ItemDisplayContext context,
            boolean left,
            Level level,
            LivingEntity entity,
            int seed,
            CallbackInfo ci) {
        ((ThumbnailItem) state).retrocraft$stack(stack.copy());
    }
}
