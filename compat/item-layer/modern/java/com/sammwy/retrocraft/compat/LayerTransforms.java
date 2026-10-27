package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sammwy.retrocraft.mixin.ItemLayerAccessor;

public final class LayerTransforms {
    public static void apply(Object layer, PoseStack.Pose pose, boolean left) {
        ((ItemLayerAccessor) layer).retrocraft$transform().apply(left, pose);
    }

    private LayerTransforms() {}
}
