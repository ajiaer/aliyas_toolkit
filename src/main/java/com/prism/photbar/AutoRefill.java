package com.prism.photbar;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 自动补充逻辑（只在服务端执行）。
 * <p>
 * 客户端检测到某个快捷栏格子“从非空变成空”后，会把消失前的那个物品一起发过来；
 * 服务端据此判断：这个物品在白名单里吗？背包（9-35）里有同样的物品吗？有就把整堆移回快捷栏。
 * <p>
 * 服务端会重新校验“目标格子确实是空的”，并且是“移动”而不是“复制”，所以不会产生刷物品。
 */
public final class AutoRefill {
    /** 快捷栏格子数量。 */
    public static final int HOTBAR_SIZE = 9;
    /** 背包主体（最后一行是 27-35）的起始索引。 */
    public static final int MAIN_INVENTORY_START = 9;
    /** 背包主体的结束索引。 */
    public static final int MAIN_INVENTORY_END = 35;

    private AutoRefill() {
    }

    /**
     * 尝试用背包里的同种白名单物品补充指定的快捷栏格子。
     *
     * @param player     服务端玩家
     * @param hotbarSlot 快捷栏格子索引（0-8）
     * @param vanished   原本在这个格子里、现在已经消失的物品（用于匹配“同样的物品”）
     */
    public static void refill(ServerPlayer player, int hotbarSlot, ItemStack vanished) {
        if (!Config.ENABLE_AUTO_REFILL.getAsBoolean()) {
            return;
        }
        if (hotbarSlot < 0 || hotbarSlot >= HOTBAR_SIZE) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        if (vanished.isEmpty()) {
            return;
        }

        Inventory inventory = player.getInventory();
        // 只补“空”的格子：如果服务器上这一格已经有物品（例如玩家刚捡起来），就不做任何事
        if (!inventory.getItem(hotbarSlot).isEmpty()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // 只有白名单里的物品才会自动补充
        if (!isWhitelisted(WhitelistData.get(server), vanished)) {
            return;
        }

        int sourceSlot = findSourceSlot(inventory, vanished);
        if (sourceSlot < 0) {
            return;
        }

        // 整堆移动（不是复制）：把背包里的那一堆放到快捷栏，原格子清空
        ItemStack stack = inventory.getItem(sourceSlot);
        inventory.setItem(sourceSlot, ItemStack.EMPTY);
        inventory.setItem(hotbarSlot, stack);
        inventory.setChanged();

        InventorySync.sync(player);
    }

    private static boolean isWhitelisted(WhitelistData data, ItemStack stack) {
        for (int i = 0; i < WhitelistData.SIZE; i++) {
            ItemStack entry = data.getSlot(i);
            if (!entry.isEmpty() && matches(entry, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在背包主体（9-35）里找第一个匹配的物品。
     *
     * @return 找到的槽位索引，找不到返回 -1
     */
    private static int findSourceSlot(Inventory inventory, ItemStack wanted) {
        for (int slot = MAIN_INVENTORY_START; slot <= MAIN_INVENTORY_END; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.isEmpty()) {
                continue;
            }
            if (matches(wanted, candidate)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 判断两个物品是否算“同一个物品”。
     * <p>
     * 由配置 {@code requireExactItemMatch} 决定：
     * <ul>
     * <li>开启（默认）：物品类型 + 全部数据组件都要一致（附魔、自定义名称等），
     * 但<b>忽略耐久值</b> —— 否则“镐子用坏后补一把新的”永远匹配不上。</li>
     * <li>关闭：只比较物品类型，附魔/名称/属性都不管。</li>
     * </ul>
     */
    public static boolean matches(ItemStack wanted, ItemStack candidate) {
        if (!ItemStack.isSameItem(wanted, candidate)) {
            return false;
        }
        if (!Config.REQUIRE_EXACT_ITEM_MATCH.getAsBoolean()) {
            return true;
        }
        ItemStack a = wanted.copy();
        ItemStack b = candidate.copy();
        a.remove(DataComponents.DAMAGE);
        b.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(a, b);
    }
}

