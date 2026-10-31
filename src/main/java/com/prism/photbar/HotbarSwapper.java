package com.prism.photbar;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 快捷栏与背包最后一行的交换逻辑。
 * <p>
 * 物品栏索引（{@link Inventory#items}，共 36 格）：
 * <ul>
 * <li>0-8：快捷栏</li>
 * <li>9-35：背包主体（9-17、18-26、27-35 三行）</li>
 * </ul>
 * 所以「背包最后一行」就是 27-35。
 * <p>
 * 玩家物品栏由服务端权威管理，因此本类只允许在服务端调用；执行后必须把变化同步给客户端。
 */
public final class HotbarSwapper {
    /** 快捷栏起始索引。 */
    public static final int HOTBAR_START = 0;
    /** 快捷栏格子数量。 */
    public static final int HOTBAR_SIZE = 9;
    /** 背包最后一行起始索引。 */
    public static final int LAST_ROW_START = 27;

    private HotbarSwapper() {
    }

    /**
     * 把快捷栏的 9 个格子和背包最后一行的 9 个格子两两互换，并把结果同步给客户端。
     *
     * @param player 要执行交换的服务端玩家
     */
    public static void swapHotbarWithLastRow(ServerPlayer player) {
        // 旁观模式没有可用的物品栏
        if (player.isSpectator()) {
            return;
        }

        Inventory inventory = player.getInventory();

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            int hotbarSlot = HOTBAR_START + i;
            int lastRowSlot = LAST_ROW_START + i;

            ItemStack hotbarStack = inventory.getItem(hotbarSlot);
            ItemStack lastRowStack = inventory.getItem(lastRowSlot);

            inventory.setItem(hotbarSlot, lastRowStack);
            inventory.setItem(lastRowSlot, hotbarStack);
        }

        // 标记物品栏已变化（影响 timesChanged，例如成就/统计等依赖）
        inventory.setChanged();

        // containerId = 0 的包在客户端会被应用到 inventoryMenu 上，快捷栏槽位比较特殊，
        // 其余格子只有在当前打开的菜单就是 inventoryMenu 时才会被应用，所以两个都广播一遍：
        // 1) inventoryMenu：玩家没有打开任何容器时走这里
        // 2) 当前打开的容器菜单：玩家打开箱子等 GUI 时走这里（否则客户端 9-35 格会延迟到下一 tick 才更新）
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }
}
