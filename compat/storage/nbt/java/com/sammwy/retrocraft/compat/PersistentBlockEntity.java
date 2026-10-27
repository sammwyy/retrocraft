package com.sammwy.retrocraft.compat;

import java.util.UUID;

import com.sammwy.retrocraft.platform.StateData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class PersistentBlockEntity extends LifecycleBlockEntity {
    protected PersistentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void saveState(StateData.Output output);

    protected abstract void loadState(StateData.Input input);

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveState(new Output(tag, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadState(new Input(tag, registries));
    }

    private record Input(CompoundTag tag, HolderLookup.Provider registries)
            implements StateData.Input {
        public UUID uuid(String key) {
            return TagData.uuid(tag, key);
        }

        public Long longValue(String key) {
            return TagData.longValue(tag, key);
        }

        public ItemStack stack(String key) {
            return TagData.stack(TagData.compound(tag, key), registries);
        }

        public CompoundTag compound(String key) {
            return TagData.compound(tag, key);
        }
    }

    private record Output(CompoundTag tag, HolderLookup.Provider registries)
            implements StateData.Output {
        public void uuid(String key, UUID value) {
            TagData.uuid(tag, key, value);
        }

        public void longValue(String key, long value) {
            tag.putLong(key, value);
        }

        public void stack(String key, ItemStack value) {
            tag.put(key, TagData.stack(value, registries));
        }

        public void compound(String key, CompoundTag value) {
            tag.put(key, value);
        }
    }
}
