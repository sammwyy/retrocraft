package com.sammwy.retrocraft.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public abstract class GlyphButton extends Button {
    protected GlyphButton(
            int x,
            int y,
            int width,
            int height,
            Component title,
            OnPress pressed,
            CreateNarration narration) {
        super(x, y, width, height, title, pressed, narration);
    }

    protected abstract void drawContents(GuiGraphics graphics, int x, int y, float delta);

    @Override
    protected void renderContents(GuiGraphics graphics, int x, int y, float delta) {
        drawContents(graphics, x, y, delta);
    }
}
