package com.sammwy.retrocraft.platform;

import java.util.Collection;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ClientPlatform {
    @FunctionalInterface
    public interface ItemPredicate {
        float value(ItemStack stack, ClientLevel level, LivingEntity entity, int seed);
    }

    public interface Backend {
        KeyMapping key(KeyMapping key);

        void joined(Runnable callback);

        void disconnected(Runnable callback);

        void tick(Consumer<Minecraft> callback);

        void frame(Runnable callback);

        void hud(Consumer<GuiGraphics> callback);

        void predicate(Item item, ResourceLocation id, ItemPredicate predicate);

        void models(Collection<ResourceLocation> models);

        boolean modelAvailable(ResourceLocation id);

        void model(
                ResourceLocation id,
                DockedConsoleBlockEntity console,
                PoseStack poses,
                MultiBufferSource buffers,
                int light,
                int overlay,
                boolean ambient);

        <T extends BlockEntity> void blockRenderer(
                BlockEntityType<T> type,
                com.sammwy.retrocraft.platform.RendererFactory<T> renderer);

        void consoleScreen();
    }

    private static Backend backend;

    public static void install(Backend implementation) {
        backend = implementation;
    }

    public static KeyMapping key(KeyMapping key) {
        return backend.key(key);
    }

    public static void joined(Runnable callback) {
        backend.joined(callback);
    }

    public static void disconnected(Runnable callback) {
        backend.disconnected(callback);
    }

    public static void tick(Consumer<Minecraft> callback) {
        backend.tick(callback);
    }

    public static void frame(Runnable callback) {
        backend.frame(callback);
    }

    public static void hud(Consumer<GuiGraphics> callback) {
        backend.hud(callback);
    }

    public static void predicate(Item item, ResourceLocation id, ItemPredicate predicate) {
        backend.predicate(item, id, predicate);
    }

    public static boolean modelAvailable(ResourceLocation id) {
        return backend.modelAvailable(id);
    }

    public static void models(Collection<ResourceLocation> models) {
        backend.models(models);
    }

    public static void model(
            ResourceLocation id,
            DockedConsoleBlockEntity console,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            boolean ambient) {
        backend.model(id, console, poses, buffers, light, overlay, ambient);
    }

    public static <T extends BlockEntity> void blockRenderer(
            BlockEntityType<T> type, com.sammwy.retrocraft.platform.RendererFactory<T> renderer) {
        backend.blockRenderer(type, renderer);
    }

    public static void consoleScreen() {
        backend.consoleScreen();
    }

    private ClientPlatform() {}
}
