package com.sammwy.retrocraft.compat;

import org.joml.Matrix4f;

public final class ClientView {
    public static Matrix4f projection() {
        return projection;
    }

    public static void renderHand(boolean visible) {
        hand = visible;
    }

    public static boolean hand = true;
    public static Matrix4f projection = new Matrix4f();

    public static Matrix4f viewRotation = new Matrix4f();

    private ClientView() {}
}
