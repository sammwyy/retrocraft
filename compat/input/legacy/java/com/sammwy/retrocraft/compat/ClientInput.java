package com.sammwy.retrocraft.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

public final class ClientInput {
    public static long window(Window window) {
        return window.getWindow();
    }

    public static boolean keyDown(int key) {
        return InputConstants.isKeyDown(window(Minecraft.getInstance().getWindow()), key);
    }

    public static boolean matches(KeyMapping binding, int key, int scan) {
        return binding.matches(key, scan);
    }

    public static boolean key(EditBox field, int key, int scan, int modifiers) {
        return field.keyPressed(key, scan, modifiers);
    }

    public static boolean character(EditBox field, char chr, int modifiers) {
        return field.charTyped(chr, modifiers);
    }

    public static boolean click(AbstractWidget widget, double x, double y, int button) {
        return widget.mouseClicked(x, y, button);
    }

    public static void activate(AbstractWidget widget) {
        widget.onClick(widget.getX(), widget.getY());
    }

    public static KeyMapping key(String name, InputConstants.Type type, int key, String category) {
        return new KeyMapping(name, type, key, category);
    }

    public static boolean screenKey(
            net.minecraft.client.gui.screens.Screen screen, int key, int scan, int modifiers) {
        return screen.keyPressed(key, scan, modifiers);
    }

    private ClientInput() {}
}
