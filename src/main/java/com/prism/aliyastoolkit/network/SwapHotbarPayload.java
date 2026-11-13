package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.HotbarSwapper;
import com.prism.aliyastoolkit.ToolkitSettings;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：请求执行快捷栏切换。
 * <p>
 * 这个包不带任何数据（具体使用指定行还是循环、以及方向和行号，由服务端保存的玩家设置决定），
 * 所以用 {@link StreamCodec#unit(Object)} 作为编解码器。
 */
public record SwapHotbarPayload() implements CustomPacketPayload {
    /** 单例，发送时直接用这一个实例即可。 */
    public static final SwapHotbarPayload INSTANCE = new SwapHotbarPayload();

    public static final Type<SwapHotbarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "swap_hotbar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwapHotbarPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<SwapHotbarPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理逻辑。
     * <p>
     * {@link net.neoforged.neoforge.network.registration.PayloadRegistrar} 默认会把处理器包装成
     * {@link net.neoforged.neoforge.network.handling.MainThreadPayloadHandler}，这里再显式 {@code enqueueWork} 一次，
     * 保证即使以后改成网络线程处理也不会在错误的线程上动世界状态。
     */
    public static void handle(SwapHotbarPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!Config.ENABLE_HOTBAR_SWAP.getAsBoolean()) {
                return;
            }
            if (context.player() instanceof ServerPlayer player) {
                ToolkitSettings settings = player.getData(AliyasToolkit.SETTINGS);
                if (settings.swapMode() == ToolkitSettings.SwapMode.CYCLE) {
                    HotbarSwapper.cycleRows(player,
                            settings.cycleDirection() == ToolkitSettings.CycleDirection.REVERSE);
                } else {
                    HotbarSwapper.swapHotbarWithRow(player, settings.swapRow());
                }
            }
        });
    }
}
