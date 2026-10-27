package com.sammwy.retrocraft.compat;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public final class FrameEvents {
    public static void register(Runnable frame) {
        WorldRenderEvents.START_MAIN.register(context -> frame.run());
    }

    private FrameEvents() {}
}
