package com.sammwy.retrocraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sammwy.retrocraft.client.render.CameraRig;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "setup", at = @At("TAIL"))
    private void retrocraft$rig(
            net.minecraft.world.level.Level area,
            Entity entity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci) {
        double[] pose = CameraRig.pose(position(), yRot(), xRot());
        if (pose == null) return;
        setRotation((float) pose[3], (float) pose[4]);
        setPosition(new Vec3(pose[0], pose[1], pose[2]));
    }
}
