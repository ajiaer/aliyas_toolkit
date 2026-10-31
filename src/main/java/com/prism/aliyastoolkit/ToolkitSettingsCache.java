package com.prism.aliyastoolkit;

/**
 * “Aliya's Toolkit 配置”设置的客户端缓存。
 * <p>
 * 服务端在玩家登录/修改设置时会发 {@code SyncToolkitSettingsPayload}，
 * 客户端收到后写进这里；连锁预览、快捷键行为都读这份缓存，避免每个 tick 都去问服务端。
 * <p>
 * 放在通用包里（内容只是一份 {@link ToolkitSettings}），这样网络包类不需要引用客户端专用类，
 * 专用服务器上加载网络包时也不会碰到客户端类。
 */
public final class ToolkitSettingsCache {
    private static volatile ToolkitSettings settings = ToolkitSettings.DEFAULT;

    private ToolkitSettingsCache() {
    }

    public static ToolkitSettings get() {
        return settings;
    }

    public static void set(ToolkitSettings value) {
        settings = value == null ? ToolkitSettings.DEFAULT : value;
    }

    public static void reset() {
        settings = ToolkitSettings.DEFAULT;
    }
}
