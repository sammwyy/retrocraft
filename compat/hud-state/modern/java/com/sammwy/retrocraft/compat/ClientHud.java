package com.sammwy.retrocraft.compat;

import net.minecraft.client.Minecraft;

public final class ClientHud {
    public static boolean hidden(Minecraft client) {
        return client.gui.hud.isHidden();
    }

    public static void hidden(Minecraft client, boolean hidden) {
        if (hidden(client) != hidden) client.gui.hud.toggle();
    }

    private ClientHud() {}
}
