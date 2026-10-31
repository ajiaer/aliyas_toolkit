package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.ToolkitSettings;
import com.prism.aliyastoolkit.ToolkitSettingsCache;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：同步这名玩家的功能设置。
 * <p>
 * 登录时发一次（让客户端知道默认值/存档里的值），玩家在配置界面里改动后再发一次。
 * 客户端把它缓存到 {@link ToolkitSettingsCache}，连锁预览、快捷键行为都用这份缓存。
 */
public record SyncToolkitSettingsPayload(ToolkitSettings settings) implements CustomPacketPayload {
    public static final Type<SyncToolkitSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "sync_toolkit_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncToolkitSettingsPayload> STREAM_CODEC = ToolkitSettings.STREAM_CODEC
            .map(SyncToolkitSettingsPayload::new, SyncToolkitSettingsPayload::settings);

    @Override
    public Type<SyncToolkitSettingsPayload> type() {
        return TYPE;
    }

    public static void handle(SyncToolkitSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ToolkitSettingsCache.set(payload.settings()));
    }
}
