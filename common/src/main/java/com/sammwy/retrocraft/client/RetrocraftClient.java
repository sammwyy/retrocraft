package com.sammwy.retrocraft.client;

import java.util.*;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.sammwy.retrocraft.client.media.AudioStreams;
import com.sammwy.retrocraft.client.media.VideoStreams;
import com.sammwy.retrocraft.client.net.HybridClient;
import com.sammwy.retrocraft.client.net.SettingsClient;
import com.sammwy.retrocraft.client.render.CameraRig;
import com.sammwy.retrocraft.client.render.DockedConsoleRenderer;
import com.sammwy.retrocraft.client.render.ScreenTextures;
import com.sammwy.retrocraft.client.render.Thumbnails;
import com.sammwy.retrocraft.client.render.TvRenderer;
import com.sammwy.retrocraft.client.ui.BiosRequiredScreen;
import com.sammwy.retrocraft.client.ui.ConsoleScreen;
import com.sammwy.retrocraft.client.ui.DownloadHud;
import com.sammwy.retrocraft.client.ui.PlayerHud;
import com.sammwy.retrocraft.client.ui.SettingsScreen;
import com.sammwy.retrocraft.client.ui.hud.DefaultConsoleHuds;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.cores.transfer.Downloads;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.platform.ClientPlatform;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.roms.RomCatalog;
import com.sammwy.retrocraft.server.block.DockedConsoleBlock;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.item.RetrocraftItemGroups;
import com.sammwy.retrocraft.server.net.HybridPackets;
import com.sammwy.retrocraft.server.net.Packets;
import com.sammwy.retrocraft.server.net.SettingsPackets;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

public final class RetrocraftClient {
    private final AudioStreams audio = new AudioStreams();
    private final VideoStreams video = new VideoStreams();
    public static VideoStreams VIDEO;
    static AudioStreams AUDIO;
    public static KeyMapping SETTINGS_KEY;
    public static HybridClient EMULATION;
    private final List<RomCatalog.Entry> pendingCatalog = new ArrayList<>();
    private int catalogGeneration;
    private boolean catalogDirty;

