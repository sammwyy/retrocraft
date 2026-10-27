package com.sammwy.retrocraft.compat;

import com.mojang.authlib.GameProfile;

public final class Profiles {
    public static String name(GameProfile profile) {
        return profile.getName();
    }

    private Profiles() {}
}
