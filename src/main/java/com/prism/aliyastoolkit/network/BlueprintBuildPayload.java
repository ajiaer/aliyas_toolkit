package com.prism.aliyastoolkit.network;

import java.util.ArrayList;
import java.util.List;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.CopyPaste;
import com.prism.aliyastoolkit.InventorySync;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxData;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.blueprint.BlueprintData;
import com.prism.aliyastoolkit.blueprint.BlueprintRotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：用主手蓝图箱里的蓝图在固定位置建造。
 * <p>
 * 材料包含方块物品 + 容器内容物，全部按精确 ItemStack 匹配并消耗。
 * 放置方块后恢复方块实体数据，并把容器内容放回原槽位。
 */
public record BlueprintBuildPayload(BlockPos target, int rotation) implements CustomPacketPayload {
    public static final Type<BlueprintBuildPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "blueprint_build"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintBuildPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BlueprintBuildPayload::target,
                    ByteBufCodecs.VAR_INT, BlueprintBuildPayload::rotation,
                    BlueprintBuildPayload::new);

    @Override
    public Type<BlueprintBuildPayload> type() {
        return TYPE;
    }

    public static void handle(BlueprintBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!Config.ENABLE_COPY_PASTE.getAsBoolean() || player.isSpectator() || !player.isAlive()
                    || player.isDeadOrDying()) {
                return;
            }
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BlueprintBoxItem boxItem)) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.need_box"), true);
                return;
            }
            ServerLevel level = player.serverLevel();
            BlueprintData blueprint = BlueprintBoxData.readBlueprint(held, level.registryAccess());
            if (blueprint == null || blueprint.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.no_blueprint"), true);
                return;
            }
            if (blueprint.entries().size() > CopyPaste.MAX_VOLUME) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME),
                        true);
                return;
            }

            int capacity = boxItem.capacity();
            if (!BlueprintBoxData.hasEnoughItems(held, blueprint, capacity)) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.not_enough"), true);
                return;
            }

            int rotation = Math.floorMod(payload.rotation(), CopyPaste.ROTATIONS);
            Vec3 eye = player.getEyePosition();
            double maxDistance = CopyPaste.MAX_DISTANCE;
            List<BlockPos> destinations = new ArrayList<>(blueprint.entries().size());
            for (BlueprintData.Entry entry : blueprint.entries()) {
                BlockPos destination = blueprint.destination(entry.x(), entry.y(), entry.z(),
                        payload.target(), rotation);
                if (!level.isLoaded(destination)) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.copy_paste_not_loaded"), true);
                    return;
                }
                if (eye.distanceToSqr(Vec3.atCenterOf(destination)) > maxDistance * maxDistance) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.copy_paste_too_far"), true);
                    return;
                }
                destinations.add(destination);
            }

            // 建造位置必须全部可替换，避免蓝图直接埋进现有方块。
            if (!level.isLoaded(payload.target()) || !level.getBlockState(payload.target()).canBeReplaced()) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.no_space", 4), true);
                return;
            }
            for (BlockPos destination : destinations) {
                if (!level.getBlockState(destination).canBeReplaced()) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.blueprint.no_space", 4), true);
                    return;
                }
            }

            // 消耗精确材料（方块物品 + 容器内容物）
            NonNullList<ItemStack> items = BlueprintBoxData.readItems(held, capacity);
            for (BlueprintData.Requirement requirement : blueprint.requirements()) {
                int remaining = requirement.count();
                for (int i = 0; i < items.size() && remaining > 0; i++) {
                    ItemStack stack = items.get(i);
                    if (!BlueprintBoxData.matches(requirement.template(), stack)) {
                        continue;
                    }
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
                if (remaining > 0) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.blueprint.not_enough"), true);
                    return;
                }
            }
            BlueprintBoxData.writeItems(held, items);

            for (int i = 0; i < blueprint.entries().size(); i++) {
                BlockState state = BlueprintRotation.rotate(blueprint.entries().get(i).state(), rotation);
                level.setBlock(destinations.get(i), state, Block.UPDATE_ALL);
            }
            correctShapes(level, destinations);
            restoreBlockEntities(level, blueprint, payload.target(), rotation);

            level.playSound(null, payload.target(),
                    BlueprintRotation.rotate(blueprint.entries().get(0).state(), rotation)
                            .getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            player.getInventory().setChanged();
            InventorySync.sync(player);
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.blueprint.built", blueprint.blockCount()), true);
        });
    }


    /**
     * 放完所有方块后重新计算连接形状（楼梯内外角、栅栏/墙/玻璃板连接、箱子合并、铁轨等）。
     * <p>
     * {@code setBlock} 只会让邻居更新，不会重算方块自己；这里按原版 {@code updateShape} 规则补一遍。
     */
    private static void correctShapes(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                continue;
            }
            BlockState corrected = current;
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                corrected = corrected.updateShape(direction, level.getBlockState(neighborPos),
                        level, pos, neighborPos);
                // 需要支撑的方块（火把、花等）没有支撑时会变成空气，保持已经放下的方块
                if (corrected.getBlock() != current.getBlock()) {
                    break;
                }
            }
            if (corrected != current && corrected.getBlock() == current.getBlock()) {
                level.setBlock(pos, corrected, Block.UPDATE_ALL);
            }
        }
    }

    /** 恢复方块实体数据，并把容器内容放回原槽位。 */
    private static void restoreBlockEntities(ServerLevel level, BlueprintData blueprint, BlockPos target,
            int rotation) {
        for (BlueprintData.BlockEntityEntry entry : blueprint.blockEntities()) {
            BlockPos destination = blueprint.destination(entry.x(), entry.y(), entry.z(), target, rotation);
            if (!level.isLoaded(destination)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(destination);
            if (blockEntity == null) {
                continue;
            }
            var actualType = BlockEntityType.getKey(blockEntity.getType());
            if (actualType == null || !actualType.equals(entry.type())) {
                continue;
            }
            var tag = entry.tag().copy();
            tag.putInt("x", destination.getX());
            tag.putInt("y", destination.getY());
            tag.putInt("z", destination.getZ());
            blockEntity.loadWithComponents(tag, level.registryAccess());

            if (blockEntity instanceof Container container) {
                // NBT 中可能带有旧的物品数据；先清空，再按记录放回原槽位，避免重复。
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    container.setItem(slot, ItemStack.EMPTY);
                }
                for (BlueprintData.ContainerContent content : blueprint.contentsAt(entry.x(), entry.y(), entry.z())) {
                    int slot = content.slot();
                    if (slot >= 0 && slot < container.getContainerSize()) {
                        container.setItem(slot, content.stack().copy());
                    }
                }
            } else if (blockEntity.getType() == BlockEntityType.CAMPFIRE) {
                CompoundTag itemTag = new CompoundTag();
                ListTag items = new ListTag();
                for (BlueprintData.ContainerContent content : blueprint.contentsAt(entry.x(), entry.y(), entry.z())) {
                    CompoundTag stackTag = (CompoundTag) content.stack().save(level.registryAccess());
                    stackTag.putByte("Slot", (byte) content.slot());
                    items.add(stackTag);
                }
                itemTag.put("Items", items);
                blockEntity.loadWithComponents(itemTag, level.registryAccess());
            } else if (blockEntity.getType() == BlockEntityType.LECTERN) {
                for (BlueprintData.ContainerContent content : blueprint.contentsAt(entry.x(), entry.y(), entry.z())) {
                    if (content.slot() == 0) {
                        CompoundTag itemTag = new CompoundTag();
                        itemTag.put("Book", content.stack().save(level.registryAccess()));
                        itemTag.putInt("Page", tag.getInt("Page"));
                        blockEntity.loadWithComponents(itemTag, level.registryAccess());
                        break;
                    }
                }
            }
            blockEntity.setChanged();
            level.sendBlockUpdated(destination, blockEntity.getBlockState(), blockEntity.getBlockState(),
                    Block.UPDATE_ALL);
        }
    }
}

