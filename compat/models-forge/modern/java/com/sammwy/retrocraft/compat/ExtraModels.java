package com.sammwy.retrocraft.compat;

import java.util.Collection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

public final class ExtraModels {
    private static final java.util.Map<
                    ResourceLocation, net.minecraft.world.level.block.state.BlockState>
            STATES = new java.util.HashMap<>();

    public static void install(java.util.function.Supplier<Collection<ResourceLocation>> models) {
        ForgeClientEvents.mod(
                ModelEvent.RegisterModelStateDefinitions.class,
                (ModelEvent.RegisterModelStateDefinitions event) -> {
                    STATES.clear();
                    for (var id : models.get()) {
                        var key =
                                com.sammwy.retrocraft.platform.Ids.of(
                                        "retrocraft", "extra/" + id.getPath());
                        var definition =
                                new net.minecraft.world.level.block.state.StateDefinition.Builder<
                                                net.minecraft.world.level.block.Block,
                                                net.minecraft.world.level.block.state.BlockState>(
                                                net.minecraft.world.level.block.Blocks.AIR)
                                        .create(
                                                net.minecraft.world.level.block.Block
                                                        ::defaultBlockState,
                                                net.minecraft.world.level.block.state.BlockState
                                                        ::new);
                        STATES.put(id, definition.any());
                        event.register(key, definition);
                    }
                });
    }

    public static void draw(
            ResourceLocation id,
            DockedConsoleBlockEntity console,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            boolean ambient) {
        var model =
                Minecraft.getInstance()
                        .getModelManager()
                        .getBlockModelShaper()
                        .getBlockModel(STATES.get(id));
        if (model == null) return;
        ModelGeometry.draw(model, console, poses, buffers, light, overlay, ambient);
    }

    public static boolean available(ResourceLocation id) {
        if (!STATES.containsKey(id)) return false;
        var model =
                Minecraft.getInstance()
                        .getModelManager()
                        .getBlockModelShaper()
                        .getBlockModel(STATES.get(id));
        return model != null && model != ModelGeometry.missing();
    }

    private ExtraModels() {}
}
