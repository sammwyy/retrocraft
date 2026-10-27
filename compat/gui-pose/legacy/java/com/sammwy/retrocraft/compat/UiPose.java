package com.sammwy.retrocraft.compat;

import net.minecraft.client.gui.GuiGraphics;

public record UiPose(GuiGraphics graphics) {
    public static UiPose of(GuiGraphics graphics) {
        return new UiPose(graphics);
    }

    public void pushPose() {
        graphics.pose().pushPose();
    }

    public void popPose() {
        graphics.pose().popPose();
    }

    public void translate(double x, double y, double z) {
        graphics.pose().translate(x, y, z);
    }

    public void scale(float x, float y, float z) {
        graphics.pose().scale(x, y, z);
    }

    public void scissor(int x1, int y1, int x2, int y2) {
        var matrix = graphics.pose().last().pose();
        var min = matrix.transform(new org.joml.Vector4f(x1, y1, 0, 1));
        var max = matrix.transform(new org.joml.Vector4f(x2, y2, 0, 1));
        graphics.enableScissor(
                (int) Math.floor(min.x),
                (int) Math.floor(min.y),
                (int) Math.ceil(max.x),
                (int) Math.ceil(max.y));
    }
}
