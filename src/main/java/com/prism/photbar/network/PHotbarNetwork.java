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
        // optional()：当服务器/客户端没有安装本模组时，缺少这些通道不会导致连接被拒绝。
        // 发送前会用 NetworkRegistry.hasChannel 做检查（见 PHotbarClient / ClientAutoRefill），
        // 否则把包发给没装模组的一方会让 NeoForge 直接断开连接。
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        // 快捷键：快捷栏 <-> 背包最后一行
        registrar.playToServer(SwapHotbarPayload.TYPE, SwapHotbarPayload.STREAM_CODEC, SwapHotbarPayload::handle);
        // 白名单界面
        registrar.playToServer(OpenWhitelistPayload.TYPE, OpenWhitelistPayload.STREAM_CODEC, OpenWhitelistPayload::handle);
        registrar.playToServer(SetWhitelistSlotPayload.TYPE, SetWhitelistSlotPayload.STREAM_CODEC, SetWhitelistSlotPayload::handle);
        registrar.playToServer(ClearWhitelistSlotPayload.TYPE, ClearWhitelistSlotPayload.STREAM_CODEC, ClearWhitelistSlotPayload::handle);
        // 自动补充
        registrar.playToServer(RefillHotbarPayload.TYPE, RefillHotbarPayload.STREAM_CODEC, RefillHotbarPayload::handle);
    }
}
