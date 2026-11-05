package com.prism.aliyastoolkit;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 快捷栏与背包某一行的交换逻辑。
 * <p>
 * 物品栏索引（{@link Inventory#items}，共 36 格）：
 * <ul>
 * <li>0-8：快捷栏</li>
 * <li>9-35：背包主体（9-17、18-26、27-35 三行）</li>
 * </ul>
 * 具体交换哪一行由玩家设置里的“交换行”决定（1 = 最上面一行，3 = 最后一行）。
 * <p>
 * 玩家物品栏由服务端权威管理，因此本类只允许在服务端调用；执行后必须把变化同步给客户端。
 */
public final class HotbarSwapper {
    /** 快捷栏起始索引。 */
    public static final int HOTBAR_START = 0;
    /** 快捷栏格子数量。 */
    public static final int HOTBAR_SIZE = 9;
    /** 背包第一行起始索引。 */
    public static final int INVENTORY_START = 9;
    /** 背包每一行的格子数量。 */
    public static final int ROW_SIZE = 9;
    /** 可交换的背包行：1 = 最上面一行，3 = 最后一行。 */
    public static final int MIN_ROW = ToolkitSettings.MIN_SWAP_ROW;
    public static final int MAX_ROW = ToolkitSettings.MAX_SWAP_ROW;

    private HotbarSwapper() {
    }

    /** 背包某一行的起始索引（row 1-3，超出范围会被夹到合法值）。 */
    public static int rowStart(int row) {
        return INVENTORY_START + (Mth.clamp(row, MIN_ROW, MAX_ROW) - 1) * ROW_SIZE;
    }

    /**
     * 把快捷栏的 9 个格子和背包指定行的 9 个格子两两互换，并把结果同步给客户端。
     *
     * @param player 要执行交换的服务端玩家
     * @param row    交换的背包行（1 = 最上面一行，3 = 最后一行）
     */
    public static void swapHotbarWithRow(ServerPlayer player, int row) {
        // 旁观模式没有可用的物品栏
        if (player.isSpectator()) {
            return;
        }

        Inventory inventory = player.getInventory();
        int rowStart = rowStart(row);

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            int hotbarSlot = HOTBAR_START + i;
            int rowSlot = rowStart + i;

            ItemStack hotbarStack = inventory.getItem(hotbarSlot);
            ItemStack rowStack = inventory.getItem(rowSlot);

            inventory.setItem(hotbarSlot, rowStack);
            inventory.setItem(rowSlot, hotbarStack);
        }

        // 标记物品栏已变化（影响 timesChanged，例如成就/统计等依赖）
        inventory.setChanged();

        // 把变化同步给客户端
        InventorySync.sync(player);
    }
}
