package com.prism.aliyastoolkit.blueprint;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 以玩家物品栏里的蓝图箱物品为后端的容器。
 * <p>
 * 方块版 {@link BlueprintBoxBlockEntity} 把数据存在方块实体里；这里则直接读写物品的
 * {@code minecraft:container} 和 {@code minecraft:block_entity_data} 组件。
 * 菜单、按钮和滚动逻辑因此可以和方块版完全共用。
 */
public class BlueprintBoxItemContainer implements BlueprintBoxView {
    private final Player player;
    private final int slot;

    /** 当前追踪的物品；客户端收到新的物品数据后对象会变化，用来刷新缓存。 */
    private ItemStack trackedStack = ItemStack.EMPTY;
    private int capacity;
    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private int[] limits = new int[0];
    @Nullable
    private BlueprintData blueprint;
    private List<BlueprintData.SlotDef> slotDefs = List.of();

    public BlueprintBoxItemContainer(Player player, int slot) {
        this.player = player;
        this.slot = slot;
        this.refresh();
    }

    private ItemStack currentStack() {
        if (this.slot < 0 || this.slot >= this.player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return this.player.getInventory().getItem(this.slot);
    }

    /**
     * 物品栏槽位可能被服务端同步替换成新的 ItemStack（新的组件数据）。
     * 这里用对象身份判断是否需要重新读取，避免每帧解析蓝图 NBT。
     */
    private void refreshIfNeeded() {
        ItemStack current = this.currentStack();
        if (current != this.trackedStack) {
            this.refresh();
        }
    }

    private void refresh() {
        this.trackedStack = this.currentStack();
        this.capacity = BlueprintBoxData.capacity(this.trackedStack);
        if (this.capacity <= 0) {
            this.items = NonNullList.withSize(0, ItemStack.EMPTY);
            this.limits = new int[0];
            this.blueprint = null;
            this.slotDefs = List.of();
            return;
        }
        HolderLookup.Provider registries = this.player.level().registryAccess();
        this.blueprint = BlueprintBoxData.readBlueprint(this.trackedStack, registries);
        this.slotDefs = this.blueprint == null ? List.of() : this.blueprint.createSlots(this.capacity);
        this.limits = BlueprintBoxData.readOrCreateLimits(this.trackedStack, this.blueprint, this.capacity);
        this.items = BlueprintBoxData.readItems(this.trackedStack, this.capacity);
    }

    private void persist() {
        // 客户端只维护一份用于界面显示的缓存；真正的数据由服务端写入物品组件并同步下来。
        if (this.player.level().isClientSide) {
            return;
        }
        ItemStack stack = this.currentStack();
        if (!(stack.getItem() instanceof BlueprintBoxItem) || this.capacity <= 0) {
            return;
        }
        HolderLookup.Provider registries = this.player.level().registryAccess();
        BlueprintBoxData.writeItems(stack, this.items);
        BlueprintBoxData.writeBlueprint(stack, this.blueprint, this.limits, registries);
    }

    @Override
    public BlueprintBoxReference reference() {
        return BlueprintBoxReference.item(this.slot);
    }

    @Override
    public int getCapacity() {
        this.refreshIfNeeded();
        return this.capacity;
    }

    public Component getDisplayName() {
        this.refreshIfNeeded();
        return this.trackedStack.getHoverName();
    }

    @Override
    public int getContainerSize() {
        this.refreshIfNeeded();
        return this.capacity;
    }

    @Override
    public boolean isEmpty() {
        this.refreshIfNeeded();
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        this.refreshIfNeeded();
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        this.refreshIfNeeded();
        if (slot < 0 || slot >= this.items.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = stack.split(amount);
        if (stack.isEmpty()) {
            this.items.set(slot, ItemStack.EMPTY);
        }
        this.setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        this.refreshIfNeeded();
        if (slot < 0 || slot >= this.items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        this.items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.refreshIfNeeded();
        if (slot < 0 || slot >= this.items.size() || isNestedBlueprintBox(stack)) {
            return;
        }
        // 客户端的槽位内容来自服务端同步，直接采用，避免因缓存稍旧而拒绝同步。
        if (this.player.level().isClientSide) {
            this.items.set(slot, stack.copy());
            return;
        }
        int limit = this.getLimit(slot);
        BlueprintData.SlotDef def = this.getSlotDef(slot);
        if (limit <= 0 || def == null) {
            if (stack.isEmpty()) {
                this.items.set(slot, ItemStack.EMPTY);
                this.setChanged();
            }
            return;
        }
        if (!stack.isEmpty() && !def.matches(stack)) {
            return;
        }
        ItemStack stored = stack;
        if (stored.getCount() > limit) {
            stored = stored.copyWithCount(limit);
        }
        this.items.set(slot, stored);
        this.setChanged();
    }

    @Override
    public void setChanged() {
        this.persist();
        if (!this.player.level().isClientSide) {
            this.player.getInventory().setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        this.refreshIfNeeded();
        int limit = this.getLimit(slot);
        BlueprintData.SlotDef def = this.getSlotDef(slot);
        return limit > 0 && def != null && !stack.isEmpty() && !isNestedBlueprintBox(stack)
                && def.matches(stack) && this.items.get(slot).getCount() < limit;
    }

    @Override
    public boolean stillValid(Player player) {
        this.refreshIfNeeded();
        return this.slot >= 0
                && this.slot < player.getInventory().getContainerSize()
                && player.getInventory().getItem(this.slot).getItem() instanceof BlueprintBoxItem;
    }

    @Override
    public void clearContent() {
        this.refreshIfNeeded();
        this.items.clear();
        this.setChanged();
    }

    @Nullable
    @Override
    public BlueprintData getBlueprint() {
        this.refreshIfNeeded();
        return this.blueprint;
    }

    @Override
    public boolean hasBlueprint() {
        BlueprintData data = this.getBlueprint();
        return data != null && !data.isEmpty();
    }

    @Override
    public int[] getLimits() {
        this.refreshIfNeeded();
        return Arrays.copyOf(this.limits, this.limits.length);
    }

    @Override
    public int getLimit(int slot) {
        this.refreshIfNeeded();
        return slot >= 0 && slot < this.limits.length ? this.limits[slot] : 0;
    }

    @Override
    public List<BlueprintData.SlotDef> getSlotDefs() {
        this.refreshIfNeeded();
        return this.slotDefs;
    }

    @Nullable
    @Override
    public BlueprintData.SlotDef getSlotDef(int slot) {
        this.refreshIfNeeded();
        return slot >= 0 && slot < this.slotDefs.size() ? this.slotDefs.get(slot) : null;
    }

    @Override
    public void applyBlueprint(@Nullable BlueprintData blueprint) {
        this.refreshIfNeeded();
        this.blueprint = blueprint;
        this.slotDefs = blueprint == null ? List.of() : blueprint.createSlots(this.capacity);
        this.limits = new int[this.capacity];
        for (int i = 0; i < Math.min(this.slotDefs.size(), this.limits.length); i++) {
            this.limits[i] = this.slotDefs.get(i).limit();
        }
        this.setChanged();
    }

    @Override
    public void clearBlueprint() {
        this.refreshIfNeeded();
        this.blueprint = null;
        this.slotDefs = List.of();
        this.limits = new int[this.capacity];
        this.setChanged();
    }

    @Override
    public boolean hasContentsOrBlueprint() {
        return this.hasBlueprint() || !this.isEmpty();
    }

    private static boolean isNestedBlueprintBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlueprintBoxItem;
    }
}
