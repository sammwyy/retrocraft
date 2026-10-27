package com.sammwy.retrocraft.compat;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class UiTooltips {
    public static void show(GuiGraphics graphics, Font font, List<Component> lines, int x, int y) {
        graphics.setComponentTooltipForNextFrame(font, lines, x, y);
    }

    private UiTooltips() {}
}
