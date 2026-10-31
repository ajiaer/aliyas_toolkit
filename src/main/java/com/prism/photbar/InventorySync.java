package com.prism.photbar;

import net.minecraft.server.level.ServerPlayer;

/**
 * 物品栏同步辅助：在服务端直接改动玩家物品栏后，把结果推送给客户端。
 */
public final class InventorySync {
    private InventorySync() {
    }

    /**
     * 同步玩家物品栏。
     * <p>
     * containerId = 0 的槽位变化包在客户端：快捷栏格子总是会被应用，其余格子只有当玩家当前打开的菜单
     * 就是 inventoryMenu 时才会被应用。所以玩家打开着箱子等 GUI 时还要额外同步当前容器菜单。
     */
    public static void sync(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }
}
