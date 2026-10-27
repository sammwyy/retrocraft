package com.sammwy.retrocraft.compat;

import java.util.function.Consumer;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ForgeEvents {
    private static FMLJavaModLoadingContext context;

    public static void initialize(FMLJavaModLoadingContext value) {
        context = value;
    }

    public static <T extends Event> void mod(Class<T> type, Consumer<T> callback) {
        mod(context, type, callback);
    }

    public static <T extends Event> void mod(
            FMLJavaModLoadingContext owner, Class<T> type, Consumer<T> callback) {
        owner.getModEventBus()
                .addListener(
                        net.minecraftforge.eventbus.api.EventPriority.NORMAL,
                        false,
                        type,
                        callback);
    }

    private ForgeEvents() {}
}
