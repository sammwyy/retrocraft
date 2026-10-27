package com.sammwy.retrocraft.server.block;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.sammwy.retrocraft.Retrocraft;
import com.sammwy.retrocraft.compat.ConsoleBlock;
import com.sammwy.retrocraft.compat.LifecycleBlock;
import com.sammwy.retrocraft.server.console.ConsoleDefinition;
import com.sammwy.retrocraft.server.console.ConsoleRegistry;
import com.sammwy.retrocraft.server.console.component.ComponentStorage;
import com.sammwy.retrocraft.server.item.CartridgeItem;
import com.sammwy.retrocraft.server.screen.ConsoleMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DockedConsoleBlock extends ConsoleBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final ConsoleDefinition definition;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public DockedConsoleBlock(ConsoleDefinition definition, Properties settings) {
        super(settings);
        this.definition = definition;
        var bounds =
                definition.bounds() == null
                        ? ConsoleDefinition.Bounds.DEFAULT
                        : definition.bounds();
        for (Direction facing : Direction.Plane.HORIZONTAL)
            shapes.put(facing, bounds.shape(facing));
        registerDefaultState(
                getStateDefinition()
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** The arcade cabinets are three blocks tall, so both blocks above must be free. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return !definition.isArcade()
                || world.getBlockState(pos.above()).canBeReplaced()
                        && world.getBlockState(pos.above(2)).canBeReplaced();
    }

    public ConsoleDefinition definition() {
        return definition;
    }

    public BlockEntityType<DockedConsoleBlockEntity> entityType() {
        return ConsoleRegistry.consoleEntityType(definition.id());
    }

    @Override
    protected MapCodec<? extends LifecycleBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DockedConsoleBlockEntity(pos, state);
    }

    @Override
    protected ItemStack cloneConsole(LevelReader world, BlockPos pos, BlockState state) {
        if (definition.internalRom()
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && !CartridgeItem.gameId(console.cartridge()).isEmpty())
            return console.arcadeStack();
        return null;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var drops = super.getDrops(state, builder);
        if (definition.internalRom()
                && builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                        instanceof DockedConsoleBlockEntity console
                && !CartridgeItem.gameId(console.cartridge()).isEmpty()) {
            for (int i = 0; i < drops.size(); i++)
                if (drops.get(i).is(asItem())) drops.set(i, console.arcadeStack());
        }
        return drops;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return definition.internalRom() ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide()
                ? null
                : createTickerHelper(type, entityType(), DockedConsoleBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!world.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console)
            ConsoleMenus.open(serverPlayer, console);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean insert(
            ItemStack stack,
            BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!definition.internalRom()
                && stack.is(definition.cartridge())
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && console.instance() == null
                && console.cartridge().isEmpty()) {
            if (!world.isClientSide()) {
                console.cartridge(stack.copyWithCount(1));
                if (!player.isCreative()) stack.shrink(1);
            }
            return true;
        }
        var component = ComponentStorage.component(stack);
        if (component != null
                && world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console
                && console.instance() == null) {
            var slots = definition.componentSlots();
            for (int i = 0; i < slots.size(); i++)
                if (slots.get(i).accepts(component) && console.component(i).isEmpty()) {
                    if (!world.isClientSide()) {
                        console.component(i, stack.copyWithCount(1));
                        if (!player.isCreative()) stack.shrink(1);
                    }
                    return true;
                }
        }
        return false;
    }

    @Override
    public void removed(Level world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof DockedConsoleBlockEntity console) {
            if (!world.isClientSide() && Retrocraft.instances() != null)
                Retrocraft.instances().powerOff(console);
            if (!console.hasInternalRom())
                Containers.dropItemStack(
                        world,
                        pos.getX() + .5,
                        pos.getY() + .5,
                        pos.getZ() + .5,
                        console.cartridge());
            for (ItemStack component : console.componentStacks())
                Containers.dropItemStack(
                        world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, component);
        }
    }
}
