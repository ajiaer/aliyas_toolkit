package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.ChainMining;
import com.prism.aliyastoolkit.Config;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：连锁快捷键当前是“激活”还是“未激活”。
 * <p>
 * “按住”和“切换”两种触发方式都由客户端换算成同一个布尔值：按下/切换开启时发 true，
 * 松开/切换关闭时发 false。服务端据此决定玩家挖方块时要不要连锁，所以没按快捷键时完全不会触发。
 */
public record SetChainStatePayload(boolean active) implements CustomPacketPayload {
    public static final Type<SetChainStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "set_chain_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetChainStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SetChainStatePayload::active,
            SetChainStatePayload::new);

    @Override
    public Type<SetChainStatePayload> type() {
        return TYPE;
    }

    public static void handle(SetChainStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean active = payload.active()
                    && Config.ENABLE_CHAIN_MINING.getAsBoolean()
                    && player.isAlive()
                    && !player.isSpectator();
            ChainMining.setActive(player, active);
        });
    }
}
