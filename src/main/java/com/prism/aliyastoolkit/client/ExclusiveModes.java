package com.prism.aliyastoolkit.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端“互斥模式”管理器。
 * <p>
 * 连锁破坏、快速建造以及以后新增的同类功能都注册到这里：同一时间最多只有一个处于开启状态，
 * 开启某个模式会自动关闭其它模式。快捷栏交换、自动补充不注册，因此不受影响。
 * <p>
 * 新功能接入方式：在 {@code AliyasToolkitClient} 构造里调用
 * {@link #register(String, Mode)} 注册，开启时调用 {@link #activate(String)}，
 * 关闭时调用 {@link #deactivate(String)}。
 */
public final class ExclusiveModes {
    /** 连锁破坏的模式 id。 */
    public static final String CHAIN_MINING = "chain_mining";
    /** 快速建造的模式 id。 */
    public static final String QUICK_BUILD = "quick_build";

    /** 一个可以被强制关闭的模式。 */
    @FunctionalInterface
    public interface Mode {
        void forceDisable();
    }

    private static final Map<String, Mode> MODES = new LinkedHashMap<>();
    private static String activeMode;

    private ExclusiveModes() {
    }

    public static void register(String id, Mode mode) {
        MODES.put(id, mode);
    }

    /** 开启某个模式，并强制关闭其它所有模式。 */
    public static void activate(String id) {
        if (!MODES.containsKey(id)) {
            return;
        }
        for (Map.Entry<String, Mode> entry : MODES.entrySet()) {
            if (!entry.getKey().equals(id)) {
                entry.getValue().forceDisable();
            }
        }
        activeMode = id;
    }

    /** 关闭某个模式（只有它当前处于开启状态时才清除记录）。 */
    public static void deactivate(String id) {
        if (id.equals(activeMode)) {
            activeMode = null;
        }
    }

    public static boolean isActive(String id) {
        return id.equals(activeMode);
    }
}