    public void initialize() {
        ClientPreferences.load();
        DefaultConsoleHuds.register();
        DownloadHud.register();
        SETTINGS_KEY =
                ClientPlatform.key(
                        com.sammwy.retrocraft.compat.ClientInput.key(
                                "key.retrocraft.settings",
                                InputConstants.Type.KEYSYM,
                                GLFW.GLFW_KEY_F8,
                                "category.retrocraft"));
        EMULATION = new HybridClient(audio);
        VIDEO = video;
        AUDIO = audio;
        ClientPlatform.joined(ClientPreferences::send);
        for (var console : ConsoleRegistry.consoles()) {
            if (console.type() == ConsoleType.HANDHELD)
                ClientPlatform.predicate(
                        ContentProperties.get(BuiltInRegistries.ITEM, console.id()),
                        Ids.of("retrocraft", "powered"),
                        (stack, world, entity, seed) ->
                                HandheldConsoleItem.instance(stack) == null ? 0 : 1);
            else
                ClientPlatform.blockRenderer(
                        ((DockedConsoleBlock)
                                        ContentProperties.get(
                                                BuiltInRegistries.BLOCK, console.id()))
                                .entityType(),
                        DockedConsoleRenderer::new);
        }
        for (var tv : ConsoleRegistry.televisions())
            ClientPlatform.blockRenderer(tv.entityType(), TvRenderer::new);
        ClientNetwork.registerGlobalReceiver(
                HybridPackets.Settings.ID,
                (packet, context) -> {
                    ClientPreferences.send();
                    EMULATION.settings(packet);
                });
        ClientNetwork.registerGlobalReceiver(
                HybridPackets.Lease.ID, (packet, context) -> EMULATION.lease(packet));
        ClientNetwork.registerGlobalReceiver(
                HybridPackets.Blob.ID, (packet, context) -> EMULATION.blob(packet));
        ClientNetwork.registerGlobalReceiver(
                HybridPackets.Demand.ID, (packet, context) -> EMULATION.demand(packet));
        ClientNetwork.registerGlobalReceiver(
                com.sammwy.retrocraft.server.net.LinkPackets.State.ID,
                (packet, context) -> {
                    if (context.client().screen instanceof ConsoleScreen screen)
                        screen.link(packet);
                });
        ClientNetwork.registerGlobalReceiver(
                Packets.Rumble.ID, (packet, context) -> PlayerHud.rumble(packet));
        ClientPlatform.consoleScreen();
        ClientPlatform.models(DockedConsoleRenderer.arcadeModels());
        ClientNetwork.registerGlobalReceiver(
                Packets.Ui.ID,
                (packet, context) -> {
                    var client = context.client();
                    switch (packet.mode()) {
                        case Packets.PLAY -> {
                            if (client.screen instanceof ConsoleScreen screen
                                    && screen.instance.equals(packet.instance()))
                                screen.update(packet);
                            else client.setScreen(new ConsoleScreen(packet));
                        }
                        case Packets.ROLE -> {
                            if (client.screen instanceof ConsoleScreen screen
                                    && screen.instance.equals(packet.instance()))
                                screen.update(packet);
                        }
                        case Packets.STOP -> {
                            if (client.screen instanceof ConsoleScreen screen
                                    && screen.instance.equals(packet.instance()))
                                screen.serverClosed();
                            PlayerHud.drop(packet.instance());
                        }
                        default -> {}
                    }
                });
        ClientNetwork.registerGlobalReceiver(
                Packets.Video.ID,
                (packet, context) -> {
                    if (!EMULATION.owns(packet.instance())) video.accept(packet);
                });
        ClientNetwork.registerGlobalReceiver(
                Packets.Audio.ID,
                (packet, context) -> {
                    if (!EMULATION.owns(packet.instance())) audio.accept(context.client(), packet);
                });
        ClientNetwork.registerGlobalReceiver(
                Packets.AudioStop.ID, (packet, context) -> audio.stop(packet.instance()));
        ClientNetwork.registerGlobalReceiver(
                Packets.Thumbnail.ID, (packet, context) -> Thumbnails.accept(packet));
        ClientNetwork.registerGlobalReceiver(
                Packets.Players.ID, (packet, context) -> PlayerHud.roster(packet));
        ClientNetwork.registerGlobalReceiver(
                Packets.Pad.ID, (packet, context) -> PlayerHud.pad(packet));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.BiosRequired.ID,
                (packet, context) ->
                        context.client()
                                .execute(
                                        () ->
                                                context.client()
                                                        .setScreen(
                                                                new BiosRequiredScreen(
                                                                        packet.coreId(),
                                                                        packet.administrator()))));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.GuiOpen.ID,
                (packet, context) ->
                        context.client()
                                .execute(() -> SettingsClient.guiOpen(context.client(), packet)));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.ConfigSaved.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.configSaved(packet)));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.RomList.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.romList(packet)));

        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.BiosChunk.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.biosChunk(packet)));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.BiosManifest.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.biosManifest(packet)));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.BiosUnavailable.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.biosUnavailable(packet)));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.ScanEvent.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.scanEvent(packet)));
        ClientNetwork.registerGlobalReceiver(
                SettingsPackets.RomEvent.ID,
                (packet, context) ->
                        context.client().execute(() -> SettingsClient.romEvent(packet)));
        ClientNetwork.registerGlobalReceiver(
                Packets.Catalog.ID,
                (packet, context) -> {
                    if (packet.first()) {
                        pendingCatalog.clear();
                        catalogGeneration = packet.generation();
                    }
                    if (packet.generation() != catalogGeneration) return;
                    pendingCatalog.addAll(packet.entries());
                    if (packet.last()) {
                        RomCatalog.clientEntries(pendingCatalog);
                        pendingCatalog.clear();
                        catalogDirty = true;
                    }
                });
        ClientPlatform.tick(
                client -> {
                    while (SETTINGS_KEY.consumeClick()) {
                        if (client.screen instanceof SettingsScreen screen) screen.onClose();
                        else client.setScreen(SettingsScreen.modMenu(client.screen));
                    }
                    EMULATION.tick(client);
                    SettingsClient.tick();
                    video.tick();
                    audio.tick();
                    ScreenTextures.tick();
                    Thumbnails.tick();
                    if (!catalogDirty || client.level == null || client.player == null) return;
                    var display =
                            new CreativeModeTab.ItemDisplayParameters(
                                    client.level.enabledFeatures(),
                                    com.sammwy.retrocraft.compat.Access.operator(client.player),
                                    client.level.registryAccess());
                    RetrocraftItemGroups.CONSOLES.get().buildContents(display);
                    RetrocraftItemGroups.GAMES.get().buildContents(display);
                    RetrocraftItemGroups.ARCADES.get().buildContents(display);
                    CreativeModeTabs.searchTab().buildContents(display);
                    if (client.screen instanceof CreativeModeInventoryScreen screen)
                        com.sammwy.retrocraft.compat.ClientScreens.refresh(screen);
                    catalogDirty = false;
                });
        // Locally emulated frames are uploaded per rendered frame, not per tick, so in-world
        // screens stay smooth.
        ClientPlatform.frame(
                () -> {
                    EMULATION.pollInput(Minecraft.getInstance());
                    for (UUID id : EMULATION.active()) {
                        var frame = EMULATION.frame(id);
                        if (frame != null) video.local(id, frame);
                    }
                    video.render();
                });
        ClientPlatform.disconnected(
                () -> {
                    var client = Minecraft.getInstance();
                    CameraRig.reset();
                    EMULATION.disconnect();
                    audio.close();
                    video.close();
                    Thumbnails.close();
                    PlayerHud.clear();
                    SettingsClient.disconnect();
                    DownloadHud.DOWNLOADS = new Downloads();
                    RomCatalog.clientEntries(List.of());
                    pendingCatalog.clear();
                    catalogDirty = true;
                    if (client.screen instanceof ConsoleScreen screen) screen.serverClosed();
                });
    }
}
