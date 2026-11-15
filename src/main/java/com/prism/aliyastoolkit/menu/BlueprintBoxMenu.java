package com.prism.aliyastoolkit.menu;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlockEntity;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItemContainer;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxReference;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxView;
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

    private final BlueprintBoxView box;
    private final BlueprintBoxReference reference;
    private final int totalRows;
    private final int visibleRows;
    private final int visibleSlots;
    private int scrollRow;
    /** 0 = 蓝图箱材料，1 = 蓝图内容器里的物品。 */
    private int viewMode;

    public BlueprintBoxMenu(int containerId, Inventory playerInventory, BlueprintBoxBlockEntity box) {
        this(containerId, playerInventory, box, BlueprintBoxReference.block(box.getBlockPos()));
    }

    /** 打开玩家物品栏里蓝图箱物品的界面。 */
    public BlueprintBoxMenu(int containerId, Inventory playerInventory, int itemSlot) {
        this(containerId, playerInventory, resolveItem(playerInventory, itemSlot));
    }

    private BlueprintBoxMenu(int containerId, Inventory playerInventory, Resolved resolved) {
        this(containerId, playerInventory, resolved.box(), resolved.reference());
    }

    public BlueprintBoxMenu(int containerId, Inventory playerInventory, BlueprintBoxView box,
            BlueprintBoxReference reference) {
        super(AliyasToolkit.BLUEPRINT_BOX_MENU.get(), containerId);
        this.box = box;
        this.reference = reference;
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
        if (box instanceof BlueprintBoxBlockEntity blockEntity) {
            blockEntity.startOpen(playerInventory.player);
        }
    }

    public BlueprintBoxMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory,
                resolve(playerInventory, BlueprintBoxReference.STREAM_CODEC.decode(data)));
    }

    private record Resolved(BlueprintBoxView box, BlueprintBoxReference reference) {
    }

    private static Resolved resolve(Inventory playerInventory, BlueprintBoxReference reference) {
        if (reference.isItem()) {
            return resolveItem(playerInventory, reference.slot());
        }
        return new Resolved(findBox(playerInventory, reference.pos()), reference);
    }

    private static Resolved resolveItem(Inventory playerInventory, int slot) {
        return new Resolved(new BlueprintBoxItemContainer(playerInventory.player, slot),
                BlueprintBoxReference.item(slot));
    }

    private static BlueprintBoxBlockEntity findBox(Inventory playerInventory, BlockPos pos) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof BlueprintBoxBlockEntity box) {
            return box;
        }
        return new BlueprintBoxBlockEntity(BlueprintBoxes.BLUEPRINT_BOX_BE.get(), pos,
                playerInventory.player.level().getBlockState(pos));
    }

    public BlueprintBoxView getBox() {
        return this.box;
    }

    public BlueprintBoxReference getReference() {
        return this.reference;
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

    public int getViewMode() {
        return this.viewMode;
    }

    /** 0 = 蓝图箱材料，1 = 蓝图内容器里的物品；切换后所有可见格子读写另一份数据。 */
    public void setViewMode(int mode) {
        this.viewMode = mode == 1 ? 1 : 0;
        for (int i = 0; i < this.visibleSlots; i++) {
            if (this.slots.get(i) instanceof BlueprintSlot slot) {
                slot.setContentsView(this.viewMode == 1);
            }
        }
        // 两栏的总行数不同，切换后按新的一栏重新限制滚动位置并重新映射槽位。
        this.applyScroll(Mth.clamp(this.scrollRow, 0, this.getMaxScroll()));
    }

    public int getMaxScroll() {
        int rows = this.viewMode == 1 ? this.getContentRows() : this.totalRows;
        return Math.max(0, rows - this.visibleRows);
    }

    /** 第二栏（容器内容物）有多少行：按它自己的容量算，最多 {@link #VISIBLE_ROWS} 行。 */
    public int getContentRows() {
        return Math.min(VISIBLE_ROWS, (this.box.getContentCapacity() + 8) / 9);
    }

    /** 当前这一栏实际显示的行数：第一栏按箱子容量，第二栏按内容容量。 */
    public int getVisibleRowsForView() {
        return this.viewMode == 1 ? this.getContentRows() : this.visibleRows;
    }

    /** 滚动到指定行；更新每个可见槽位对应的容器索引。 */
    public void setScrollRow(int row) {
        int clamped = Mth.clamp(row, 0, this.getMaxScroll());
        if (clamped == this.scrollRow) {
            return;
        }
        this.applyScroll(clamped);
    }

    private void applyScroll(int row) {
        this.scrollRow = row;
        for (int i = 0; i < this.visibleSlots; i++) {
            if (this.slots.get(i) instanceof BlueprintSlot slot) {
                slot.setContainerSlot((row + slot.getRow()) * 9 + slot.getColumn());
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.box instanceof BlueprintBoxBlockEntity blockEntity) {
            blockEntity.stopOpen(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.box.stillValid(player);
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
