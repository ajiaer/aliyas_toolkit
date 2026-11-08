package com.prism.aliyastoolkit.menu;

import com.prism.aliyastoolkit.blueprint.BlueprintBoxView;
import com.prism.aliyastoolkit.blueprint.BlueprintData;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 蓝图箱的可见格子。
 * <p>
 * 菜单只创建“当前可见”的 5×9（或更少）个槽位；滚动时改变 {@code containerSlot} 映射，
 * 而不是移动槽位坐标，这样槽位 ID 在客户端/服务端保持一致。
 */
public class BlueprintSlot extends Slot {
    private final BlueprintBoxView box;
    private final int row;
    private final int column;
    private int containerSlot;

    public BlueprintSlot(BlueprintBoxView box, int row, int column, int x, int y) {
        super(box, row * 9 + column, x, y);
        this.box = box;
        this.row = row;
        this.column = column;
        this.containerSlot = row * 9 + column;
    }

    public int getRow() {
        return this.row;
    }

    public int getColumn() {
        return this.column;
    }

    public void setContainerSlot(int containerSlot) {
        this.containerSlot = containerSlot;
    }

    @Override
    public int getContainerSlot() {
        return this.containerSlot;
    }

    @Override
    public ItemStack getItem() {
        return this.box.getItem(this.containerSlot);
    }

    @Override
    public void set(ItemStack stack) {
        this.box.setItem(this.containerSlot, stack);
    }

    @Override
    public ItemStack remove(int amount) {
        return this.box.removeItem(this.containerSlot, amount);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.box.canPlaceItem(this.containerSlot, stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return Math.max(0, this.box.getLimit(this.containerSlot));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        int limit = this.box.getLimit(this.containerSlot);
        return limit <= 0 ? 0 : Math.min(limit, stack.getMaxStackSize());
    }

    public int getLimit() {
        return this.box.getLimit(this.containerSlot);
    }

    public boolean isDisabled() {
        return this.getLimit() <= 0;
    }

    public ItemStack getDisplayStack() {
        BlueprintData.SlotDef def = this.box.getSlotDef(this.containerSlot);
        return def == null ? ItemStack.EMPTY : def.displayStack();
    }

    @Override
    public void setChanged() {
        this.box.setChanged();
    }
}
