package com.sammwy.retrocraft.compat;

import java.util.function.Consumer;

import net.minecraftforge.eventbus.api.Event;

public final class ForgeServerEvents {
    public static <T extends Event> void listen(Class<T> type, Consumer<T> callback) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, type, callback);
    }

    private ForgeServerEvents() {}
}
