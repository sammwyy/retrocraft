package com.sammwy.retrocraft.compat;

import java.util.function.Consumer;

import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ForgeEvents {
    private static FMLJavaModLoadingContext context;

    public static void initialize(FMLJavaModLoadingContext value) {
        context = value;
    }

    public static <T extends IModBusEvent> void mod(Class<T> type, Consumer<T> callback) {
        mod(context, type, callback);
    }

    public static <T extends IModBusEvent> void mod(
            FMLJavaModLoadingContext owner, Class<T> type, Consumer<T> callback) {
        IModBusEvent.getBus(owner.getModBusGroup(), type).addListener(callback);
    }

    private ForgeEvents() {}
}
