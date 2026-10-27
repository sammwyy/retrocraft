package com.sammwy.retrocraft.compat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class InputScreen extends Screen {
    protected InputScreen(Component title) {
        super(title);
    }
}
