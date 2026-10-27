package com.sammwy.retrocraft.compat;

import java.util.function.Consumer;

import net.minecraftforge.eventbus.api.Event;

public final class ForgeClientEvents {
    public static <T extends Event> void mod(Class<T> type, Consumer<T> callback) {
        ForgeEvents.mod(type, callback);
    }

    public static <T extends Event> void listen(Class<T> type, Consumer<T> callback) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, type, callback);
    }

    private ForgeClientEvents() {}
}
