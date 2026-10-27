package com.sammwy.retrocraft.client.render;

import java.io.*;
import java.util.*;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.client.RetrocraftClient;
import com.sammwy.retrocraft.compat.TextureImages;
import com.sammwy.retrocraft.compat.Vertices;
import com.sammwy.retrocraft.cores.stream.DualScreen;
import com.sammwy.retrocraft.cores.stream.ThumbnailCodec;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;
import com.sammwy.retrocraft.server.net.Packets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Pocket-size copies of a handheld's live picture; the server refreshes them for whoever can see
 * the item.
 */
public final class Thumbnails {
    private static final long MIN_AGE = 15_000_000_000L;

    /** LCD glass on each item model (model units, south face): x0 x1 y0 y1 z. */
    private static final Map<String, float[]> SCREENS =
            Map.of(
                    "gb", new float[] {5.2f, 10.8f, 9, 14.04f, 9.22f},
                    "gbc", new float[] {5.2f, 10.8f, 9.35f, 14.39f, 9.22f},
                    "gba", new float[] {4.9f, 11.1f, 6.3f, 10.44f, 9.22f},
                    "nes_handheld", new float[] {5.15f, 10.85f, 6.2f, 10.55f, 9.22f},
                    "gba_sp", new float[] {4.4f, 11.6f, 9.4f, 14.2f, 8.72f},
                    "nds", new float[] {4.4f, 11.6f, 9.15f, 14.55f, 8.72f},
                    "3ds", new float[] {3, 13, 8.95f, 14.95f, 8.72f},
                    "psp", new float[] {3.7f, 12.3f, 5.76f, 10.6333f, 9.22f});

    private static final Map<String, float[]> TOUCH_SCREENS =
            Map.of(
                    "nds", new float[] {4.4f, 11.6f, 1.9f, 7.3f, 9.18f},
                    "3ds", new float[] {4, 12, 1.35f, 7.35f, 9.18f});

    private record Entry(ResourceLocation id, int width, int height, long at, byte[] png) {}

    private static final Map<UUID, Entry> entries = new HashMap<>();
    private static final Map<UUID, DynamicTexture> textures = new HashMap<>();

