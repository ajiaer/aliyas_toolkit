package com.prism.photbar.network;

import com.prism.photbar.Config;
import com.prism.photbar.HotbarSwapper;
import com.prism.photbar.PHotbar;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：请求把快捷栏和背包最后一行互换。
 * <p>
 * 这个包不带任何数据（交换的槽位是固定的），所以用 {@link StreamCodec#unit(Object)} 作为编解码器。
 */
public record SwapHotbarPayload() implements CustomPacketPayload {
    /** 单例，发送时直接用这一个实例即可。 */
    public static final SwapHotbarPayload INSTANCE = new SwapHotbarPayload();

    public static final Type<SwapHotbarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PHotbar.MODID, "swap_hotbar"));

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
                HotbarSwapper.swapHotbarWithLastRow(player);
            }
        });
    }
}
