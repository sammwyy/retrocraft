package com.sammwy.retrocraft.platform;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Stable, typed access to the existing block-entity fields across Minecraft's storage APIs. */
public final class StateData {
    public interface Input {
        UUID uuid(String key);

        Long longValue(String key);

        ItemStack stack(String key);

        CompoundTag compound(String key);
    }

    public interface Output {
        void uuid(String key, UUID value);

        void longValue(String key, long value);

        void stack(String key, ItemStack value);

        void compound(String key, CompoundTag value);
    }

    private StateData() {}
}
