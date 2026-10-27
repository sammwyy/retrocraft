package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class VertexWidths {
    public static void apply(VertexConsumer output, float width) {
        output.setLineWidth(width);
    }

    private VertexWidths() {}
}
