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
    /** false = 第一栏（蓝图箱材料），true = 第二栏（蓝图内容器里的物品）。 */
    private boolean contentsView;

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

    public boolean isContentsView() {
        return this.contentsView;
    }

    public void setContentsView(boolean contentsView) {
        this.contentsView = contentsView;
    }

    @Override
    public int getContainerSlot() {
        return this.containerSlot;
    }

    /**
     * 第二栏只显示“内容容量”范围内的行：超出的行直接不参与渲染和交互，
     * 这样第二栏的行数与它自己的容量一致，不会沿用第一栏的行数。
     */
    @Override
    public boolean isActive() {
        return !this.contentsView || this.containerSlot < this.box.getContentCapacity();
    }

    @Override
    public ItemStack getItem() {
        return this.contentsView ? this.box.getContentItem(this.containerSlot)
                : this.box.getItem(this.containerSlot);
    }

    @Override
    public void set(ItemStack stack) {
        if (this.contentsView) {
            this.box.setContentItem(this.containerSlot, stack);
        } else {
            this.box.setItem(this.containerSlot, stack);
        }
    }

    @Override
    public ItemStack remove(int amount) {
        return this.contentsView ? this.box.removeContentItem(this.containerSlot, amount)
                : this.box.removeItem(this.containerSlot, amount);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (!this.isActive()) {
            return false;
        }
        return this.contentsView ? this.box.canPlaceContentItem(this.containerSlot, stack)
                : this.box.canPlaceItem(this.containerSlot, stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return this.isActive();
    }

    @Override
    public int getMaxStackSize() {
        return Math.max(0, this.getLimit());
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        int limit = this.getLimit();
        return limit <= 0 ? 0 : Math.min(limit, stack.getMaxStackSize());
    }

    public int getLimit() {
        return this.contentsView ? this.box.getContentLimit(this.containerSlot)
                : this.box.getLimit(this.containerSlot);
    }

    public boolean isDisabled() {
        return this.getLimit() <= 0;
    }

    public ItemStack getDisplayStack() {
        BlueprintData.SlotDef def = this.contentsView ? this.box.getContentSlotDef(this.containerSlot)
                : this.box.getSlotDef(this.containerSlot);
        return def == null ? ItemStack.EMPTY : def.displayStack();
    }

    @Override
    public void setChanged() {
        this.box.setChanged();
    }
}
