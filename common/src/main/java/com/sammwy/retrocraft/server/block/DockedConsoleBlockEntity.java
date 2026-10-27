package com.sammwy.retrocraft.server.block;

import java.util.*;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.compat.PersistentBlockEntity;
import com.sammwy.retrocraft.platform.StateData;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.item.ArcadeItem;
import com.sammwy.retrocraft.server.item.CartridgeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class DockedConsoleBlockEntity extends PersistentBlockEntity implements ConsoleHost {
    private ItemStack cartridge = ItemStack.EMPTY;
    private UUID instance;
    private BlockPos tv;

    private final ItemStack[] components;

    public DockedConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(((DockedConsoleBlock) state.getBlock()).entityType(), pos, state);
        components = new ItemStack[definition().componentSlots().size()];
        Arrays.fill(components, ItemStack.EMPTY);
    }

    @Override
    public ItemStack component(int slot) {
        return slot >= 0 && slot < components.length ? components[slot] : ItemStack.EMPTY;
    }

    @Override
    public void component(int slot, ItemStack stack) {
        if (slot < 0 || slot >= components.length) return;
        components[slot] =
                definition().componentSlots().get(slot).accepts(ComponentStorage.component(stack))
                        ? stack.copyWithCount(1)
                        : ItemStack.EMPTY;
        changed();
    }

    public List<ItemStack> componentStacks() {
        return List.of(components);
    }

    @Override
    public ConsoleDefinition definition() {
        return ((DockedConsoleBlock) getBlockState().getBlock()).definition();
    }

    @Override
    public ItemStack cartridge() {
        return cartridge;
    }

    @Override
    public void cartridge(ItemStack stack) {
        if (hasInternalRom()) return;
        cartridge = stack;
        changed();
    }

    public void installInternalRom(ItemStack stack) {
        if (!hasInternalRom()
                || !cartridge.isEmpty()
                || !stack.is(definition().cartridge())
                || CartridgeItem.gameId(stack).isEmpty()) return;
        cartridge = stack.copyWithCount(1);
        changed();
    }

    public ItemStack arcadeStack() {
        return ((ArcadeItem) getBlockState().getBlock().asItem())
                .game(CartridgeItem.gameId(cartridge), cartridge.getHoverName().getString());
    }

    @Override
    public UUID instance() {
        return instance;
    }

    @Override
    public void instance(UUID id) {
        instance = id;
        changed();
    }

    public BlockPos tv() {
        return tv;
    }

    public void tv(BlockPos pos) {
        tv = pos;
        changed();
    }

    @Override
    public Vec3 soundPosition() {
        if (tv != null && level != null && level.getBlockEntity(tv) instanceof TvBlockEntity set)
            return Vec3.atCenterOf(tv).add(0, set.size().height / 32.0, 0);
        return position();
    }

    private void changed() {
        setChanged();
        if (level == null || level.isClientSide()) return;
        BlockState before = getBlockState(),
                after = before.setValue(DockedConsoleBlock.POWERED, instance != null);
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
    public ServerLevel world() {
        return (ServerLevel) level;
    }

    @Override
    public Vec3 position() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public boolean valid() {
        return !isRemoved()
                && level instanceof ServerLevel
                && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this;
    }

    @Override
    public boolean canAccess(ServerPlayer player) {
        return valid() && player.level() == level && player.distanceToSqr(position()) <= 64;
    }

    @Override
    protected void saveState(StateData.Output nbt) {
        nbt.stack(hasInternalRom() ? "InternalRom" : "Cartridge", cartridge);
        if (instance != null) nbt.uuid("InstanceID", instance);
        if (tv != null) nbt.longValue("Tv", tv.asLong());
        CompoundTag installed = new CompoundTag();
        var slots = definition().componentSlots();
        for (int i = 0; i < components.length; i++)
            ComponentStorage.write(installed, slots.get(i), components[i]);
        if (!installed.isEmpty()) nbt.compound(ComponentStorage.KEY, installed);
    }

    @Override
    protected void loadState(StateData.Input nbt) {
        cartridge = nbt.stack(hasInternalRom() ? "InternalRom" : "Cartridge");
        instance = nbt.uuid("InstanceID");
        tv = nbt.longValue("Tv") == null ? null : BlockPos.of(nbt.longValue("Tv"));
        CompoundTag installed = nbt.compound(ComponentStorage.KEY);
        var slots = definition().componentSlots();
        for (int i = 0; i < components.length; i++)
            components[i] = ComponentStorage.read(installed, slots.get(i));
    }

    public static void tick(
            Level world, BlockPos pos, BlockState state, DockedConsoleBlockEntity console) {
        if (state.getValue(DockedConsoleBlock.POWERED) != (console.instance != null))
            console.changed();
        if (world.getGameTime() % 20 == 0
                && console.instance != null
                && Retrocraft.instances() != null) Retrocraft.instances().discover(console);
    }
}
