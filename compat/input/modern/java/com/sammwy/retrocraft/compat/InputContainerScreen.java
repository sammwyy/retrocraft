package com.sammwy.retrocraft.compat;

import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class InputContainerScreen<T extends AbstractContainerMenu>
        extends SizedContainerScreen<T> {
    protected InputContainerScreen(
            T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return NativeInput.dispatch(
                event, doubled, () -> mouseClicked(event.x(), event.y(), event.button()));
    }

    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(NativeInput.mouse(x, y, button), NativeInput.doubled());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return NativeInput.dispatch(
                event, false, () -> mouseReleased(event.x(), event.y(), event.button()));
    }

    public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(NativeInput.mouse(x, y, button));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return NativeInput.dispatch(
                event, false, () -> mouseDragged(event.x(), event.y(), event.button(), dx, dy));
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(NativeInput.mouse(x, y, button), dx, dy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        return super.keyPressed(new KeyEvent(key, scan, modifiers));
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return keyReleased(event.key(), event.scancode(), event.modifiers());
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        return super.keyReleased(new KeyEvent(key, scan, modifiers));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return NativeInput.character(
                event, () -> charTyped((char) event.codepoint(), Characters.modifiers(event)));
    }

    public boolean charTyped(char chr, int modifiers) {
        return super.charTyped(NativeInput.character(chr, modifiers));
    }
}
