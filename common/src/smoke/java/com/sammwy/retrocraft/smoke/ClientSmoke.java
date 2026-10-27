package com.sammwy.retrocraft.smoke;

import java.util.UUID;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.client.render.ScreenTextures;
import com.sammwy.retrocraft.client.ui.SettingsScreen;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.platform.ClientPlatform;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Development-only smoke test: initialize a real renderer, upload video and render settings. */
public final class ClientSmoke {
    private static int ticks;
    private static final UUID INSTANCE = UUID.randomUUID();
    private static final FrameCodec.Frame FRAME = new FrameCodec.Frame(4, 4, new byte[48]);
    private static ItemStack handheld = ItemStack.EMPTY;
    private static net.minecraft.client.gui.screens.Screen closing;

    public static void install() {
        ClientPlatform.hud(
                graphics -> {
                    if (!handheld.isEmpty()) graphics.renderItem(handheld, 8, 8);
                });
        ClientPlatform.tick(
                client -> {
                    if ((ticks == 0 && client.screen == null) || client.getOverlay() != null)
                        return;
                    ticks++;
                    if (ticks == 20) {
                        for (var model :
                                com.sammwy.retrocraft.client.render.DockedConsoleRenderer
                                        .arcadeModels())
                            if (!ClientPlatform.modelAvailable(model))
                                throw new AssertionError(
                                        "Missing Retrocraft casing model: " + model);
                        if (ConsoleRegistry.consoles().isEmpty())
                            throw new AssertionError("No consoles registered");
                        var texture = ScreenTextures.upload(INSTANCE, FRAME);
                        if (texture.width() != 4)
                            throw new AssertionError("Video texture upload failed");
                        handheld =
                                new ItemStack(
                                        ContentProperties.get(
                                                BuiltInRegistries.ITEM,
                                                Ids.of("retrocraft", "gb")));
                        HandheldConsoleItem.instance(handheld, INSTANCE);
                        client.setScreen(SettingsScreen.modMenu(null));
                    }
                    if (ticks > 20 && ticks < 80) ScreenTextures.upload(INSTANCE, FRAME);
                    if (ticks == 60) {
                        closing = client.screen;
                        if (closing == null
                                || !com.sammwy.retrocraft.compat.ClientInput.screenKey(
                                        closing, org.lwjgl.glfw.GLFW.GLFW_KEY_F8, 0, 0))
                            throw new AssertionError(
                                    "Settings did not accept its native keyboard event");
                    }
                    if (ticks == 75 && client.screen == closing)
                        throw new AssertionError("The settings closing animation did not finish");
                    if (ticks == 80) {
                        ScreenTextures.clear();
                        Retrocraft.LOG.info("RETROCRAFT_CLIENT_SMOKE_OK");
                        client.stop();
                    }
                });
    }

    private ClientSmoke() {}
}
