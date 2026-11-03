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


public record CycleBuildPresetPayload(int direction) implements CustomPacketPayload {
    public static final Type<CycleBuildPresetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "cycle_build_preset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleBuildPresetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CycleBuildPresetPayload::direction,
            CycleBuildPresetPayload::new);

    @Override
    public Type<CycleBuildPresetPayload> type() {
        return TYPE;
    }

    /**
     * 计算切换后的快速建造预设索引（不循环：已经在两端时保持不变）。
     */
    public static int nextIndex(int current, int size, int direction) {
        if (size <= 1) {
            return 0;
        }
        int next = current + (direction >= 0 ? 1 : -1);
        return Math.max(0, Math.min(next, size - 1));
    }

    public static void handle(CycleBuildPresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ToolkitSettings settings = player.getData(AliyasToolkit.SETTINGS);
            int size = settings.buildPresets().size();
            if (size > 1) {
                int next = nextIndex(settings.selectedBuildPreset(), size, payload.direction());
                if (next != settings.selectedBuildPreset()) {
                    settings = settings.withSelectedBuildPreset(next);
                    player.setData(AliyasToolkit.SETTINGS, settings);
                }
            }
            PacketDistributor.sendToPlayer(player, new SyncToolkitSettingsPayload(settings));
        });
    }
}
