package com.sammwy.retrocraft.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientScreens {
    public static void refresh(Screen screen) {
        var client = Minecraft.getInstance();
        screen.init(
                client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
    }

    private ClientScreens() {}
}
