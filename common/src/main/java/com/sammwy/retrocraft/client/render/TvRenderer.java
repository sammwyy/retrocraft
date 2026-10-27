package com.sammwy.retrocraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sammwy.retrocraft.compat.ConsoleRenderer;
import com.sammwy.retrocraft.server.block.*;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Paints the bound console's live picture on the television glass; the cabinet itself is the baked
 * block model.
 */
public final class TvRenderer implements ConsoleRenderer<TvBlockEntity> {
    public TvRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void draw(
            TvBlockEntity tv,
            float tickDelta,
            PoseStack matrices,
            MultiBufferSource vertices,
            int light,
            int overlay) {
        var entry = ScreenTextures.get(tv.instance());
        if (entry == null) return;
        TvBlock.Size size = tv.size();
        matrices.pushPose();
        matrices.translate(.5, 0, .5);
        float rotation =
                switch (tv.getBlockState().getValue(TvBlock.FACING)) {
                    case EAST -> -90;
                    case SOUTH -> 180;
                    case WEST -> 90;
                    default -> 0;
                };
        matrices.mulPose(Axis.YP.rotationDegrees(rotation));
        DockedConsoleRenderer.drawScreen(
                entry,
                matrices,
                vertices,
                size.screenX0,
                size.screenX1,
                size.screenY0,
                size.screenY1,
                size.screenZ);
        matrices.popPose();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
