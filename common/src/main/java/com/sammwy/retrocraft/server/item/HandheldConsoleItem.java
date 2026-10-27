package com.sammwy.retrocraft.server.item;

import java.util.List;
import java.util.UUID;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.compat.ConsoleItem;
import com.sammwy.retrocraft.compat.ContentProperties;
import com.sammwy.retrocraft.compat.TagData;
import com.sammwy.retrocraft.server.console.*;
import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.screen.ConsoleMenus;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class HandheldConsoleItem extends ConsoleItem {
    private final ConsoleDefinition definition;

    public HandheldConsoleItem(ConsoleDefinition definition) {
        super(ContentProperties.item(definition.id(), new Properties().stacksTo(1)));
        this.definition = definition;
    }

    public ConsoleDefinition definition() {
        return definition;
    }

    @Override
    protected void activate(Level world, Player player, ItemStack stack) {
        if (!world.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && Retrocraft.instances() != null)
            ConsoleMenus.open(serverPlayer, new HandheldHost(serverPlayer, stack));
    }

    /** Installed components, one line each. */
    @Override
    public void tooltip(ItemStack stack, List<Component> tooltip) {
        CompoundTag components = components(stack);
        for (var slot : definition.componentSlots()) {
            ItemStack installed = ComponentStorage.read(components, slot);
            if (!installed.isEmpty())
                tooltip.add(
                        Component.literal("+ ")
                                .append(installed.getHoverName())
                                .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** Copy of the stack's {@link ComponentStorage} compound. */
    public static CompoundTag components(ItemStack stack) {
        return TagData.compound(
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(),
                ComponentStorage.KEY);
    }

    public static void components(ItemStack stack, CompoundTag components) {
        CompoundTag data =
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (components.isEmpty()) data.remove(ComponentStorage.KEY);
        else data.put(ComponentStorage.KEY, components);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static UUID instance(ItemStack stack) {
        CompoundTag nbt =
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return TagData.uuid(nbt, "InstanceID");
    }

    public static void instance(ItemStack stack, UUID id) {
        CompoundTag data =
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (id == null) data.remove("InstanceID");
        else TagData.uuid(data, "InstanceID", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
}
