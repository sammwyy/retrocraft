package com.sammwy.retrocraft.compat;

import com.sammwy.retrocraft.mixin.ItemModelPropertiesAccessor;
import com.sammwy.retrocraft.platform.ClientPlatform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ItemModels {
    public static void register(
            Item item, ResourceLocation id, ClientPlatform.ItemPredicate predicate) {
        ItemModelPropertiesAccessor.retrocraft$register(item, id, predicate::value);
    }

    public static void bootstrap() {}

    private ItemModels() {}
}
