package com.sammwy.retrocraft.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

public final class Access {
    public static boolean operator(Player player) {
        return player.hasPermissions(2);
    }

    public static boolean operator(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private Access() {}
}
