package com.sammwy.retrocraft.compat;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class HudEvents {
    public static void register(Consumer<GuiGraphics> callback) {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                com.sammwy.retrocraft.platform.Ids.of("retrocraft", "downloads"),
                (graphics, tick) -> {
                    if (Minecraft.getInstance().screen == null) callback.accept(graphics);
                });
    }

    private HudEvents() {}
}
