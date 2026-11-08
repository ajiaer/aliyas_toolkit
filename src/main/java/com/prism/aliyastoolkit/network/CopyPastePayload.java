package com.prism.aliyastoolkit.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.ChainMining;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.CopyPaste;
import com.prism.aliyastoolkit.InventorySync;
import com.prism.aliyastoolkit.ItemConsumption;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：剪切复制。
 * <p>
 * 客户端把“源方块位置”和“目标位置”（一一对应，都是相对锚点的偏移）发过来，
 * 服务端从自己的世界读取源方块状态、重新校验距离/数量后真正移动或放置方块。
 * <p>
 * 剪切（{@code cut = true}）：不消耗物品，先清空源位置再在目标位置放下方块；
 * 目标位置可以与源位置重叠。
 * 复制（{@code cut = false}）：目标位置不能与源位置重叠，
 * 并按方块种类消耗背包 + 快捷栏里的物品（优先背包）。
 */
public record CopyPastePayload(boolean cut, BlockPos sourceAnchor, BlockPos destinationAnchor,
        List<BlockMove> blocks) implements CustomPacketPayload {
    /** 一个方块的源位置 / 目标位置，都是相对各自锚点的偏移（非负）。 */
    public record BlockMove(int sourceDx, int sourceDy, int sourceDz,
            int destinationDx, int destinationDy, int destinationDz) {
    }

    public static final Type<CopyPastePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "copy_paste"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CopyPastePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.cut());
                BlockPos.STREAM_CODEC.encode(buffer, payload.sourceAnchor());
                BlockPos.STREAM_CODEC.encode(buffer, payload.destinationAnchor());
                buffer.writeVarInt(payload.blocks().size());
                for (BlockMove move : payload.blocks()) {
                    buffer.writeVarInt(move.sourceDx());
                    buffer.writeVarInt(move.sourceDy());
                    buffer.writeVarInt(move.sourceDz());
                    buffer.writeVarInt(move.destinationDx());
                    buffer.writeVarInt(move.destinationDy());
                    buffer.writeVarInt(move.destinationDz());
                }
            },
            buffer -> {
                boolean cut = buffer.readBoolean();
                BlockPos sourceAnchor = BlockPos.STREAM_CODEC.decode(buffer);
                BlockPos destinationAnchor = BlockPos.STREAM_CODEC.decode(buffer);
                int count = buffer.readVarInt();
                if (count < 0 || count > CopyPaste.MAX_VOLUME) {
                    throw new IllegalArgumentException("Invalid copy-paste block count: " + count);
                }
                List<BlockMove> blocks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    blocks.add(new BlockMove(
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
                }
                return new CopyPastePayload(cut, sourceAnchor, destinationAnchor, blocks);
            });

    public CopyPastePayload {
        blocks = List.copyOf(blocks);
    }

    @Override
    public Type<CopyPastePayload> type() {
        return TYPE;
    }

    /**
     * 用两组一一对应的世界坐标生成网络包（锚点取两组坐标各自的最小角）。
     */
    public static CopyPastePayload of(boolean cut, List<BlockPos> sources, List<BlockPos> destinations) {
        if (sources.isEmpty() || sources.size() != destinations.size()) {
            return new CopyPastePayload(cut, BlockPos.ZERO, BlockPos.ZERO, List.of());
        }
        BlockPos sourceAnchor = minOf(sources);
        BlockPos destinationAnchor = minOf(destinations);
        List<BlockMove> moves = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            BlockPos source = sources.get(i);
            BlockPos destination = destinations.get(i);
            moves.add(new BlockMove(
                    source.getX() - sourceAnchor.getX(),
                    source.getY() - sourceAnchor.getY(),
                    source.getZ() - sourceAnchor.getZ(),
                    destination.getX() - destinationAnchor.getX(),
                    destination.getY() - destinationAnchor.getY(),
                    destination.getZ() - destinationAnchor.getZ()));
        }
        return new CopyPastePayload(cut, sourceAnchor, destinationAnchor, moves);
    }

    private static BlockPos minOf(List<BlockPos> positions) {
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int z = Integer.MAX_VALUE;
        for (BlockPos pos : positions) {
            x = Math.min(x, pos.getX());
            y = Math.min(y, pos.getY());
            z = Math.min(z, pos.getZ());
        }
        return new BlockPos(x, y, z);
    }

    public static void handle(CopyPastePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!Config.ENABLE_COPY_PASTE.getAsBoolean() || player.isSpectator() || !player.isAlive()
                    || player.isDeadOrDying()) {
                return;
            }
            List<BlockMove> moves = payload.blocks();
            if (moves.isEmpty() || moves.size() > CopyPaste.MAX_VOLUME) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME), true);
                return;
            }

            ServerLevel level = player.serverLevel();
            Vec3 eye = player.getEyePosition();
            double maxDistance = CopyPaste.MAX_DISTANCE;

            // 先读出所有源方块（跳过空气和流体），同时校验距离、目标位置是否合法
            List<BlockPos> sources = new ArrayList<>(moves.size());
            List<BlockPos> destinations = new ArrayList<>(moves.size());
            List<BlockState> states = new ArrayList<>(moves.size());
            // 剪切时顺便保存方块实体数据（箱子里的物品、告示牌文字等），移到目标位置后恢复
            List<CompoundTag> blockEntityTags = new ArrayList<>(moves.size());
            Set<BlockPos> sourceSet = new HashSet<>();
            Set<BlockPos> destinationSet = new HashSet<>();

            for (BlockMove move : moves) {
                BlockPos source = payload.sourceAnchor().offset(move.sourceDx(), move.sourceDy(), move.sourceDz());
                BlockPos destination = payload.destinationAnchor().offset(move.destinationDx(),
                        move.destinationDy(), move.destinationDz());
                if (!level.isLoaded(source) || !level.isLoaded(destination)) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.copy_paste_not_loaded"), true);
                    return;
                }
                if (eye.distanceToSqr(Vec3.atCenterOf(source)) > maxDistance * maxDistance
                        || eye.distanceToSqr(Vec3.atCenterOf(destination)) > maxDistance * maxDistance) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.copy_paste_too_far"), true);
                    return;
                }

                BlockState state = level.getBlockState(source);
                if (state.isAir() || ChainMining.isFluidBlock(state)) {
                    continue;
                }
                if (!destinationSet.add(destination)) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.copy_paste_occupied"), true);
                    return;
                }
                sources.add(source);
                destinations.add(destination);
                states.add(state);
                sourceSet.add(source);
                if (payload.cut()) {
                    BlockEntity blockEntity = level.getBlockEntity(source);
                    blockEntityTags.add(blockEntity == null ? null
                            : blockEntity.saveWithFullMetadata(level.registryAccess()));
                } else {
                    blockEntityTags.add(null);
                }
            }

            if (sources.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.copy_paste_no_blocks"), true);
                return;
            }
            if (!payload.cut()) {
                // 复制：目标不能与原方块重叠（可以覆盖其它位置的方块）
                for (BlockPos destination : destinations) {
                    if (sourceSet.contains(destination)) {
                        player.displayClientMessage(
                                Component.translatable("message.aliyastoolkit.copy_paste_copy_overlap"), true);
                        return;
                    }
                }

                // 按方块种类统计需要的物品；创造模式不检查也不消耗
                if (!player.isCreative()) {
                    Map<Item, Integer> needed = new HashMap<>();
                    for (BlockState state : states) {
                        Item item = state.getBlock().asItem();
                        if (item != Items.AIR) {
                            needed.merge(item, 1, Integer::sum);
                        }
                    }
                    for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
                        int available = ItemConsumption.count(player.getInventory(), entry.getKey());
                        if (available < entry.getValue()) {
                            player.displayClientMessage(Component.translatable(
                                    "message.aliyastoolkit.copy_paste_not_enough",
                                    new ItemStack(entry.getKey()).getHoverName(),
                                    entry.getValue(), available), true);
                            return;
                        }
                    }
                    needed.forEach((item, amount) -> ItemConsumption.consume(player.getInventory(), item, amount));
                }
            }

            // 剪切：先清空源位置，再统一放到目标位置（目标可能与源位置重叠）
            if (payload.cut()) {
                BlueprintBoxBlock.withDropsSuppressed(() -> {
                    for (BlockPos source : sources) {
                        if (!level.getBlockState(source).isAir()) {
                            level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                });
            }
            for (int i = 0; i < destinations.size(); i++) {
                level.setBlock(destinations.get(i), states.get(i), Block.UPDATE_ALL);
            }

            // 剪切：把方块实体数据（容器内容、告示牌文字等）一并搬到目标位置
            if (payload.cut()) {
                for (int i = 0; i < destinations.size(); i++) {
                    CompoundTag tag = blockEntityTags.get(i);
                    if (tag == null) {
                        continue;
                    }
                    BlockPos destination = destinations.get(i);
                    tag.putInt("x", destination.getX());
                    tag.putInt("y", destination.getY());
                    tag.putInt("z", destination.getZ());
                    BlockEntity blockEntity = level.getBlockEntity(destination);
                    if (blockEntity != null) {
                        blockEntity.loadWithComponents(tag, level.registryAccess());
                        blockEntity.setChanged();
                    }
                }
            }

            level.playSound(null, payload.destinationAnchor(), states.get(0).getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            InventorySync.sync(player);
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.copy_paste_done", states.size()), true);
        });
    }
}