    public static void accept(Packets.Thumbnail packet) {
        if (!ClientPreferences.get().renderHandheldScreens) return;
        if (packet.png().length == 0) {
            release(packet.instance());
            return;
        }
        if (!ThumbnailCodec.valid(packet.png())) return;
        Entry cached = entries.get(packet.instance());
        if (cached != null && Arrays.equals(cached.png, packet.png())) {
            entries.put(
                    packet.instance(),
                    new Entry(
                            cached.id, cached.width, cached.height, System.nanoTime(), cached.png));
            return;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(packet.png()));
            Entry entry = entries.get(packet.instance());
            DynamicTexture texture = textures.get(packet.instance());
            if (texture == null
                    || entry.width != image.getWidth()
                    || entry.height != image.getHeight()) {
                release(packet.instance());
                texture = TextureImages.create(image);
                ResourceLocation id = Ids.of("retrocraft", "thumbnail/" + packet.instance());
                Minecraft.getInstance().getTextureManager().register(id, texture);
                textures.put(packet.instance(), texture);
                entry = new Entry(id, image.getWidth(), image.getHeight(), 0, packet.png());
            } else texture.setPixels(image);
            texture.upload();
            entries.put(
                    packet.instance(),
                    new Entry(
                            entry.id, entry.width, entry.height, System.nanoTime(), packet.png()));
        } catch (IOException ignored) {
        }
    }

    public static void tick() {
        long now = System.nanoTime(),
                maxAge =
                        Math.max(
                                MIN_AGE,
                                RetrocraftClient.EMULATION.thumbnailSeconds() * 3_000_000_000L);
        for (UUID id : List.copyOf(entries.keySet()))
            if (now - entries.get(id).at > maxAge) release(id);
    }

    public static void close() {
        for (UUID id : List.copyOf(entries.keySet())) release(id);
    }

    private static void release(UUID instance) {
        Entry entry = entries.remove(instance);
        if (textures.remove(instance) != null && entry != null)
            Minecraft.getInstance().getTextureManager().release(entry.id);
    }

    /**
     * Overlays the console's picture on the item's LCD glass: live frames when streamed, the
     * thumbnail otherwise.
     */
    public static void render(ItemStack stack, PoseStack matrices, MultiBufferSource vertices) {
        if (!ClientPreferences.get().renderHandheldScreens) return;
        if (!(stack.getItem() instanceof HandheldConsoleItem item)) return;
        UUID instance = HandheldConsoleItem.instance(stack);
        float[] rect = instance == null ? null : SCREENS.get(item.definition().id().getPath());
        if (rect == null) return;
        var live = ScreenTextures.live(instance);
        var thumb = live == null ? entries.get(instance) : null;
        if (live == null && thumb == null) return;
        ResourceLocation texture = live != null ? live.id() : thumb.id();
        int w = live != null ? live.width() : thumb.width(),
                h = live != null ? live.height() : thumb.height();
        String console = item.definition().id().getPath();
        DualScreen dual = DualScreen.of(console);
        if (dual != null || console.equals("gba_sp")) {
            matrices.pushPose();
            fold(matrices, FoldablePose.LID_ANGLE);
            drawScreen(
                    rect,
                    texture,
                    w,
                    h,
                    dual == null ? null : dual.crop(false),
                    matrices,
                    vertices);
            matrices.popPose();
        } else drawScreen(rect, texture, w, h, null, matrices, vertices);
        if (dual != null) {
            matrices.pushPose();
            fold(matrices, FoldablePose.BASE_ANGLE);
            drawScreen(
                    TOUCH_SCREENS.get(console), texture, w, h, dual.crop(true), matrices, vertices);
            matrices.popPose();
        }
    }

    private static void fold(PoseStack matrices, float angle) {
        float x = FoldablePose.HINGE_X / 16,
                y = FoldablePose.HINGE_Y / 16,
                z = FoldablePose.HINGE_Z / 16;
        matrices.translate(x, y, z);
        matrices.mulPose(Axis.XP.rotationDegrees(angle));
        matrices.translate(-x, -y, -z);
    }

    private static void drawScreen(
            float[] rect,
            ResourceLocation texture,
            int w,
            int h,
            DualScreen.Crop crop,
            PoseStack matrices,
            MultiBufferSource vertices) {
        float u0 = crop == null ? 0 : crop.u0(),
                v0 = crop == null ? 0 : crop.v0(),
                u1 = crop == null ? 1 : crop.u1(),
                v1 = crop == null ? 1 : crop.v1();
        float x0 = rect[0] / 16,
                x1 = rect[1] / 16,
                y0 = rect[2] / 16,
                y1 = rect[3] / 16,
                z = rect[4] / 16 + .002f;
        float sw = w * (u1 - u0), sh = h * (v1 - v0);
        float fit = Math.min((x1 - x0) / sw, (y1 - y0) / sh), fw = sw * fit, fh = sh * fit;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        Matrix4f matrix = matrices.last().pose();
        quad(
                vertices.getBuffer(com.sammwy.retrocraft.compat.Materials.entitySolid(texture)),
                matrix,
                x0,
                y0,
                x1,
                y1,
                z,
                0,
                u0,
                v0,
                u1,
                v1);
        quad(
                vertices.getBuffer(com.sammwy.retrocraft.compat.Materials.cutout(texture)),
                matrix,
                cx - fw / 2,
                cy - fh / 2,
                cx + fw / 2,
                cy + fh / 2,
                z + .001f,
                255,
                u0,
                v0,
                u1,
                v1);
    }

    /**
     * South-facing quad over the LCD; viewed from +z the viewer's left is -x, so u=0 maps to x0 and
     * v=0 to y1.
     */
    private static void quad(
            VertexConsumer buffer,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            float z,
            int rgb,
            float u0,
            float v0,
            float u1,
            float v1) {
        int light = 0xf000f0, overlay = OverlayTexture.NO_OVERLAY;
        Vertices.vertex(buffer, matrix, x0, y1, z, u0, v0, rgb, overlay, light, 1);
        Vertices.vertex(buffer, matrix, x0, y0, z, u0, v1, rgb, overlay, light, 1);
        Vertices.vertex(buffer, matrix, x1, y0, z, u1, v1, rgb, overlay, light, 1);
        Vertices.vertex(buffer, matrix, x1, y1, z, u1, v0, rgb, overlay, light, 1);
    }

    private Thumbnails() {}
}
