package com.sammwy.retrocraft.compat;

import java.util.Collection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class ExtraModels {
    public static java.util.function.Supplier<Collection<ResourceLocation>> SOURCE =
            java.util.List::of;

    public static void install(java.util.function.Supplier<Collection<ResourceLocation>> models) {
        SOURCE = models;
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
                        .getModel(new ModelResourceLocation(id, "standalone"));
        if (model == null) return;
        var state = console.getBlockState();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        if (ambient)
            renderer.tesselateBlock(
                    console.getLevel(),
                    model,
                    state,
                    console.getBlockPos().above(2),
                    poses,
                    buffers.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(state)),
                    false,
                    RandomSource.create(),
                    state.getSeed(console.getBlockPos()),
                    overlay);
        else
            renderer.renderModel(
                    poses.last(),
                    buffers.getBuffer(com.sammwy.retrocraft.compat.Materials.solid()),
                    state,
                    model,
                    1,
                    1,
                    1,
                    light,
                    overlay);
    }

    public static boolean available(ResourceLocation id) {
        var model =
                Minecraft.getInstance()
                        .getModelManager()
                        .getModel(new ModelResourceLocation(id, "standalone"));
        return model != null
                && model != Minecraft.getInstance().getModelManager().getMissingModel();
    }

    private ExtraModels() {}
}
