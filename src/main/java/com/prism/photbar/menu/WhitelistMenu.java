package com.prism.photbar.menu;

import com.prism.photbar.PHotbar;
import com.prism.photbar.WhitelistData;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 白名单配置界面用的容器菜单。
 * <p>
 * 布局：上方 3x3 白名单格子（0-8），下方玩家的背包 3 行 + 快捷栏（9-44）。
 * 没有人物/装备/合成区域。
 * <p>
 * 白名单格子既不能放入也不能取出（{@link WhitelistSlot}），设置/清空由
 * {@code WhitelistScreen} 拦截鼠标点击后发送自定义网络包完成。
 */
public class WhitelistMenu extends AbstractContainerMenu {
    public static final int WHITELIST_SIZE = 9;
    public static final int WHITELIST_COLUMNS = 3;
    /** 白名单格子左上角坐标（相对 GUI）。 */
    public static final int WHITELIST_X = 61;
    public static final int WHITELIST_Y = 18;
    /** 玩家背包格子左上角坐标（相对 GUI）。 */
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 88;
    public static final int HOTBAR_Y = 144;

    private final Container whitelist;

    /** 客户端创建时使用（内容由服务端同步过来）。 */
    public WhitelistMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(WHITELIST_SIZE));
    }

    public WhitelistMenu(int containerId, Inventory playerInventory, Container whitelist) {
        super(PHotbar.WHITELIST_MENU.get(), containerId);
        checkContainerSize(whitelist, WHITELIST_SIZE);
        this.whitelist = whitelist;

        // 上方 3x3 白名单
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < WHITELIST_COLUMNS; column++) {
                int index = column + row * WHITELIST_COLUMNS;
                this.addSlot(new WhitelistSlot(whitelist, index, WHITELIST_X + column * 18, WHITELIST_Y + row * 18));
            }
        }

        // 玩家背包 3 行
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }

        // 玩家快捷栏
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
        }
    }

    /**
     * 用存档里的白名单构造菜单（服务端打开界面时使用）。
     */
    public static WhitelistMenu fromData(int containerId, Inventory playerInventory, WhitelistData data) {
        SimpleContainer container = new SimpleContainer(WHITELIST_SIZE);
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            ItemStack stack = data.getSlot(i);
            if (!stack.isEmpty()) {
                container.setItem(i, stack.copyWithCount(1));
            }
        }
        return new WhitelistMenu(containerId, playerInventory, container);
    }

    public Container getWhitelist() {
        return this.whitelist;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        // 白名单格子不允许 shift 点击
        if (index < WHITELIST_SIZE) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < WHITELIST_SIZE + 27) {
            // 背包 -> 快捷栏
            moved = this.moveItemStackTo(stack, WHITELIST_SIZE + 27, this.slots.size(), false);
        } else {
            // 快捷栏 -> 背包
            moved = this.moveItemStackTo(stack, WHITELIST_SIZE, WHITELIST_SIZE + 27, false);
        }

        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 白名单格子：只用于显示，禁止放入/取出。
     */
    public static class WhitelistSlot extends Slot {
        public WhitelistSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
