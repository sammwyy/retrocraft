package com.sammwy.retrocraft.compat;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

public final class SkinTextures {
    public static ResourceLocation texture(PlayerInfo player) {
        return player.getSkin().body().texturePath();
    }

    private SkinTextures() {}
}
