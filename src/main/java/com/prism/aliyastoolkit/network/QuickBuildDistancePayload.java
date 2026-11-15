package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.ToolkitSettings;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：修改快速建造“指示器没有指向方块时的默认位置距离”（2-8 格）。
 * <p>
 * 服务端会 clamp 后保存，并把完整设置同步回客户端（{@link SyncToolkitSettingsPayload}）。
 */
public record QuickBuildDistancePayload(int distance) implements CustomPacketPayload {
    public static final Type<QuickBuildDistancePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "quick_build_distance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickBuildDistancePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, QuickBuildDistancePayload::distance,
                    QuickBuildDistancePayload::new);

    @Override
    public Type<QuickBuildDistancePayload> type() {
        return TYPE;
    }

    public static void handle(QuickBuildDistancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ToolkitSettings settings = player.getData(AliyasToolkit.SETTINGS);
            ToolkitSettings updated = settings.withQuickBuildDistance(payload.distance());
            if (updated != settings) {
                player.setData(AliyasToolkit.SETTINGS, updated);
            }
            PacketDistributor.sendToPlayer(player, new SyncToolkitSettingsPayload(updated));
        });
    }
}
