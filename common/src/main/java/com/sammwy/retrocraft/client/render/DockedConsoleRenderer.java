package com.sammwy.retrocraft.client.render;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sammwy.retrocraft.client.ClientPreferences;
import com.sammwy.retrocraft.compat.ConsoleRenderer;
import com.sammwy.retrocraft.compat.Vertices;
import com.sammwy.retrocraft.platform.Ids;
import com.sammwy.retrocraft.server.block.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The casing is a baked vanilla model; this draws the inserted cartridge and, on the arcade
 * cabinet, the live CRT picture.
 */
public final class DockedConsoleRenderer implements ConsoleRenderer<DockedConsoleBlockEntity> {
    /** Roof of the cabinet (model units above 32 cannot live in the block's own JSON model). */
    public static final Map<String, ResourceLocation> TOP_MODELS =
            Arrays.stream(ArcadeSkin.values())
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    ArcadeSkin::model,
                                    skin ->
                                            Ids.of(
                                                    "retrocraft",
                                                    "block/" + skin.model() + "_top")));

    public static List<ResourceLocation> arcadeModels() {
        return Arrays.stream(ArcadeSkin.values())
                .flatMap(
                        skin ->
                                Stream.of(
                                        TOP_MODELS.get(skin.model()),
                                        Ids.of("retrocraft", "block/" + skin.model()),
                                        Ids.of("retrocraft", "block/" + skin.model() + "_on")))
                .toList();
    }

    /**
     * Top-loading slots in model units. Media models face south, so they are turned toward the
     * console's north-facing ports. Front loaders (e.g. the NES-001) hide their media entirely.
     */
    private record Slot(float x, float y, float z, float scale) {}

    private static final Map<String, Slot> EXPOSED_MEDIA =
            Map.of(
                    "snes", new Slot(0, 8.1f, 1.5f, .54f),
                    "n64", new Slot(0, 7.45f, 1.5f, .55f),
                    "megadrive", new Slot(-1, 7.05f, .9f, .59f),
                    "genesis", new Slot(-1, 7.05f, .9f, .59f));

    private static String model(DockedConsoleBlockEntity console) {
        return console.hasInternalRom()
                ? ArcadeSkin.of(console.cartridge().getHoverName().getString()).model()
                : console.definition().id().getPath();
    }

    public DockedConsoleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void draw(
            DockedConsoleBlockEntity console,
            float tickDelta,
            PoseStack matrices,
            MultiBufferSource vertices,
            int light,
            int overlay) {
        String id = console.definition().id().getPath();
        matrices.pushPose();
        matrices.translate(.5, 0, .5);
        float rotation =
                switch (console.getBlockState().getValue(DockedConsoleBlock.FACING)) {
                    case EAST -> -90;
                    case SOUTH -> 180;
                    case WEST -> 90;
                    default -> 0;
                };
        matrices.mulPose(Axis.YP.rotationDegrees(rotation));
        if (console.definition().isArcade()) {
            if (console.hasInternalRom()) casing(console, matrices, vertices, light, overlay);
            roof(console, matrices, vertices, overlay);
            if (console.hasInternalRom() && model(console).equals(ArcadeSkin.GENERIC.model()))
                marquee(console, matrices, vertices);
            crt(console, matrices, vertices);
        } else if (!console.cartridge().isEmpty() && EXPOSED_MEDIA.containsKey(id)) {
            Slot slot = EXPOSED_MEDIA.get(id);
            matrices.translate(slot.x / 16, slot.y / 16, slot.z / 16);
            matrices.mulPose(Axis.YP.rotationDegrees(180));
            matrices.scale(slot.scale, slot.scale, slot.scale);
            com.sammwy.retrocraft.compat.ClientItems.draw(
                    console.cartridge(),
                    console.getLevel(),
                    matrices,
                    vertices,
                    light,
                    overlay,
                    (int) console.getBlockPos().asLong());
        }
        matrices.popPose();
    }

    private static void casing(
            DockedConsoleBlockEntity console,
            PoseStack matrices,
            MultiBufferSource vertices,
            int light,
            int overlay) {
        ResourceLocation id =
                Ids.of(
                        "retrocraft",
                        "block/" + model(console) + (console.instance() == null ? "" : "_on"));
        matrices.pushPose();
        matrices.translate(-.5, 0, -.5);
        com.sammwy.retrocraft.platform.ClientPlatform.model(
                id, console, matrices, vertices, light, overlay, false);
        matrices.popPose();
    }

    /** Unrecognized sets still have a personalized marquee, without guessing another game's art. */
    private static void marquee(
            DockedConsoleBlockEntity console, PoseStack matrices, MultiBufferSource vertices) {
        var font = Minecraft.getInstance().font;
        String name = console.cartridge().getHoverName().getString();
        if (name.isEmpty()) return;
        float scale = Math.min(.008f, 1.1f / Math.max(1, font.width(name)));
        matrices.pushPose();
        matrices.translate(0, 35.5 / 16, 2.77 / 16 - .5);
        matrices.scale(-scale, -scale, scale);
        com.sammwy.retrocraft.compat.WorldText.draw(
                font, name, -font.width(name) / 2f, -4, 0xffbcebf2, matrices, vertices, 0xff141923);
        matrices.popPose();
    }

    private static void roof(
            DockedConsoleBlockEntity console,
            PoseStack matrices,
            MultiBufferSource vertices,
            int overlay) {
        ResourceLocation top = TOP_MODELS.get(model(console));
        if (top == null || console.getLevel() == null) return;
        matrices.pushPose();
        matrices.translate(-.5, 2, -.5);
        com.sammwy.retrocraft.platform.ClientPlatform.model(
                top, console, matrices, vertices, 0xf000f0, overlay, true);
        matrices.popPose();
    }

    private static void crt(
            DockedConsoleBlockEntity console, PoseStack matrices, MultiBufferSource vertices) {
        // Tilt around the same lower glass edge as the baked model, keeping live video flush.
        matrices.pushPose();
        matrices.translate(0, ArcadeCabinet.SCREEN_Y0 / 16, ArcadeCabinet.SCREEN_Z / 16 - .5);
        matrices.mulPose(Axis.XP.rotationDegrees(ArcadeCabinet.SCREEN_TILT));
        matrices.translate(0, -ArcadeCabinet.SCREEN_Y0 / 16, .5 - ArcadeCabinet.SCREEN_Z / 16);
        drawScreen(
                ScreenTextures.get(console.instance()),
                matrices,
                vertices,
                ArcadeCabinet.SCREEN_X0,
                ArcadeCabinet.SCREEN_X1,
                ArcadeCabinet.SCREEN_Y0,
                ArcadeCabinet.SCREEN_Y1,
                ArcadeCabinet.SCREEN_Z);
        matrices.popPose();
    }

    /**
     * Emulator frame fitted inside a glass rectangle (model units, front = north, matrices at the
     * block centre), full bright over a black letterbox.
     */
    static void drawScreen(
            ScreenTextures.Entry entry,
            PoseStack matrices,
            MultiBufferSource vertices,
            float x0,
            float x1,
            float y0,
            float y1,
            float zUnits) {
        if (entry == null || !ClientPreferences.get().renderWorldScreens) return;
        float gw = (x1 - x0) / 16f, gh = (y1 - y0) / 16f;
        float fit = Math.min(gw / entry.width(), gh / entry.height());
        float w = entry.width() * fit, h = entry.height() * fit;
        float cx = (x0 + x1) / 32f - .5f, cy = (y0 + y1) / 32f, z = zUnits / 16f - .5f;
        var matrix = matrices.last().pose();
        var black =
                vertices.getBuffer(com.sammwy.retrocraft.compat.Materials.entitySolid(entry.id()));
        quad(
                black,
                matrix,
                cx - gw / 2,
                cy - gh / 2,
                cx + gw / 2,
                cy + gh / 2,
                z - .002f,
                0,
                0,
                0,
                0,
                0);
        com.sammwy.retrocraft.compat.ProjectionCapture.capture(
                vertices,
                entry.id(),
                matrix,
                cx - w / 2,
                cy - h / 2,
                cx + w / 2,
                cy + h / 2,
                z - .0045f);
        var glass = vertices.getBuffer(com.sammwy.retrocraft.compat.Materials.cutout(entry.id()));
        quad(
                glass,
                matrix,
                cx - w / 2,
                cy - h / 2,
                cx + w / 2,
                cy + h / 2,
                z - .0045f,
                0,
                0,
                1,
                1,
                255);
    }

    /**
     * Quad facing -z (the cabinet front). Seen from the front, +x is the viewer's left, so u=0 maps
     * to x1.
     */
    private static void quad(
            VertexConsumer buffer,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            float z,
            float u0,
            float v0,
            float u1,
            float v1,
            int rgb) {
        int light = 0xf000f0, overlay = OverlayTexture.NO_OVERLAY;
        Vertices.vertex(buffer, matrix, x1, y1, z, u0, v0, rgb, overlay, light, -1);
        Vertices.vertex(buffer, matrix, x1, y0, z, u0, v1, rgb, overlay, light, -1);
        Vertices.vertex(buffer, matrix, x0, y0, z, u1, v1, rgb, overlay, light, -1);
        Vertices.vertex(buffer, matrix, x0, y1, z, u1, v0, rgb, overlay, light, -1);
    }
}
