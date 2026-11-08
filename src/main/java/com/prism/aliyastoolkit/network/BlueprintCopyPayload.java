package com.prism.aliyastoolkit.network;

import java.util.List;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.CopyPaste;
import com.prism.aliyastoolkit.InventorySync;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlock;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlockEntity;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxData;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：用主手的空蓝图箱复制另一个已放置蓝图箱的蓝图（不复制物品）。
 * 大蓝图箱可以复制小蓝图箱，小蓝图箱不能复制大蓝图箱。
 */
public record BlueprintCopyPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<BlueprintCopyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "blueprint_copy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCopyPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, BlueprintCopyPayload::pos, BlueprintCopyPayload::new);

    @Override
    public Type<BlueprintCopyPayload> type() {
        return TYPE;
    }

    public static void handle(BlueprintCopyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!Config.ENABLE_COPY_PASTE.getAsBoolean() || player.isSpectator() || !player.isAlive()) {
                return;
            }
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BlueprintBoxItem boxItem)) {
                return;
            }
            if (!BlueprintBoxData.isEmpty(held)) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.not_empty"), true);
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(payload.pos()) || !player.canInteractWithBlock(payload.pos(), 8.0D)) {
                return;
            }
            if (!(level.getBlockEntity(payload.pos()) instanceof BlueprintBoxBlockEntity target)) {
                return;
            }
            if (!(level.getBlockState(payload.pos()).getBlock() instanceof BlueprintBoxBlock)) {
                return;
            }
            if (!target.hasBlueprint()) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.target_empty"), true);
                return;
            }
            BlueprintData blueprint = target.getBlueprint();
            if (blueprint == null || blueprint.entries().size() > CopyPaste.MAX_VOLUME) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.too_many"), true);
                return;
            }
            int capacity = boxItem.capacity();
            List<BlueprintData.SlotDef> defs = blueprint.createSlots(capacity);
            if (defs.size() > capacity) {
                player.displayClientMessage(
                        Component.translatable("message.aliyastoolkit.blueprint.too_many"), true);
                return;
            }
            int[] limits = new int[capacity];
            for (int i = 0; i < Math.min(defs.size(), capacity); i++) {
                limits[i] = defs.get(i).limit();
            }
            BlueprintBoxData.writeBlueprint(held, blueprint, limits, level.registryAccess());
            player.getInventory().setChanged();
            InventorySync.sync(player);
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.blueprint.copied"), true);
        });
    }
}
