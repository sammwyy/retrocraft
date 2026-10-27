package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sammwy.retrocraft.client.render.CameraRig;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Inject(method = "update", at = @At("TAIL"))
    private void retrocraft$rig(CallbackInfo ci) {
        double[] pose = CameraRig.pose(position(), yRot(), xRot());
        if (pose == null) return;
        setRotation((float) pose[3], (float) pose[4]);
        setPosition(new Vec3(pose[0], pose[1], pose[2]));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void retrocraft$projection(
            net.minecraft.client.renderer.state.level.CameraRenderState state,
            float tick,
            CallbackInfo ci) {
        com.sammwy.retrocraft.compat.ClientView.projection.set(state.projectionMatrix);
        com.sammwy.retrocraft.compat.ClientView.viewRotation.set(state.viewRotationMatrix);
    }
}
