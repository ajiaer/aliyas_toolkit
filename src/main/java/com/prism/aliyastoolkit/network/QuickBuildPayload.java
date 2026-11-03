package com.prism.aliyastoolkit.network;

import java.util.ArrayList;
import java.util.List;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.BuildPreset;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.InventorySync;
import com.prism.aliyastoolkit.QuickBuild;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：快速建造。客户端把两个（已吸附的）点和当前预设发过来，
 * 服务端重新计算范围、校验距离/方块数量后真正放置方块。
 */
public record QuickBuildPayload(BlockPos first, BlockPos second, BuildPreset preset) implements CustomPacketPayload {
    public static final Type<QuickBuildPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "quick_build"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickBuildPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, QuickBuildPayload::first,
            BlockPos.STREAM_CODEC, QuickBuildPayload::second,
            BuildPreset.STREAM_CODEC, QuickBuildPayload::preset,
            QuickBuildPayload::new);

    @Override
    public Type<QuickBuildPayload> type() {
        return TYPE;
    }

    public static void handle(QuickBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!Config.ENABLE_QUICK_BUILD.getAsBoolean() || player.isSpectator() || !player.isAlive()) {
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockPos first = payload.first();
            BlockPos second = payload.second();
            Vec3 eye = player.getEyePosition();
            double maxDistance = QuickBuild.MAX_DISTANCE;
            if (eye.distanceToSqr(Vec3.atCenterOf(first)) > maxDistance * maxDistance
                    || eye.distanceToSqr(Vec3.atCenterOf(second)) > maxDistance * maxDistance) {
                player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_too_far"), true);
                return;
            }

            List<BlockPos> positions = QuickBuild.positions(first, second, payload.preset());
            if (positions.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.build_too_big", QuickBuild.MAX_VOLUME), true);
                return;
            }

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BlockItem blockItem)) {
                player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_not_block"), true);
                return;
            }
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (!state.isCollisionShapeFullBlock(level, first)) {
                player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_not_block"), true);
                return;
            }

            // 只保留可以替换（空气/水/草等）的位置
            double maxPlaceDistance = maxDistance + 4.0D;
            List<BlockPos> placeable = new ArrayList<>(positions.size());
            for (BlockPos pos : positions) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                if (eye.distanceToSqr(Vec3.atCenterOf(pos)) > maxPlaceDistance * maxPlaceDistance) {
                    continue;
                }
                if (!level.getBlockState(pos).canBeReplaced()) {
                    continue;
                }
                placeable.add(pos);
            }
            if (placeable.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_no_space"), true);
                return;
            }

            Item item = held.getItem();
            int available = countItem(player.getInventory(), item);
            if (available < placeable.size()) {
                player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_not_enough",
                        placeable.size(), available), true);
                return;
            }

            consumeItem(player.getInventory(), item, placeable.size());
            for (BlockPos pos : placeable) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
            level.playSound(null, first, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
            InventorySync.sync(player);
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.build_done", placeable.size()), true);
        });
    }

    /** 统计物品栏（含快捷栏）里某种物品的数量。 */
    private static int countItem(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 优先消耗背包（槽位 9-35），不够再消耗快捷栏（0-8）。 */
    private static void consumeItem(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            int start = pass == 0 ? 9 : 0;
            int end = pass == 0 ? 36 : 9;
            for (int i = start; i < end && remaining > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        inventory.setChanged();
    }
}

