package com.prism.aliyastoolkit.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 带固定排序号的按键绑定。
 * <p>
 * 原版 {@code KeyMapping.compareTo} 会按翻译后的名称排序；这里让同一分类下的
 * 模组按键按功能页顺序显示（打开设置、物品切换、连锁破坏、快速建造、蓝图相关），
 * 不受语言和名称影响。
 */
public class OrderedKeyMapping extends KeyMapping {
    private final int order;

    public OrderedKeyMapping(String description, IKeyConflictContext conflictContext, KeyModifier modifier,
            InputConstants.Type type, int keyCode, String category, int order) {
        super(description, conflictContext, modifier, type, keyCode, category);
        this.order = order;
    }

    public OrderedKeyMapping(String description, IKeyConflictContext conflictContext, InputConstants.Type type,
            int keyCode, String category, int order) {
        super(description, conflictContext, type, keyCode, category);
        this.order = order;
    }

    @Override
    public int compareTo(KeyMapping other) {
        if (other instanceof OrderedKeyMapping ordered) {
            return Integer.compare(this.order, ordered.order);
        }
        return super.compareTo(other);
    }
}
