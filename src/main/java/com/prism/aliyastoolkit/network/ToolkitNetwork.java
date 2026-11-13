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
     * 4：去掉了连锁触发方式（固定为切换），设置的同步结构随之变化。
     * 5：新增快速建造（设置里多了建造预设，新增建造相关网络包）。
     * 6：快速建造请求增加半砖朝向字段。
     * 7：设置里增加“交换行”和预设名称。
     * 8：快速建造请求改为发送第一个点的方块状态（朝向/上下半等由它决定）。
     * 9：新增剪切复制（新增剪切复制网络包）。
     * 11：蓝图箱分级、容器内容作为材料、滚动界面。
     * 12：蓝图建造改为 V 选区 + 蓝图箱收蓝图 + Ctrl+右键虚影预览；移除 Ctrl+C/X/V。
     * 13：蓝图建造改为蓝图箱双模式（Ctrl+右键进入），新增可配置快捷键；蓝图箱界面支持手持物品。
     * 14：蓝图设置新增自动收集流体，蓝图记录过滤蓝图箱并修复容器复制掉落。
     * 15：物品切换新增指定行 / 循环模式和循环方向。
     */
    public static final String PROTOCOL_VERSION = "15";

    private ToolkitNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional()：当服务器/客户端没有安装本模组时，缺少这些通道不会导致连接被拒绝。
        // 发送前会用 NetworkRegistry.hasChannel 做检查（见 AliyasToolkitClient / ClientAutoRefill /
        // ToolkitServer），否则把包发给没装模组的一方会让 NeoForge 直接断开连接。
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        // 快捷键：快捷栏 <-> 背包指定行（“交换行”设置）
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
        // 快捷键 + 滚轮切换“大量方块”预设
        registrar.playToServer(CyclePresetPayload.TYPE, CyclePresetPayload.STREAM_CODEC,
                CyclePresetPayload::handle);
        // 快速建造
        registrar.playToServer(QuickBuildPayload.TYPE, QuickBuildPayload.STREAM_CODEC, QuickBuildPayload::handle);
        registrar.playToServer(CycleBuildPresetPayload.TYPE, CycleBuildPresetPayload.STREAM_CODEC,
                CycleBuildPresetPayload::handle);
        // 剪切复制
        registrar.playToServer(CopyPastePayload.TYPE, CopyPastePayload.STREAM_CODEC, CopyPastePayload::handle);
        // 蓝图箱：记录蓝图、使用蓝图建造、复制蓝图、界面按钮
        registrar.playToServer(BlueprintCreatePayload.TYPE, BlueprintCreatePayload.STREAM_CODEC,
                BlueprintCreatePayload::handle);
        registrar.playToServer(BlueprintBuildPayload.TYPE, BlueprintBuildPayload.STREAM_CODEC,
                BlueprintBuildPayload::handle);
        registrar.playToServer(BlueprintCopyPayload.TYPE, BlueprintCopyPayload.STREAM_CODEC,
                BlueprintCopyPayload::handle);
        registrar.playToServer(BlueprintBoxActionPayload.TYPE, BlueprintBoxActionPayload.STREAM_CODEC,
                BlueprintBoxActionPayload::handle);
        registrar.playToServer(BlueprintScrollPayload.TYPE, BlueprintScrollPayload.STREAM_CODEC,
                BlueprintScrollPayload::handle);
        registrar.playToServer(OpenBlueprintBoxPayload.TYPE, OpenBlueprintBoxPayload.STREAM_CODEC,
                OpenBlueprintBoxPayload::handle);
        // 自动补充
        registrar.playToServer(RefillHotbarPayload.TYPE, RefillHotbarPayload.STREAM_CODEC, RefillHotbarPayload::handle);
    }
}
