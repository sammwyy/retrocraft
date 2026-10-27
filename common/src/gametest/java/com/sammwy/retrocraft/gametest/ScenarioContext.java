package com.sammwy.retrocraft.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Keep diagnostic strings and scenario code independent from GameTest API changes. */
public record ScenarioContext(GameTestHelper helper) {
    public void assertTrue(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public ServerLevel getLevel() {
        return helper.getLevel();
    }

    public BlockPos absolutePos(BlockPos pos) {
        return helper.absolutePos(pos);
    }

    public ServerPlayer makeMockServerPlayerInLevel() {
        return helper.makeMockServerPlayerInLevel();
    }

    public void succeed() {
        helper.succeed();
    }
}
