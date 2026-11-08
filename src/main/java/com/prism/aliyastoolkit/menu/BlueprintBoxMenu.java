package com.prism.aliyastoolkit.menu;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlock;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlockEntity;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 蓝图箱界面菜单：最多显示 5 行 × 9 列，列与玩家背包列对齐。
 * <p>
 * 菜单只创建可见槽位；滚动时修改每个槽位对应的容器索引，
 * 因此客户端/服务端的槽位 ID 不变，漏斗/按钮仍访问完整箱子。
 */
public class BlueprintBoxMenu extends AbstractContainerMenu {
    public static final int VISIBLE_ROWS = 5;
    private static final int SLOT_X = 8;
    private static final int SLOT_Y = 18;

    private final BlueprintBoxBlockEntity box;
    private final int totalRows;
    private final int visibleRows;
    private final int visibleSlots;
    private int scrollRow;

    public BlueprintBoxMenu(int containerId, Inventory playerInventory, BlueprintBoxBlockEntity box) {
        super(AliyasToolkit.BLUEPRINT_BOX_MENU.get(), containerId);
        this.box = box;
        this.totalRows = box.getContainerSize() / 9;
        this.visibleRows = Math.min(VISIBLE_ROWS, this.totalRows);
        this.visibleSlots = this.visibleRows * 9;
        this.scrollRow = 0;

        for (int row = 0; row < this.visibleRows; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new BlueprintSlot(box, row, column, SLOT_X + column * 18, SLOT_Y + row * 18));
            }
        }

        int inventoryY = SLOT_Y + this.visibleRows * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        SLOT_X + column * 18, inventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, SLOT_X + column * 18, inventoryY + 3 * 18 + 4));
        }
        // 与原版 ChestMenu 一样通知箱子“有人打开”，驱动箱盖动画。
        this.box.startOpen(playerInventory.player);
    }

    public BlueprintBoxMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, findBox(playerInventory, data.readBlockPos()));
    }

    private static BlueprintBoxBlockEntity findBox(Inventory playerInventory, BlockPos pos) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof BlueprintBoxBlockEntity box) {
            return box;
        }
        return new BlueprintBoxBlockEntity(BlueprintBoxes.BLUEPRINT_BOX_BE.get(), pos,
                playerInventory.player.level().getBlockState(pos));
    }

    public BlueprintBoxBlockEntity getBox() {
        return this.box;
    }

    public int getTotalRows() {
        return this.totalRows;
    }

    public int getVisibleRows() {
        return this.visibleRows;
    }

    public int getVisibleSlots() {
        return this.visibleSlots;
    }

    public int getScrollRow() {
        return this.scrollRow;
    }

    public int getMaxScroll() {
        return Math.max(0, this.totalRows - this.visibleRows);
    }

    /** 滚动到指定行；更新每个可见槽位对应的容器索引。 */
    public void setScrollRow(int row) {
        int clamped = Mth.clamp(row, 0, this.getMaxScroll());
        if (clamped == this.scrollRow) {
            return;
        }
        this.scrollRow = clamped;
        for (int i = 0; i < this.visibleSlots; i++) {
            if (this.slots.get(i) instanceof BlueprintSlot slot) {
                slot.setContainerSlot((clamped + slot.getRow()) * 9 + slot.getColumn());
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.box.stopOpen(player);
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.box.getLevel() == null) {
            return true;
        }
        return this.box.getLevel().getBlockState(this.box.getBlockPos()).getBlock() instanceof BlueprintBoxBlock
                && player.distanceToSqr(Vec3.atCenterOf(this.box.getBlockPos())) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < this.visibleSlots) {
            if (!this.moveItemStackTo(stack, this.visibleSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 与原版 ChestMenu 一致：玩家槽位 shift-click 只尝试放进箱子，
            // 放不下就保持在原槽，不再在背包/快捷栏之间二次转移。
            if (!this.moveItemStackTo(stack, 0, this.visibleSlots, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }
}
