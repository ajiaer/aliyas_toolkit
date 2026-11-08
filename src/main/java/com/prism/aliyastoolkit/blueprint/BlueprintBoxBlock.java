package com.prism.aliyastoolkit.blueprint;

import java.util.List;

import javax.annotation.Nullable;

import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 蓝图箱方块：直接继承原版 {@link ChestBlock}，因此拥有箱子的形状、朝向、
 * 双击/双箱逻辑、比较器以及原版箱子的翻盖动画。
 * <p>
 * 只是把菜单、物品容量、蓝图数据和掉落物品替换成蓝图箱自己的实现。
 */
public class BlueprintBoxBlock extends ChestBlock {
    /** 蓝图剪切掉源方块时，不应该额外生成一份“箱子掉落物”。 */
    private static final ThreadLocal<Boolean> SUPPRESS_DROPS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final BlueprintTier tier;

    public BlueprintBoxBlock(BlueprintTier tier, BlockBehaviour.Properties properties) {
        super(properties, () -> BlueprintBoxes.BLUEPRINT_BOX_BE.get());
        this.tier = tier;
    }

    public BlueprintTier tier() {
        return this.tier;
    }

    /**
     * 蓝图箱永远保持单箱状态；即使左右相邻同类蓝图箱，也不合并成双箱。
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(ChestBlock.TYPE, ChestType.SINGLE);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        return updated.setValue(ChestBlock.TYPE, ChestType.SINGLE);
    }

    /** 蓝图剪切/复制移除源方块时，临时禁止蓝图箱生成掉落物。 */
    public static void withDropsSuppressed(Runnable action) {
        boolean previous = SUPPRESS_DROPS.get();
        SUPPRESS_DROPS.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            SUPPRESS_DROPS.set(previous);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlueprintBoxBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BlueprintBoxBlockEntity box && box.canOpen(player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new BlueprintBoxMenu(containerId, inventory, box),
                        box.getDisplayName()), buffer -> buffer.writeBlockPos(pos));
            }
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 蓝图箱的内容由“箱子物品”承载，而不是像原版箱子一样额外掉一堆内容物。
     * 这里在方块被移除时生成一个带完整数据的箱子物品；同种方块换状态（双箱合并等）不触发。
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock()) || SUPPRESS_DROPS.get()) {
            return;
        }
        if (!level.isClientSide) {
            ItemStack stack = new ItemStack(this.asItem());
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BlueprintBoxBlockEntity box) {
                box.saveToItem(stack, level.registryAccess());
            }
            Block.popResource(level, pos, stack);
        }
    }

    /** 掉落统一由 {@link #onRemove} 生成，避免原版箱子再额外掉一份容器内容。 */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this.asItem());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BlueprintBoxBlockEntity box) {
            box.saveToItem(stack, level.registryAccess());
        }
        return stack;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BlueprintBoxBlockEntity box ? box.getComparatorOutput() : 0;
    }

    public static BlockBehaviour.Properties boxProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .pushReaction(PushReaction.BLOCK);
    }

    /**
     * 黑曜石及以上额外获得方块级别的绝对防爆特性（物品实体另有爆炸保护）。
     * <p>
     * 普通蓝图箱保持和普通箱子一样的爆炸抗性；黑曜石及以上使用极大值，
     * 避免大型爆炸仍然把放在世界里的蓝图箱炸掉、连带损失箱内物品和蓝图。
     */
    public static BlockBehaviour.Properties boxProperties(BlueprintTier tier) {
        return boxProperties().explosionResistance(tier.explosionProof() ? Float.MAX_VALUE : 2.5F);
    }
}
