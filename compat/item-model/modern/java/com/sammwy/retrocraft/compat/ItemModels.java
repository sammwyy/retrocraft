package com.sammwy.retrocraft.compat;

import java.util.*;

import com.sammwy.retrocraft.mixin.ItemModelPropertiesAccessor;
import com.sammwy.retrocraft.platform.ClientPlatform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ItemModels {
    static final Map<Item, ClientPlatform.ItemPredicate> PREDICATES = new IdentityHashMap<>();
    private static final Set<ResourceLocation> REGISTERED = new HashSet<>();

    public static void register(
            Item item, ResourceLocation id, ClientPlatform.ItemPredicate predicate) {
        PREDICATES.put(item, predicate);
        if (REGISTERED.add(id))
            ItemModelPropertiesAccessor.retrocraft$properties().put(id, PoweredProperty.CODEC);
    }

    public static void bootstrap() {
        var id = com.sammwy.retrocraft.platform.Ids.of("retrocraft", "powered");
        if (REGISTERED.add(id))
            ItemModelPropertiesAccessor.retrocraft$properties().put(id, PoweredProperty.CODEC);
    }

    private ItemModels() {}
}
