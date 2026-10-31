package com.prism.photbar.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 模组的网络包注册入口（在主类构造里挂到 mod 事件总线上）。
 */
public final class PHotbarNetwork {
    /**
     * 网络协议版本。
     * <p>
     * NeoForge 之间连接时，双方注册的同一个包版本必须一致；修改包结构时记得把它加一。
     */
    public static final String PROTOCOL_VERSION = "1";

    private PHotbarNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional()：当服务器/客户端没有安装本模组时，缺少这个通道不会导致连接被拒绝。
        // 发送前会用 NetworkRegistry.hasChannel 做检查（见 PHotbarClient#trySwap），
        // 否则把包发给没装模组的一方会让 NeoForge 直接断开连接。
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        // 客户端按键 -> 服务端执行交换
        registrar.playToServer(SwapHotbarPayload.TYPE, SwapHotbarPayload.STREAM_CODEC, SwapHotbarPayload::handle);
    }
}
