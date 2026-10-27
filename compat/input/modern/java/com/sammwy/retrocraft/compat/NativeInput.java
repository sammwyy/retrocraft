package com.sammwy.retrocraft.compat;

import java.util.function.Supplier;

import net.minecraft.client.input.*;

final class NativeInput {
    private record Mouse(MouseButtonEvent event, boolean doubled) {}

    private static final ThreadLocal<Mouse> MOUSE = new ThreadLocal<>();
    private static final ThreadLocal<CharacterEvent> CHARACTER = new ThreadLocal<>();

    static boolean dispatch(MouseButtonEvent event, boolean doubled, Supplier<Boolean> callback) {
        var previous = MOUSE.get();
        MOUSE.set(new Mouse(event, doubled));
        try {
            return callback.get();
        } finally {
            if (previous == null) MOUSE.remove();
            else MOUSE.set(previous);
        }
    }

    static MouseButtonEvent mouse(double x, double y, int button) {
        var current = MOUSE.get();
        if (current != null
                && current.event.x() == x
                && current.event.y() == y
                && current.event.button() == button) return current.event;
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    static boolean doubled() {
        return MOUSE.get() != null && MOUSE.get().doubled;
    }

    static boolean character(CharacterEvent event, Supplier<Boolean> callback) {
        var previous = CHARACTER.get();
        CHARACTER.set(event);
        try {
            return callback.get();
        } finally {
            if (previous == null) CHARACTER.remove();
            else CHARACTER.set(previous);
        }
    }

    static CharacterEvent character(char chr, int modifiers) {
        var current = CHARACTER.get();
        return current != null && (char) current.codepoint() == chr
                ? current
                : Characters.create(chr, modifiers);
    }
}
