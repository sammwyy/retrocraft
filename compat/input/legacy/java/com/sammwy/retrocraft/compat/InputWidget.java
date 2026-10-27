package com.sammwy.retrocraft.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public abstract class InputWidget extends AbstractWidget {
    protected InputWidget(int x, int y, int width, int height, Component title) {
        super(x, y, width, height, title);
    }
}
