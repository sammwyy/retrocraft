package com.sammwy.retrocraft.compat;

import net.minecraft.client.input.CharacterEvent;

final class Characters {
    static CharacterEvent create(char chr, int modifiers) {
        return new CharacterEvent(chr, modifiers);
    }

    static int modifiers(CharacterEvent event) {
        return event.modifiers();
    }

    private Characters() {}
}
