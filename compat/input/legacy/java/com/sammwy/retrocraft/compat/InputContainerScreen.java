package com.sammwy.retrocraft.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class InputContainerScreen<T extends AbstractContainerMenu>
        extends SizedContainerScreen<T> {
    protected InputContainerScreen(
            T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
    }
}
