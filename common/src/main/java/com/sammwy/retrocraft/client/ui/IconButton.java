package com.sammwy.retrocraft.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Miniature square action button: a drawn pixel glyph with a tooltip instead of a label. */
final class IconButton extends com.sammwy.retrocraft.compat.GlyphButton {
    enum Icon {
        POWER,
        EJECT,
        ZOOM_IN,
        ZOOM_OUT
    }

    private final Icon icon;

    IconButton(int x, int y, Icon icon, OnPress onPress, Component tooltip) {
        super(x, y, 14, 14, tooltip, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void drawContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean hover = isHovered();
        rounded(context, getX(), getY(), 14, 14, hover ? 0xE04A5160 : 0xC0282D38, 3);
        context.fill(getX() + 3, getY(), getX() + 11, getY() + 1, hover ? 0x30FFFFFF : 0x18FFFFFF);
        int fg = hover ? 0xFFFFFFFF : 0xBFE4E8F0;
        String[] art =
                switch (icon) {
                    case POWER -> POWER_GLYPH;
                    case EJECT -> EJECT_GLYPH;
                    case ZOOM_IN -> ZOOM_IN_GLYPH;
                    case ZOOM_OUT -> ZOOM_OUT_GLYPH;
                };
        UiDrawing.centeredGlyph(context, art, getX(), getY(), getWidth(), getHeight(), fg);
    }

    // Power symbol: open ring with the 12-o'clock bar. Eject: up triangle over a tray bar.
    // Zoom: magnifier lens with a plus/minus inside and a handle out the lower right.
    private static final String[] POWER_GLYPH = {
        "   XX   ", " X XX X ", "X  XX  X", "X      X", "X      X", " X    X ", "  XXXX  "
    };
    private static final String[] EJECT_GLYPH = {
        "   X   ", "  XXX  ", " XXXXX ", "XXXXXXX", "       ", "XXXXXXX"
    };
    private static final String[] ZOOM_IN_GLYPH = {
        " XXX    ", "X X X   ", "XXXXX   ", "X X X   ", " XXX    ", "     X  ", "      X "
    };
    private static final String[] ZOOM_OUT_GLYPH = {
        " XXX    ", "X   X   ", "XXXXX   ", "X   X   ", " XXX    ", "     X  ", "      X "
    };

    private static void rounded(GuiGraphics context, int x, int y, int w, int h, int color, int r) {
        context.fill(x + r, y, x + w - r, y + h, color);
        context.fill(x, y + r, x + r, y + h - r, color);
        context.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int k = 1; k < r; k++) {
            context.fill(x + r - k, y + k, x + r, y + k + 1, color);
            context.fill(x + w - r, y + k, x + w - r + k, y + k + 1, color);
            context.fill(x + r - k, y + h - k - 1, x + r, y + h - k, color);
            context.fill(x + w - r, y + h - k - 1, x + w - r + k, y + h - k, color);
        }
    }
}
