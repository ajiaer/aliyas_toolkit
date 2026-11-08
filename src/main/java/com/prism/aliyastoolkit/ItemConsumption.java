package com.prism.aliyastoolkit;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品栏计数 / 消耗的小工具（快速建造、剪切复制共用）。
 * <p>
 * 只统计背包（槽位 9-35）和快捷栏（槽位 0-8），和“消耗背包加物品栏”的规则一致。
 */
public final class ItemConsumption {
    private ItemConsumption() {
    }

    /** 统计背包 + 快捷栏里某种物品的数量。 */
    public static int count(Inventory inventory, Item item) {
        if (item == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 优先消耗背包（槽位 9-35），不够再消耗快捷栏（0-8）。 */
    public static void consume(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            int start = pass == 0 ? 9 : 0;
            int end = pass == 0 ? 36 : 9;
            for (int i = start; i < end && remaining > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        inventory.setChanged();
    }
}
