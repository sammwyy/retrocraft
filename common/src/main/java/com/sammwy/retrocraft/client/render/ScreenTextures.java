package com.sammwy.retrocraft.client.render;

import java.util.*;

import com.mojang.blaze3d.platform.NativeImage;
import com.sammwy.retrocraft.compat.ClientGraphics;
import com.sammwy.retrocraft.compat.TextureImages;
import com.sammwy.retrocraft.cores.stream.FrameCodec;
import com.sammwy.retrocraft.platform.Ids;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Decoded emulator frames as GPU textures, shared by the play screen and the in-world cabinet
 * renderer.
 */
public final class ScreenTextures {
    private static final long FROZEN_TTL = 60_000_000_000L;

    public record Entry(ResourceLocation id, int width, int height, long at) {}

    private static final Map<UUID, DynamicTexture> TEXTURES = new HashMap<>();
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    /**
     * Streams that stopped but whose console may still be on: keep the last frame on the glass
     * briefly.
     */
    private static final Map<UUID, Long> FROZEN = new HashMap<>();

    public static Entry upload(UUID instance, FrameCodec.Frame frame) {
        FROZEN.remove(instance);
        Entry entry = ENTRIES.get(instance);
        DynamicTexture texture = TEXTURES.get(instance);
        if (texture == null || entry.width != frame.width() || entry.height != frame.height()) {
            release(instance);
            texture = TextureImages.create(new NativeImage(frame.width(), frame.height(), false));
            ResourceLocation id = Ids.of("retrocraft", "console/" + instance);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            entry = new Entry(id, frame.width(), frame.height(), 0);
            TEXTURES.put(instance, texture);
            ENTRIES.put(instance, entry);
        }
        NativeImage image = texture.getPixels();
        byte[] rgb = frame.rgb();
        for (int y = 0; y < entry.height; y++) {
            for (int x = 0; x < entry.width; x++) {
                int i = (y * entry.width + x) * 3;
                ClientGraphics.pixel(
                        image,
                        x,
                        y,
                        0xff000000
                                | (rgb[i] & 255)
                                | ((rgb[i + 1] & 255) << 8)
                                | ((rgb[i + 2] & 255) << 16));
            }
        }
        texture.upload();
        entry = new Entry(entry.id(), entry.width(), entry.height(), System.nanoTime());
        ENTRIES.put(instance, entry);
        return entry;
    }

    public static Entry get(UUID instance) {
        return instance == null ? null : ENTRIES.get(instance);
    }

    /** The stream is actually producing frames right now (not a frozen leftover). */
    static Entry live(UUID instance) {
        Entry entry = get(instance);
        return entry != null && System.nanoTime() - entry.at < 1_000_000_000L ? entry : null;
    }

    public static void freeze(UUID instance) {
        if (ENTRIES.containsKey(instance)) FROZEN.put(instance, System.nanoTime());
    }

    public static void tick() {
        long now = System.nanoTime();
        for (UUID id : List.copyOf(FROZEN.keySet()))
            if (now - FROZEN.get(id) > FROZEN_TTL) release(id);
    }

    public static void clear() {
        for (UUID id : List.copyOf(ENTRIES.keySet())) release(id);
    }

    static void release(UUID instance) {
        FROZEN.remove(instance);
        Entry entry = ENTRIES.remove(instance);
        if (TEXTURES.remove(instance) != null && entry != null)
            Minecraft.getInstance().getTextureManager().release(entry.id);
    }

    private ScreenTextures() {}
}
