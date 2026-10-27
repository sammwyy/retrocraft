package com.sammwy.retrocraft.server.console;

import java.util.*;

import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.item.HandheldConsoleItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

public final class HandheldHost implements ConsoleHost {
    private final ServerPlayer holder;
    private final ItemEntity dropped;
    private final ItemStack stack;
    private final HandheldConsoleItem console;

    public HandheldHost(ServerPlayer holder, ItemStack stack) {
        this.holder = holder;
        this.stack = stack;
        dropped = null;
        console = (HandheldConsoleItem) stack.getItem();
    }

    public HandheldHost(ItemEntity dropped) {
        this.dropped = dropped;
        stack = dropped.getItem();
        holder = null;
        console = (HandheldConsoleItem) stack.getItem();
    }

    public ItemStack stack() {
        return stack;
    }

    /** The entity carrying the stack: its holder, or the dropped item entity on the ground. */
    public Entity carrier() {
        return holder != null ? holder : dropped;
    }

    public int inventorySlot() {
        if (holder == null) return -1;
        for (int i = 0; i < holder.getInventory().getContainerSize(); i++)
            if (holder.getInventory().getItem(i) == stack) return i;
        return -1;
    }

    @Override
    public ConsoleDefinition definition() {
        return console.definition();
    }

    @Override
    public ItemStack cartridge() {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream()
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public void cartridge(ItemStack cartridge) {
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(cartridge)));
        dirty();
    }

    @Override
    public ItemStack component(int slot) {
        var slots = definition().componentSlots();
        if (slot < 0 || slot >= slots.size()) return ItemStack.EMPTY;
        return ComponentStorage.read(HandheldConsoleItem.components(stack), slots.get(slot));
    }

    @Override
    public void component(int slot, ItemStack component) {
        var slots = definition().componentSlots();
        if (slot < 0 || slot >= slots.size()) return;
        CompoundTag components = HandheldConsoleItem.components(stack);
        ComponentStorage.write(components, slots.get(slot), component);
        HandheldConsoleItem.components(stack, components);
        dirty();
    }

    @Override
    public UUID instance() {
        return HandheldConsoleItem.instance(stack);
    }

    @Override
    public void instance(UUID id) {
        HandheldConsoleItem.instance(stack, id);
        dirty();
    }

    @Override
    public ServerLevel world() {
        return holder != null ? (ServerLevel) holder.level() : (ServerLevel) dropped.level();
    }

    @Override
    public Vec3 position() {
        return holder != null ? holder.position() : dropped.position();
    }

    @Override
    public boolean valid() {
        return !stack.isEmpty()
                && (holder != null
                        ? !holder.hasDisconnected() && inventorySlot() >= 0
                        : !dropped.isRemoved() && dropped.getItem() == stack);
    }

    @Override
    public boolean canAccess(ServerPlayer player) {
        return holder == player && valid();
    }

    private void dirty() {
        if (holder != null) holder.getInventory().setChanged();
        else dropped.setItem(stack);
    }
}
