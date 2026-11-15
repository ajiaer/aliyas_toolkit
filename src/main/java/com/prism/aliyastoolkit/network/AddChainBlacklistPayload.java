package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.ToolkitSettings;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：把“添加”模式里左键点到的方块加入连锁破坏黑名单。
 * <p>
 * 服务端校验数量上限后保存，并把完整设置同步回客户端（{@link SyncToolkitSettingsPayload}）。
 * 黑名单里的方块使用“预设连锁破坏方案”破坏。
 */
public record AddChainBlacklistPayload(ResourceLocation blockId) implements CustomPacketPayload {
    public static final Type<AddChainBlacklistPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "add_chain_blacklist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddChainBlacklistPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, AddChainBlacklistPayload::blockId,
                    AddChainBlacklistPayload::new);

    @Override
    public Type<AddChainBlacklistPayload> type() {
        return TYPE;
    }

    public static void handle(AddChainBlacklistPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ToolkitSettings settings = player.getData(AliyasToolkit.SETTINGS);
            ToolkitSettings updated = settings.withAddedChainBlacklist(payload.blockId());
            if (updated != settings) {
                player.setData(AliyasToolkit.SETTINGS, updated);
            }
            PacketDistributor.sendToPlayer(player, new SyncToolkitSettingsPayload(updated));
        });
    }
}
