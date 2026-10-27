package com.sammwy.retrocraft.compat;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;

public final class ClientView {
    public static Matrix4f projection() {
        return com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
    }

    public static void renderHand(boolean visible) {
        Minecraft.getInstance().gameRenderer.setRenderHand(visible);
    }

    private ClientView() {}
}
