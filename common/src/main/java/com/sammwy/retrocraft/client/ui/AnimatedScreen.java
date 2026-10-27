package com.sammwy.retrocraft.client.ui;

import org.lwjgl.glfw.GLFW;

import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.compat.UiPose;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keeps the screen alive until its closing animation has finished. */
abstract class AnimatedScreen extends com.sammwy.retrocraft.compat.InputScreen {
    private final UiMotion motion = new UiMotion();
    private Runnable afterClose;

    AnimatedScreen(Component title) {
        super(title);
    }

    public final boolean acceptingInput() {
        return motion.shown() && motion.settled(System.nanoTime());
    }

    final boolean closing() {
        return !motion.shown();
    }

    @Override
    public final void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float visibility = motion.value(now);
        if (closing() && motion.settled(now)) {
            Runnable action = afterClose;
            afterClose = null;
            if (action != null) action.run();
            return;
        }
        renderDimmer(context, visibility);
        UiPose.of(context).pushPose();
        try {
            UiDrawing.pop(context, width / 2f, height / 2f, visibility);
            renderContents(
                    context, acceptingInput() ? mouseX : -1, acceptingInput() ? mouseY : -1, delta);
        } finally {
            ClientGraphics.color(context, 1, 1, 1, 1);
            UiPose.of(context).popPose();
        }
    }

    protected abstract void renderContents(
            GuiGraphics context, int mouseX, int mouseY, float delta);

    protected void renderDimmer(GuiGraphics context, float visibility) {}

    protected final void renderWidgets(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    protected final void transitionTo(Screen next) {
        dismiss(() -> minecraft.setScreen(next));
    }

    private void dismiss(Runnable action) {
        if (closing()) return;
        afterClose = action;
        motion.show(false, System.nanoTime());
        setFocused(null);
    }

    @Override
    public void onClose() {
        dismiss(this::finishClose);
    }

    protected void finishClose() {
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return !acceptingInput() || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return !acceptingInput() || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return !acceptingInput() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return !acceptingInput() || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (closing()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return !acceptingInput() || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return !acceptingInput() || super.charTyped(chr, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}
}
