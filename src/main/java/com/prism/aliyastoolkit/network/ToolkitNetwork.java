package com.prism.aliyastoolkit.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 模组的网络包注册入口（在主类构造里挂到 mod 事件总线上）。
 */
public final class ToolkitNetwork {
    /**
     * 网络协议版本。
     * <p>
     * NeoForge 之间连接时，双方注册的同一个包版本必须一致；修改包结构时记得把它加一。
     */
    public static final String PROTOCOL_VERSION = "2";

    private ToolkitNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional()：当服务器/客户端没有安装本模组时，缺少这些通道不会导致连接被拒绝。
        // 发送前会用 NetworkRegistry.hasChannel 做检查（见 AliyasToolkitClient / ClientAutoRefill /
        // ToolkitServer），否则把包发给没装模组的一方会让 NeoForge 直接断开连接。
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        // 快捷键：快捷栏 <-> 背包最后一行
        registrar.playToServer(SwapHotbarPayload.TYPE, SwapHotbarPayload.STREAM_CODEC, SwapHotbarPayload::handle);
        // “Aliya's Toolkit 配置”界面（白名单 + 各项设置）
        registrar.playToServer(OpenToolkitPayload.TYPE, OpenToolkitPayload.STREAM_CODEC, OpenToolkitPayload::handle);
        registrar.playToServer(SetWhitelistSlotPayload.TYPE, SetWhitelistSlotPayload.STREAM_CODEC,
                SetWhitelistSlotPayload::handle);
        registrar.playToServer(ClearWhitelistSlotPayload.TYPE, ClearWhitelistSlotPayload.STREAM_CODEC,
                ClearWhitelistSlotPayload::handle);
        // 设置修改与同步
        registrar.playToServer(SetToolkitSettingsPayload.TYPE, SetToolkitSettingsPayload.STREAM_CODEC,
                SetToolkitSettingsPayload::handle);
        registrar.playToClient(SyncToolkitSettingsPayload.TYPE, SyncToolkitSettingsPayload.STREAM_CODEC,
                SyncToolkitSettingsPayload::handle);
        // 连锁快捷键的激活状态
        registrar.playToServer(SetChainStatePayload.TYPE, SetChainStatePayload.STREAM_CODEC,
                SetChainStatePayload::handle);
        // 自动补充
        registrar.playToServer(RefillHotbarPayload.TYPE, RefillHotbarPayload.STREAM_CODEC, RefillHotbarPayload::handle);
    }
}
