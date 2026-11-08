package com.prism.aliyastoolkit.network;

import java.util.ArrayList;
import java.util.List;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.CopyPaste;
import com.prism.aliyastoolkit.InventorySync;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：把当前选区记录进主手的蓝图箱。
 * <p>
 * 服务端重新读取方块状态、方块实体和容器内容；容器内容会作为额外材料需求，
 * 剪切时同时填入箱子并移除源方块。
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
            BlueprintCapture.Result result = BlueprintCapture.capture(level, payload.min(), payload.offsets(), player);
            if (result.errorKey() != null || result.blueprint() == null) {
                player.displayClientMessage(Component.translatable(result.errorKey() == null
                        ? "message.aliyastoolkit.copy_paste_no_blocks" : result.errorKey()), true);
                return;
            }
            BlueprintData blueprint = result.blueprint();
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
            BlueprintBoxData.writeAll(held, items, limits, blueprint, level.registryAccess());

            if (payload.cut()) {
                // 蓝图已经保存了源方块（包括蓝图箱自身）的完整数据，移除时不再额外掉一份。
                BlueprintBoxBlock.withDropsSuppressed(() -> {
                    for (BlueprintData.Entry entry : blueprint.entries()) {
                        BlockPos pos = payload.min().offset(entry.x(), entry.y(), entry.z());
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                });
            }

            player.getInventory().setChanged();
            InventorySync.sync(player);
            player.displayClientMessage(payload.cut()
                    ? Component.translatable("message.aliyastoolkit.blueprint.cut_created", blueprint.blockCount())
                    : Component.translatable("message.aliyastoolkit.blueprint.created", blueprint.blockCount()), true);
        });
    }
}

