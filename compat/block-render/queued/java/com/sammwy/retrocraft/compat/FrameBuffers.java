package com.sammwy.retrocraft.compat;

import java.util.*;
import java.util.function.BiConsumer;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** Records the shared immediate geometry during extraction and submits a self-contained frame. */
public final class FrameBuffers implements MultiBufferSource {
    @FunctionalInterface
    public interface Command {
        void submit(PoseStack base, SubmitNodeCollector collector);
    }

    private final List<Command> commands = new ArrayList<>();
    private final Map<RenderType, Vertices> buffers = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return buffers.computeIfAbsent(
                type,
                key -> {
                    var vertices = new Vertices();
                    commands.add(
                            (base, collector) ->
                                    collector.submitCustomGeometry(
                                            base,
                                            key,
                                            (pose, output) -> vertices.emit(pose, output)));
                    return vertices;
                });
    }

    public void record(PoseStack local, BiConsumer<PoseStack, SubmitNodeCollector> command) {
        var transform = new Matrix4f(local.last().pose());
        commands.add(
                (base, collector) -> {
                    base.pushPose();
                    base.mulPose(transform);
                    try {
                        command.accept(base, collector);
                    } finally {
                        base.popPose();
                    }
                });
    }

    public List<Command> snapshot() {
        return List.copyOf(commands);
    }

    public static void submit(
            List<Command> commands, PoseStack base, SubmitNodeCollector collector) {
        for (var command : commands) command.submit(base, collector);
    }

    private static final class Vertex {
        float x, y, z, u, v, nx, ny, nz;
        float lineWidth = 1;
        int r = 255, g = 255, b = 255, a = 255, overlayU, overlayV, lightU, lightV;
    }

    private static final class Vertices implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private Vertex current;

        public VertexConsumer addVertex(float x, float y, float z) {
            current = new Vertex();
            current.x = x;
            current.y = y;
            current.z = z;
            vertices.add(current);
            return this;
        }

        public VertexConsumer setColor(int r, int g, int b, int a) {
            current.r = r;
            current.g = g;
            current.b = b;
            current.a = a;
            return this;
        }

        public VertexConsumer setColor(int argb) {
            return setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24);
        }

        public VertexConsumer setUv(float u, float v) {
            current.u = u;
            current.v = v;
            return this;
        }

        public VertexConsumer setUv1(int u, int v) {
            current.overlayU = u;
            current.overlayV = v;
            return this;
        }

        public VertexConsumer setUv2(int u, int v) {
            current.lightU = u;
            current.lightV = v;
            return this;
        }

        public VertexConsumer setNormal(float x, float y, float z) {
            current.nx = x;
            current.ny = y;
            current.nz = z;
            return this;
        }

        public VertexConsumer setLineWidth(float width) {
            current.lineWidth = width;
            return this;
        }

        void emit(PoseStack.Pose pose, VertexConsumer output) {
            for (var v : vertices) {
                output.addVertex(pose, v.x, v.y, v.z)
                        .setColor(v.r, v.g, v.b, v.a)
                        .setUv(v.u, v.v)
                        .setUv1(v.overlayU, v.overlayV)
                        .setUv2(v.lightU, v.lightV)
                        .setNormal(pose, v.nx, v.ny, v.nz);
                VertexWidths.apply(output, v.lineWidth);
            }
        }
    }
}
