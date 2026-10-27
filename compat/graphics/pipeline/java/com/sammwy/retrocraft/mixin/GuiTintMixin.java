package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

import com.sammwy.retrocraft.compat.ClientGraphics;

import net.minecraft.client.gui.GuiGraphics;

/** Carry Retrocraft's animated tint into queued GUI states. */
@Mixin(GuiGraphics.class)
abstract class GuiTintMixin {
    @ModifyVariable(
            method = "submitColoredRectangle",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 4)
    private int retrocraft$fill(int color) {
        return ClientGraphics.tint((GuiGraphics) (Object) this, color);
    }

    @ModifyVariable(
            method = "submitColoredRectangle",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Integer retrocraft$gradient(Integer color) {
        return color == null ? null : ClientGraphics.tint((GuiGraphics) (Object) this, color);
    }

    @ModifyVariable(
            method =
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2)
    private int retrocraft$text(int color) {
        return ClientGraphics.tint((GuiGraphics) (Object) this, color);
    }
}
