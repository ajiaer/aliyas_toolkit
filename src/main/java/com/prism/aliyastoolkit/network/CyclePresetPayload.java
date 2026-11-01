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
 * 客户端 -> 服务端：切换“大量方块”的预设（快捷键 + 滚轮时使用）。
 * <p>
 * {@code direction} 大于 0 表示下一个预设，小于 0 表示上一个；到末尾会循环。
 * 服务端修改后会把完整设置同步回客户端（{@link SyncToolkitSettingsPayload}）。
 */
public record CyclePresetPayload(int direction) implements CustomPacketPayload {
    public static final Type<CyclePresetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "cycle_preset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CyclePresetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CyclePresetPayload::direction,
            CyclePresetPayload::new);

    @Override
    public Type<CyclePresetPayload> type() {
        return TYPE;
    }

    /**
     * 计算切换后的预设索引（循环）。
     *
     * @param current   当前索引
     * @param size      预设数量
     * @param direction 大于 0 下一个，小于 0 上一个
     */
    public static int nextIndex(int current, int size, int direction) {
        if (size <= 1) {
            return 0;
        }
        return Math.floorMod(current + (direction >= 0 ? 1 : -1), size);
    }

    public static void handle(CyclePresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ToolkitSettings settings = player.getData(AliyasToolkit.SETTINGS);
            int size = settings.presets().size();
            if (size > 1) {
                int next = nextIndex(settings.selectedPreset(), size, payload.direction());
                settings = settings.withSelectedPreset(next);
                player.setData(AliyasToolkit.SETTINGS, settings);
            }
            PacketDistributor.sendToPlayer(player, new SyncToolkitSettingsPayload(settings));
        });
    }
}
