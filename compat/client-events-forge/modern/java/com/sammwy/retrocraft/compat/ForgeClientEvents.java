package com.sammwy.retrocraft.compat;

import java.util.function.Consumer;

import net.minecraftforge.eventbus.api.bus.EventBus;

public final class ForgeClientEvents {
    public static <T> void mod(Class<T> type, Consumer<T> callback) {
        listen(type, callback);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void listen(Class<T> type, Consumer<T> callback) {
        EventBus<?> bus;
        if (type == net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn.class)
            bus = net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn.BUS;
        else if (type == net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class)
            bus = net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.BUS;
        else if (type == net.minecraftforge.event.TickEvent.ClientTickEvent.Post.class)
            bus = net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS;
        else if (type == net.minecraftforge.event.TickEvent.RenderTickEvent.Pre.class)
            bus = net.minecraftforge.event.TickEvent.RenderTickEvent.Pre.BUS;
        else if (type == net.minecraftforge.client.event.ScreenEvent.Render.Post.class)
            bus = net.minecraftforge.client.event.ScreenEvent.Render.Post.BUS;
        else if (type == net.minecraftforge.client.event.RegisterKeyMappingsEvent.class)
            bus = net.minecraftforge.client.event.RegisterKeyMappingsEvent.BUS;
        else if (type
                == net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.class)
            bus = net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.BUS;
        else if (type == net.minecraftforge.client.event.AddGuiOverlayLayersEvent.class)
            bus = net.minecraftforge.client.event.AddGuiOverlayLayersEvent.BUS;
        else if (type
                == net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions.class)
            bus = net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions.BUS;
        else throw new IllegalArgumentException("Unsupported Forge event: " + type.getName());
        ((EventBus) bus).addListener(callback);
    }

    private ForgeClientEvents() {}
}
