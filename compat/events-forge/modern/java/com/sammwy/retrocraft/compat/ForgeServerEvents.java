package com.sammwy.retrocraft.compat;

import java.util.function.Consumer;

import net.minecraftforge.eventbus.api.bus.EventBus;

public final class ForgeServerEvents {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void listen(Class<T> type, Consumer<T> callback) {
        EventBus<?> bus;
        if (type == net.minecraftforge.event.RegisterCommandsEvent.class)
            bus = net.minecraftforge.event.RegisterCommandsEvent.BUS;
        else if (type == net.minecraftforge.event.server.ServerStartedEvent.class)
            bus = net.minecraftforge.event.server.ServerStartedEvent.BUS;
        else if (type == net.minecraftforge.event.server.ServerStoppingEvent.class)
            bus = net.minecraftforge.event.server.ServerStoppingEvent.BUS;
        else if (type == net.minecraftforge.event.TickEvent.ServerTickEvent.Post.class)
            bus = net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS;
        else if (type == net.minecraftforge.event.entity.EntityJoinLevelEvent.class)
            bus = net.minecraftforge.event.entity.EntityJoinLevelEvent.BUS;
        else if (type == net.minecraftforge.event.entity.EntityLeaveLevelEvent.class)
            bus = net.minecraftforge.event.entity.EntityLeaveLevelEvent.BUS;
        else if (type
                == net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class)
            bus = net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.BUS;
        else if (type
                == net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class)
            bus = net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.BUS;
        else if (type == net.minecraftforge.event.entity.player.PlayerEvent.StartTracking.class)
            bus = net.minecraftforge.event.entity.player.PlayerEvent.StartTracking.BUS;
        else if (type == net.minecraftforge.event.entity.player.PlayerEvent.StopTracking.class)
            bus = net.minecraftforge.event.entity.player.PlayerEvent.StopTracking.BUS;
        else throw new IllegalArgumentException("Unsupported Forge event: " + type.getName());
        ((EventBus) bus).addListener(callback);
    }

    private ForgeServerEvents() {}
}
