package com.prism.aliyastoolkit.network;

import java.util.ArrayList;
import java.util.List;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.CopyPaste;
import com.prism.aliyastoolkit.InventorySync;
import com.prism.aliyastoolkit.ToolkitSettings;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlock;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxData;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.blueprint.BlueprintCapture;
import com.prism.aliyastoolkit.blueprint.BlueprintData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：把当前选区记录进主手的蓝图箱。
 * <p>
 * 服务端重新读取方块状态、方块实体和容器内容。
 * {@code cut = true} 时把材料填入箱子并移除源方块（剪切）；
 * {@code cut = false} 时只保存蓝图，不改变源方块，也不向箱子放入材料（复制）。
 */
public record BlueprintCreatePayload(boolean cut, BlockPos min, List<BlockPos> offsets)
        implements CustomPacketPayload {
    public static final Type<BlueprintCreatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "blueprint_create"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCreatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.cut());
                BlockPos.STREAM_CODEC.encode(buffer, payload.min());
                buffer.writeVarInt(payload.offsets().size());
                for (BlockPos offset : payload.offsets()) {
                    buffer.writeVarInt(offset.getX());
                    buffer.writeVarInt(offset.getY());
                    buffer.writeVarInt(offset.getZ());
                }
            },
            buffer -> {
                boolean cut = buffer.readBoolean();
                BlockPos min = BlockPos.STREAM_CODEC.decode(buffer);
                int count = buffer.readVarInt();
                if (count < 0 || count > CopyPaste.MAX_VOLUME) {
                    throw new IllegalArgumentException("Invalid blueprint block count: " + count);
                }
                List<BlockPos> offsets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    offsets.add(new BlockPos(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
                }
                return new BlueprintCreatePayload(cut, min, offsets);
            });

    public BlueprintCreatePayload {
        offsets = List.copyOf(offsets);
    }

    @Override
    public Type<BlueprintCreatePayload> type() {
        return TYPE;
    }

    /** 用绝对方块坐标生成网络包（自动取最小角并转成相对坐标）。 */
    public static BlueprintCreatePayload of(boolean cut, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return new BlueprintCreatePayload(cut, BlockPos.ZERO, List.of());
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        List<BlockPos> offsets = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            offsets.add(pos.subtract(min));
        }
        return new BlueprintCreatePayload(cut, min, offsets);
    }

    public static void handle(BlueprintCreatePayload payload, IPayloadContext context) {
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
            if (!BlueprintBoxData.isEmpty(held)) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.not_empty"), true);
                return;
            }
            if (payload.offsets().isEmpty() || payload.offsets().size() > CopyPaste.MAX_VOLUME) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.copy_paste_no_blocks"), true);
                return;
            }
            for (BlockPos offset : payload.offsets()) {
                if (!player.canInteractWithBlock(payload.min().offset(offset), CopyPaste.MAX_DISTANCE)) {
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.copy_paste_too_far"), true);
                    return;
                }
            }

            ServerLevel level = player.serverLevel();
            ToolkitSettings settings = player.getData(AliyasToolkit.SETTINGS);
            boolean collectFluids = settings.collectFluids();
            BlueprintCapture.Result result = BlueprintCapture.capture(level, payload.min(), payload.offsets(), player,
                    collectFluids);
            if (result.errorKey() != null || result.blueprint() == null) {
                player.displayClientMessage(Component.translatable(result.errorKey() == null
                        ? "message.aliyastoolkit.copy_paste_no_blocks" : result.errorKey()), true);
                return;
            }
            BlueprintData blueprint = result.blueprint();
            int fluidCount = blueprint.fluidSourceCount();
            int skippedFluidSources = 0;
            if (payload.cut() && fluidCount > 0) {
                int emptyBuckets = countEmptyBuckets(player);
                if (emptyBuckets < fluidCount) {
                    skippedFluidSources = fluidCount;
                    // 空桶不足时不取消记录，只是跳过流体源头。
                    result = BlueprintCapture.capture(level, payload.min(), payload.offsets(), player, false);
                    if (result.errorKey() != null || result.blueprint() == null) {
                        player.displayClientMessage(Component.translatable(
                                "message.aliyastoolkit.blueprint.not_enough_buckets", fluidCount, emptyBuckets), true);
                        return;
                    }
                    blueprint = result.blueprint();
                    fluidCount = 0;
                }
            }
            int capacity = boxItem.capacity();
            List<BlueprintData.SlotDef> slotDefs = blueprint.createSlots(capacity);
            if (slotDefs.size() > capacity) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.too_many_advanced"), true);
                return;
            }

            int[] limits = new int[capacity];
            List<ItemStack> items = new ArrayList<>(capacity);
            for (int i = 0; i < capacity; i++) {
                items.add(ItemStack.EMPTY);
                if (i < slotDefs.size()) {
                    BlueprintData.SlotDef def = slotDefs.get(i);
                    limits[i] = def.limit();
                    if (payload.cut()) {
                        items.set(i, def.template().copyWithCount(def.limit()));
                    }
                }
            }
            if (payload.cut() && fluidCount > 0) {
                consumeEmptyBuckets(player, fluidCount);
            }
            BlueprintBoxData.writeAll(held, items, limits, blueprint, level.registryAccess());

            if (payload.cut()) {
                // 蓝图已经保存了源方块（包括容器内容）的完整数据，移除时不再额外掉一份。
                BlueprintData finalBlueprint = blueprint;
                BlueprintBoxBlock.withDropsSuppressed(() -> {
                    for (BlueprintData.Entry entry : finalBlueprint.entries()) {
                        BlockPos pos = payload.min().offset(entry.x(), entry.y(), entry.z());
                        // 先清空并移除方块实体，避免箱子等容器在 setBlock 时把内容物再掉一份。
                        var blockEntity = level.getBlockEntity(pos);
                        if (blockEntity instanceof Container container) {
                            container.clearContent();
                        }
                        if (blockEntity != null) {
                            level.removeBlockEntity(pos);
                        }
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                });
            }

            player.getInventory().setChanged();
            InventorySync.sync(player);
            int skippedBoxes = result.skippedBlueprintBoxes();
            if (skippedBoxes > 0 && skippedFluidSources > 0) {
                player.displayClientMessage(Component.translatable(
                        "message.aliyastoolkit.blueprint.created_skipped_both",
                        blueprint.blockCount(), skippedBoxes, skippedFluidSources), true);
            } else if (skippedBoxes > 0) {
                player.displayClientMessage(Component.translatable(
                        "message.aliyastoolkit.blueprint.created_skipped_boxes",
                        blueprint.blockCount(), skippedBoxes), true);
            } else if (skippedFluidSources > 0) {
                player.displayClientMessage(Component.translatable(
                        "message.aliyastoolkit.blueprint.created_skipped_fluids",
                        blueprint.blockCount(), skippedFluidSources), true);
            } else {
                player.displayClientMessage(payload.cut()
                        ? Component.translatable("message.aliyastoolkit.blueprint.cut_created", blueprint.blockCount())
                        : Component.translatable("message.aliyastoolkit.blueprint.created", blueprint.blockCount()),
                        true);
            }
        });
    }

    /** 主物品栏 + 快捷栏里空桶的数量。 */
    private static int countEmptyBuckets(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.BUCKET)) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.BUCKET)) {
            count += offhand.getCount();
        }
        return count;
    }

    /** 从主物品栏 + 快捷栏 + 副手消耗指定数量的空桶。 */
    private static void consumeEmptyBuckets(ServerPlayer player, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.BUCKET)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            player.getInventory().removeItem(slot, take);
            remaining -= take;
        }
        if (remaining > 0) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.is(Items.BUCKET)) {
                offhand.shrink(Math.min(remaining, offhand.getCount()));
            }
        }
    }
}

