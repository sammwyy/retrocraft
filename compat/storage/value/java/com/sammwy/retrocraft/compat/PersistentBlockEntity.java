package com.sammwy.retrocraft.compat;

import java.util.UUID;

import com.sammwy.retrocraft.platform.StateData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class PersistentBlockEntity extends LifecycleBlockEntity {
    protected PersistentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void saveState(StateData.Output output);

    protected abstract void loadState(StateData.Input input);

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveState(new Output(output));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadState(new Input(input));
    }

    private record Input(ValueInput input) implements StateData.Input {
        public UUID uuid(String key) {
            return input.read(key, UUIDUtil.CODEC).orElse(null);
        }

        public Long longValue(String key) {
            return input.getLong(key).orElse(null);
        }

        public ItemStack stack(String key) {
            return input.read(key, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }

        public CompoundTag compound(String key) {
            return input.read(key, CompoundTag.CODEC).orElseGet(CompoundTag::new);
        }
    }

    private record Output(ValueOutput output) implements StateData.Output {
        public void uuid(String key, UUID value) {
            output.store(key, UUIDUtil.CODEC, value);
        }

        public void longValue(String key, long value) {
            output.putLong(key, value);
        }

        public void stack(String key, ItemStack value) {
            output.store(key, ItemStack.OPTIONAL_CODEC, value);
        }

        public void compound(String key, CompoundTag value) {
            output.store(key, CompoundTag.CODEC, value);
        }
    }
}
