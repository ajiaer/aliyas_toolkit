package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AutoRefill;
import com.prism.aliyastoolkit.AliyasToolkit;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：某个快捷栏格子空了，请求从背包补充同样的物品。
 * <p>
 * 会把“消失前的那个物品”一起带上，服务端用它来判断白名单匹配和背包里的候选物品。
 * 客户端只在“没有打开任何 GUI 且这一格刚从非空变成空”时发送，服务端会再校验一次。
 */
public record RefillHotbarPayload(int hotbarSlot, ItemStack vanished) implements CustomPacketPayload {
    public static final Type<RefillHotbarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "refill_hotbar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefillHotbarPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RefillHotbarPayload::hotbarSlot,
            ItemStack.OPTIONAL_STREAM_CODEC, RefillHotbarPayload::vanished,
            RefillHotbarPayload::new);

    @Override
    public Type<RefillHotbarPayload> type() {
        return TYPE;
    }

    public static void handle(RefillHotbarPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AutoRefill.refill(player, payload.hotbarSlot(), payload.vanished());
            }
        });
    }
}
