package com.prism.aliyastoolkit;

/**
 * 预设名称的公共常量与清理逻辑。
 * <p>
 * 单独放在这个类里、并且不引用其它模组类，是为了避免
 * {@link ChainPreset#DEFAULT} / {@link BuildPreset#DEFAULT} 在静态初始化时
 * 反过来触发 {@link ToolkitSettings} 的初始化（循环初始化会让默认对象读到 null）。
 */
public final class PresetNames {
    /** 预设名称的最大长度。 */
    public static final int MAX_LENGTH = 32;
    /** 新预设的默认名称前缀：预设1、预设2…… */
    public static final String DEFAULT_PREFIX = "预设";

    private PresetNames() {
    }

    /**
     * 清理预设名称：去掉换行/制表符（避免破坏界面显示），并限制长度。
     * {@code null} 视为空字符串；空名称由 {@link ToolkitSettings} 在构造时补默认名称。
     */
    public static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
