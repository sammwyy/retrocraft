package com.sammwy.retrocraft.platform.fabric;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.client.RetrocraftClient;
import com.sammwy.retrocraft.client.ui.ConsoleInventoryScreen;
import com.sammwy.retrocraft.compat.ExtraModels;
import com.sammwy.retrocraft.compat.ItemModels;
import com.sammwy.retrocraft.platform.ClientNetwork;
import com.sammwy.retrocraft.platform.ClientPlatform;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class FabricClient
        implements ClientModInitializer, ClientNetwork.Backend, ClientPlatform.Backend {
    @Override
    public void onInitializeClient() {
        ItemModels.bootstrap();
        ClientNetwork.install(this);
        ClientPlatform.install(this);
        new RetrocraftClient().initialize();
    }

    @Override
    public <T extends CustomPacketPayload> void receive(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ClientNetwork.Context> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(
                type,
                (packet, context) ->
                        receiver.accept(packet, new ClientNetwork.Context(context.client())));
    }

    @Override
    public void send(CustomPacketPayload packet) {
        ClientPlayNetworking.send(packet);
    }

    @Override
    public boolean canSend(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    @Override
    public KeyMapping key(KeyMapping key) {
        return KeyBindingHelper.registerKeyBinding(key);
    }

    @Override
    public void joined(Runnable callback) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> callback.run());
    }

    @Override
    public void disconnected(Runnable callback) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> callback.run());
    }

    @Override
    public void tick(Consumer<Minecraft> callback) {
        ClientTickEvents.END_CLIENT_TICK.register(callback::accept);
    }

    @Override
    public void frame(Runnable callback) {
        com.sammwy.retrocraft.compat.FrameEvents.register(callback);
    }

    @Override
    public void hud(Consumer<GuiGraphics> callback) {
        com.sammwy.retrocraft.compat.HudEvents.register(callback);
        ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) ->
                        ScreenEvents.afterRender(screen)
                                .register(
                                        (s, graphics, mx, my, delta) -> callback.accept(graphics)));
    }

    @Override
    public void predicate(Item item, ResourceLocation id, ClientPlatform.ItemPredicate predicate) {
        ItemModels.register(item, id, predicate);
    }

    @Override
    public boolean modelAvailable(ResourceLocation id) {
        return ExtraModels.available(id);
    }

    @Override
    public void models(Collection<ResourceLocation> models) {
        ExtraModels.register(models);
    }

    @Override
    public void model(
            ResourceLocation id,
            DockedConsoleBlockEntity console,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            boolean ambient) {
        ExtraModels.draw(id, console, poses, buffers, light, overlay, ambient);
    }

    @Override
    public <T extends BlockEntity> void blockRenderer(
            BlockEntityType<T> type, com.sammwy.retrocraft.platform.RendererFactory<T> renderer) {
        BlockEntityRenderers.register(type, renderer::create);
    }

    @Override
    public void consoleScreen() {
        MenuScreens.register(Retrocraft.CONSOLE_SCREEN.get(), ConsoleInventoryScreen::new);
    }
}
