package com.sammwy.retrocraft.compat;

import org.joml.Matrix4f;

public final class ClientMatrices {
    public static Matrix4f view() {
        return com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
    }

    private ClientMatrices() {}
}
