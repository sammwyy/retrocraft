package com.sammwy.retrocraft.compat;

import net.minecraft.client.Minecraft;

public final class ClientHud {
    public static boolean hidden(Minecraft client) {
        return client.options.hideGui;
    }

    public static void hidden(Minecraft client, boolean hidden) {
        client.options.hideGui = hidden;
    }

    private ClientHud() {}
}
