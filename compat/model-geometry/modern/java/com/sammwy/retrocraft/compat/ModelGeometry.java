package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.server.block.DockedConsoleBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.util.RandomSource;

public final class ModelGeometry {
    public static void draw(
            BlockStateModel model,
            DockedConsoleBlockEntity console,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay,
            boolean ambient) {
        var state = console.getBlockState();
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        if (ambient)
            renderer.tesselateBlock(
                    console.getLevel(),
                    model.collectParts(RandomSource.create()),
                    state,
                    console.getBlockPos().above(2),
                    poses,
                    buffers.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(state)),
                    false,
                    overlay);
        else
            renderer.renderModel(
                    poses.last(),
                    buffers.getBuffer(com.sammwy.retrocraft.compat.Materials.solid()),
                    model,
                    1,
                    1,
                    1,
                    light,
                    overlay);
    }

    public static BlockStateModel missing() {
        return Minecraft.getInstance().getModelManager().getMissingBlockStateModel();
    }

    private ModelGeometry() {}
}
