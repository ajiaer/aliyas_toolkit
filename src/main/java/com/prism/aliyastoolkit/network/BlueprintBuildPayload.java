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
import com.prism.aliyastoolkit.blueprint.FluidTank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.ItemContainerContents;
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
                        Component.translatable("message.aliyastoolkit.blueprint.no_space",
                                CopyPaste.BLUEPRINT_PLACEMENT_SEARCH_RADIUS), true);
                return;
            }
            for (BlockPos destination : destinations) {
                if (!level.getBlockState(destination).canBeReplaced()) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.blueprint.no_space",
                                    CopyPaste.BLUEPRINT_PLACEMENT_SEARCH_RADIUS), true);
                    return;
                }
            }

            // 消耗精确材料（方块物品 + 储罐流体）；容器内容物不再作为建造材料，也不恢复。
            List<BlueprintData.FluidRequirement> fluidRequirements = blueprint.fluidRequirements();
            FluidTank tank = BlueprintBoxData.readTank(held, boxItem.tier().tankCapacity());
            if (!tank.containsAll(fluidRequirements)) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.not_enough"), true);
                return;
            }
            NonNullList<ItemStack> items = BlueprintBoxData.readItems(held, capacity);
            // 第二栏的容器内容物：允许缺货，有多少回填多少。
            NonNullList<ItemStack> contentItems = BlueprintBoxData.readContentItems(held,
                    boxItem.tier().contentSlots(), level.registryAccess());
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
            tank.consume(fluidRequirements);
            BlueprintBoxData.writeTank(held, tank);

            for (int i = 0; i < blueprint.entries().size(); i++) {
                BlockState state = BlueprintRotation.rotate(blueprint.entries().get(i).state(), rotation);
                level.setBlock(destinations.get(i), state, Block.UPDATE_ALL);
            }
            correctShapes(level, destinations);
            restoreBlockEntities(level, blueprint, payload.target(), rotation);
            restoreContainerContents(level, blueprint, payload.target(), rotation, contentItems);
            restoreContainerItems(level, blueprint, payload.target(), rotation);
            BlueprintBoxData.writeContentItems(held, contentItems, level.registryAccess());

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
     * 回填容器内容物：按蓝图记录的原容器 / 原槽位放回，缺货就少放一些，不影响建造。
     */
    private static void restoreContainerContents(ServerLevel level, BlueprintData blueprint, BlockPos target,
            int rotation, NonNullList<ItemStack> available) {
        for (BlueprintData.ContainerContent content : blueprint.contents()) {
            BlockPos destination = blueprint.destination(content.x(), content.y(), content.z(), target, rotation);
            if (!level.isLoaded(destination)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(destination);
            if (blockEntity == null || content.stack().isEmpty()) {
                continue;
            }
            int taken = takeFromAvailable(available, content.stack(), content.stack().getCount());
            if (taken <= 0) {
                continue;
            }
            ItemStack toPlace = content.stack().copyWithCount(taken);
            if (blockEntity instanceof Container container) {
                int slot = content.slot();
                if (slot >= 0 && slot < container.getContainerSize()) {
                    ItemStack existing = container.getItem(slot);
                    if (existing.isEmpty()) {
                        container.setItem(slot, toPlace);
                    } else if (ItemStack.isSameItemSameComponents(existing, toPlace)) {
                        ItemStack merged = existing.copy();
                        merged.grow(taken);
                        container.setItem(slot, merged);
                    }
                }
            } else if (blockEntity.getType() == BlockEntityType.CAMPFIRE) {
                CompoundTag stackTag = (CompoundTag) toPlace.save(level.registryAccess());
                stackTag.putByte("Slot", (byte) content.slot());
                ListTag items = new ListTag();
                items.add(stackTag);
                CompoundTag itemTag = new CompoundTag();
                itemTag.put("Items", items);
                blockEntity.loadWithComponents(itemTag, level.registryAccess());
            } else if (blockEntity.getType() == BlockEntityType.LECTERN) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.put("Book", toPlace.save(level.registryAccess()));
                blockEntity.loadWithComponents(itemTag, level.registryAccess());
            }
            blockEntity.setChanged();
            level.sendBlockUpdated(destination, blockEntity.getBlockState(), blockEntity.getBlockState(),
                    Block.UPDATE_ALL);
        }
    }

    /** 从第二栏里取走指定数量的物品，返回实际取到的数量。 */
    private static int takeFromAvailable(NonNullList<ItemStack> available, ItemStack wanted, int max) {
        int taken = 0;
        for (int i = 0; i < available.size() && taken < max; i++) {
            ItemStack stack = available.get(i);
            if (stack.isEmpty() || !BlueprintBoxData.matches(wanted, stack)) {
                continue;
            }
            int take = Math.min(max - taken, stack.getCount());
            stack.shrink(take);
            taken += take;
        }
        return taken;
    }

    /**
     * 整体记录的容器（潜影盒等）：内容由容器物品本身携带，
     * 这里把记录时那个容器物品的容器组件应用到放好的方块实体上。
     */
    private static void restoreContainerItems(ServerLevel level, BlueprintData blueprint, BlockPos target,
            int rotation) {
        for (BlueprintData.ContainerItem item : blueprint.containerItems()) {
            BlockPos destination = blueprint.destination(item.x(), item.y(), item.z(), target, rotation);
            if (!level.isLoaded(destination)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(destination);
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            ItemContainerContents contents = item.stack().get(DataComponents.CONTAINER);
            if (contents == null) {
                continue;
            }
            NonNullList<ItemStack> stacks = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
            contents.copyInto(stacks);
            for (int slot = 0; slot < stacks.size(); slot++) {
                container.setItem(slot, stacks.get(slot));
            }
            blockEntity.setChanged();
            level.sendBlockUpdated(destination, blockEntity.getBlockState(), blockEntity.getBlockState(),
                    Block.UPDATE_ALL);
        }
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
            // 容器先清空：普通容器的内容物由 restoreContainerContents 回填，
            // 潜影盒这类容器由 restoreContainerItems 按记录时的容器物品回填。
            if (blockEntity instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    container.setItem(slot, ItemStack.EMPTY);
                }
            }
            blockEntity.setChanged();
            level.sendBlockUpdated(destination, blockEntity.getBlockState(), blockEntity.getBlockState(),
                    Block.UPDATE_ALL);
        }
    }
}

