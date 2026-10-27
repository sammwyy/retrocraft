package com.sammwy.retrocraft.platform.forge;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.platform.Network;
import com.sammwy.retrocraft.platform.Platform;
import com.sammwy.retrocraft.server.command.RetrocraftCommands;
import com.sammwy.retrocraft.server.console.ConsoleHost;
import com.sammwy.retrocraft.server.screen.ConsoleOpenData;
import com.sammwy.retrocraft.server.screen.ConsoleScreenHandler;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;

@Mod("retrocraft")
public final class ForgePlatform implements Platform.Backend {
    static final ForgeNetwork NETWORK = new ForgeNetwork();
    private final List<Registration<?, ?>> registrations = new ArrayList<>();

    private static final class Registration<V, T extends V> implements Supplier<T> {
        private final Registry<V> registry;
        private final ResourceLocation id;
        private final Supplier<T> factory;
        private T value;

        Registration(Registry<V> registry, ResourceLocation id, Supplier<T> factory) {
            this.registry = registry;
            this.id = id;
            this.factory = factory;
        }

        void apply(RegisterEvent event) {
            event.register(
                    registry.key(),
                    id,
                    () -> {
                        value = factory.get();
                        return value;
                    });
        }

        @Override
        public T get() {
            if (value == null)
                throw new IllegalStateException("Content is not registered yet: " + id);
            return value;
        }
    }

    public ForgePlatform(FMLJavaModLoadingContext context) {
        com.sammwy.retrocraft.compat.ForgeEvents.initialize(context);
        Platform.install(this);
        Network.install(NETWORK);
        Retrocraft.initialize();
        NETWORK.finish();
        com.sammwy.retrocraft.compat.ForgeEvents.mod(
                RegisterEvent.class,
                (RegisterEvent event) -> registrations.forEach(entry -> entry.apply(event)));
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                RegisterCommandsEvent.class,
                (RegisterCommandsEvent event) ->
                        RetrocraftCommands.register(event.getDispatcher(), Retrocraft::instances));
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                ServerStartedEvent.class,
                (ServerStartedEvent event) -> Retrocraft.start(event.getServer()));
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                ServerStoppingEvent.class,
                (ServerStoppingEvent event) -> {
                    Retrocraft.stop();
                    NETWORK.clear();
                });
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                TickEvent.ServerTickEvent.Post.class,
                (TickEvent.ServerTickEvent.Post event) -> {
                    if (Retrocraft.instances() != null) Retrocraft.instances().tick();
                });
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                EntityJoinLevelEvent.class,
                (EntityJoinLevelEvent event) -> {
                    if (!event.getLevel().isClientSide() && Retrocraft.instances() != null)
                        Retrocraft.instances().entityLoaded(event.getEntity());
                });
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                EntityLeaveLevelEvent.class,
                (EntityLeaveLevelEvent event) -> {
                    if (!event.getLevel().isClientSide()) {
                        NETWORK.unload(event.getEntity());
                        if (Retrocraft.instances() != null)
                            Retrocraft.instances().entityUnloaded(event.getEntity());
                    }
                });
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                PlayerEvent.PlayerLoggedInEvent.class,
                (PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player
                            && Retrocraft.instances() != null)
                        Retrocraft.instances().sendCatalog(player);
                });
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                PlayerEvent.PlayerLoggedOutEvent.class,
                (PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        NETWORK.disconnect(player);
                        if (Retrocraft.instances() != null)
                            Retrocraft.instances().disconnect(player.getUUID());
                    }
                });
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                PlayerEvent.StartTracking.class,
                (PlayerEvent.StartTracking event) -> {
                    if (event.getEntity() instanceof ServerPlayer player)
                        NETWORK.track(event.getTarget(), player);
                });
        com.sammwy.retrocraft.compat.ForgeServerEvents.listen(
                PlayerEvent.StopTracking.class,
                (PlayerEvent.StopTracking event) -> {
                    if (event.getEntity() instanceof ServerPlayer player)
                        NETWORK.untrack(event.getTarget(), player);
                });
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT)
            ForgeClient.install();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public String version() {
        return ModList.get()
                .getModContainerById("retrocraft")
                .orElseThrow()
                .getModInfo()
                .getVersion()
                .toString();
    }

    @Override
    public <V, T extends V> Supplier<T> register(
            Registry<V> registry, ResourceLocation id, Supplier<T> factory) {
        var entry = new Registration<>(registry, id, factory);
        registrations.add(entry);
        return entry;
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            Platform.BlockEntityFactory<T> factory, Block... blocks) {
        return com.sammwy.retrocraft.compat.ForgeTypes.create(factory, blocks);
    }

    @Override
    public MenuType<ConsoleScreenHandler> consoleMenuType() {
        return IForgeMenuType.create(
                (id, inventory, buffer) ->
                        new ConsoleScreenHandler(
                                id,
                                inventory,
                                new ConsoleOpenData(
                                        ResourceLocation.STREAM_CODEC.decode(buffer),
                                        buffer.readInt())));
    }

    @Override
    public void openConsoleMenu(ServerPlayer player, ConsoleHost host, int lockedSlot) {
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, user) ->
                                new ConsoleScreenHandler(id, inventory, host, lockedSlot),
                        host.definition().displayName()),
                buffer -> {
                    ResourceLocation.STREAM_CODEC.encode(buffer, host.definition().id());
                    buffer.writeInt(lockedSlot);
                });
    }

    @Override
    public CreativeModeTab creativeTab(
            Component title,
            Supplier<ItemStack> icon,
            CreativeModeTab.DisplayItemsGenerator entries) {
        return CreativeModeTab.builder().title(title).icon(icon).displayItems(entries).build();
    }
}
