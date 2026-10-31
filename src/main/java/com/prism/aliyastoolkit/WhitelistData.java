package com.prism.aliyastoolkit;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家的物品白名单（3x3，共 9 格）。
 * <p>
 * 通过 Data Attachment 绑定在玩家身上（见 {@link AliyasToolkit#WHITELIST}），所以：
 * <ul>
 * <li>每个玩家有自己独立的一份白名单，互不影响</li>
 * <li>随玩家数据一起保存（多人服存在玩家数据里，单人存在存档的 playerdata 里）</li>
 * <li>死亡/重生后保留（{@code copyOnDeath}）</li>
 * </ul>
 */
public class WhitelistData {
    /** 白名单格子数量（3x3）。 */
    public static final int SIZE = 9;

    /** Data Attachment 的序列化方式：9 个 ItemStack 的列表。 */
    public static final Codec<WhitelistData> CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .xmap(WhitelistData::fromList, WhitelistData::toList);

    private final NonNullList<ItemStack> stacks = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    private static WhitelistData fromList(List<ItemStack> list) {
        WhitelistData data = new WhitelistData();
        for (int i = 0; i < Math.min(list.size(), SIZE); i++) {
            ItemStack stack = list.get(i);
            if (!stack.isEmpty()) {
                data.stacks.set(i, stack.copyWithCount(1));
            }
        }
        return data;
    }

    private List<ItemStack> toList() {
        return new ArrayList<>(this.stacks);
    }

    /** 读取某一格（返回的是内部实例，调用方不要修改它）。 */
    public ItemStack getSlot(int index) {
        return index >= 0 && index < SIZE ? this.stacks.get(index) : ItemStack.EMPTY;
    }

    /**
     * 设置某一格。白名单只记录“物品本身”，所以数量统一存 1。
     */
    public void setSlot(int index, ItemStack stack) {
        if (index < 0 || index >= SIZE) {
            return;
        }
        this.stacks.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }
}

