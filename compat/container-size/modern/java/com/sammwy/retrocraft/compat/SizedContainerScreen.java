package com.sammwy.retrocraft.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class SizedContainerScreen<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {
    protected SizedContainerScreen(
            T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
    }

    protected abstract void renderBg(GuiGraphics graphics, float delta, int x, int y);
}
