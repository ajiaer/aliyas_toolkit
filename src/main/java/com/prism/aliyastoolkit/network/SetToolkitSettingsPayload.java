package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.ToolkitSettings;
import com.prism.aliyastoolkit.menu.ToolkitMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：修改“Aliya's Toolkit 配置”界面里的设置。
 * <p>
 * 一次把三项设置整体发过去（而不是一项一个包），服务端会再校验一遍范围，
 * 保存到这名玩家自己的玩家数据里，并回发一份 {@link SyncToolkitSettingsPayload} 作为确认。
 */
public record SetToolkitSettingsPayload(ToolkitSettings settings) implements CustomPacketPayload {
    public static final Type<SetToolkitSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "set_toolkit_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetToolkitSettingsPayload> STREAM_CODEC = ToolkitSettings.STREAM_CODEC
            .map(SetToolkitSettingsPayload::new, SetToolkitSettingsPayload::settings);

    @Override
    public Type<SetToolkitSettingsPayload> type() {
        return TYPE;
    }

    public static void handle(SetToolkitSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // 只有真的打开着配置界面时才允许修改
            if (!(player.containerMenu instanceof ToolkitMenu)) {
                return;
            }

            // ToolkitSettings 的构造器里会 clamp 连锁数量
            ToolkitSettings settings = payload.settings();
            player.setData(AliyasToolkit.SETTINGS, settings);
            PacketDistributor.sendToPlayer(player, new SyncToolkitSettingsPayload(settings));
        });
    }
}
