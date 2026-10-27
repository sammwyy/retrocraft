package com.sammwy.retrocraft.compat;

import net.minecraft.client.gui.GuiGraphics;

public record UiPose(GuiGraphics graphics) {
    public static UiPose of(GuiGraphics graphics) {
        return new UiPose(graphics);
    }

    public void pushPose() {
        graphics.pose().pushMatrix();
    }

    public void popPose() {
        graphics.pose().popMatrix();
    }

    public void translate(double x, double y, double z) {
        graphics.pose().translate((float) x, (float) y);
        if (z > 0) graphics.nextStratum();
    }

    public void scale(float x, float y, float z) {
        graphics.pose().scale(x, y);
    }

    public void scissor(int x1, int y1, int x2, int y2) {
        graphics.enableScissor(x1, y1, x2, y2);
    }
}
