package com.sammwy.retrocraft.compat;

import net.minecraft.client.input.CharacterEvent;

final class Characters {
    static CharacterEvent create(char chr, int modifiers) {
        return new CharacterEvent(chr);
    }

    static int modifiers(CharacterEvent event) {
        return 0;
    }

    private Characters() {}
}
