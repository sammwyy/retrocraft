package com.sammwy.retrocraft.server.block;

import java.util.UUID;

import com.sammwy.retrocraft.compat.PersistentBlockEntity;
import com.sammwy.retrocraft.platform.StateData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds the emulation instance currently shown on this set and the console block it is bound to.
 */
public final class TvBlockEntity extends PersistentBlockEntity {
    private UUID instance;
    private BlockPos console;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(((TvBlock) state.getBlock()).entityType(), pos, state);
    }

    public TvBlock.Size size() {
        return ((TvBlock) getBlockState().getBlock()).size;
    }

    public UUID instance() {
        return instance;
    }

    public BlockPos console() {
        return console;
    }

    public boolean free() {
        return instance == null;
    }

    public void bind(UUID id, BlockPos consolePos) {
        instance = id;
        console = consolePos;
        changed();
    }

    public void unbind() {
        instance = null;
        console = null;
        changed();
    }

    private void changed() {
        setChanged();
        if (level == null || level.isClientSide()) return;
        BlockState before = getBlockState(),
                after = before.setValue(TvBlock.POWERED, instance != null);
        if (!before.equals(after)) level.setBlock(worldPosition, after, Block.UPDATE_CLIENTS);
        else level.sendBlockUpdated(worldPosition, before, after, Block.UPDATE_CLIENTS);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void saveState(StateData.Output nbt) {
        if (instance != null) nbt.uuid("InstanceID", instance);
        if (console != null) nbt.longValue("Console", console.asLong());
    }

    @Override
    protected void loadState(StateData.Input nbt) {
        instance = nbt.uuid("InstanceID");
        console = nbt.longValue("Console") == null ? null : BlockPos.of(nbt.longValue("Console"));
    }

    /**
     * Drops a binding whose console vanished or moved on to another instance, so the set never
     * stays orphaned.
     */
    public static void tick(Level world, BlockPos pos, BlockState state, TvBlockEntity tv) {
        if (state.getValue(TvBlock.POWERED) != (tv.instance != null)) tv.changed();
        if (world.getGameTime() % 40 != 0 || tv.instance == null) return;
        if (tv.console == null || !world.hasChunkAt(tv.console)) return;
        if (!(world.getBlockEntity(tv.console) instanceof DockedConsoleBlockEntity console)
                || !tv.instance.equals(console.instance())
                || !pos.equals(console.tv())) tv.unbind();
    }
}
