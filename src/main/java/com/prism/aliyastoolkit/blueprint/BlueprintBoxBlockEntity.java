package com.prism.aliyastoolkit.blueprint;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;

/**
 * 蓝图箱方块实体：物品 + 蓝图 + 每格上限 + 比较器输出。
 * <p>
 * 没有蓝图时所有格子上限为 0（禁用）。记录蓝图后按精确 ItemStack 生成占位。
 * 方块实体实现 {@link WorldlyContainer}，漏斗可以像普通箱子一样抽取；
 * 插入时通过面检查确保不会超过每格上限。
 */
public class BlueprintBoxBlockEntity extends ChestBlockEntity implements WorldlyContainer, BlueprintBoxView {
    private NonNullList<ItemStack> items;
    private int[] limits;
    @Nullable
    private BlueprintData blueprint;
    private List<BlueprintData.SlotDef> slotDefs = List.of();

    public BlueprintBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int slots = slotCount(state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
        this.limits = new int[slots];
    }

    public BlueprintBoxBlockEntity(BlockPos pos, BlockState state) {
        this(BlueprintBoxes.BLUEPRINT_BOX_BE.get(), pos, state);
    }

    private static int slotCount(BlockState state) {
        return state.getBlock() instanceof BlueprintBoxBlock box ? box.tier().slots() : BlueprintTier.LAPIS.slots();
    }

    public BlueprintTier tier() {
        return this.getBlockState().getBlock() instanceof BlueprintBoxBlock box ? box.tier() : BlueprintTier.LAPIS;
    }

    /**
     * 我们的菜单是 {@link BlueprintBoxMenu}，不是原版 {@code ChestMenu}，
     * 原版 {@code ContainerOpenersCounter} 的玩家扫描无法识别它。
     * 菜单构造和 {@code removed} 已经成对调用 {@code startOpen/stopOpen}，
     * 因此禁用 5 tick 后的自动重检，避免箱子打开后盖子在 5 tick 后被误判为关闭。
     */
    @Override
    public void recheckOpen() {
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
        if (this.limits.length != items.size()) {
            this.limits = Arrays.copyOf(this.limits, items.size());
        }
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public int getCapacity() {
        return this.items.size();
    }

    @Override
    public BlueprintBoxReference reference() {
        return BlueprintBoxReference.block(this.worldPosition);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.level != null
                && this.level.getBlockState(this.worldPosition).getBlock() instanceof BlueprintBoxBlock
                && player.distanceToSqr(Vec3.atCenterOf(this.worldPosition)) <= 64.0D;
    }

    @Override
    protected Component getDefaultName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BlueprintBoxMenu(containerId, inventory, this);
    }

    @Nullable
    public BlueprintData getBlueprint() {
        return this.blueprint;
    }

    public boolean hasBlueprint() {
        return this.blueprint != null && !this.blueprint.isEmpty();
    }

    public int[] getLimits() {
        return Arrays.copyOf(this.limits, this.limits.length);
    }

    public int getLimit(int slot) {
        return slot >= 0 && slot < this.limits.length ? this.limits[slot] : 0;
    }

    public List<BlueprintData.SlotDef> getSlotDefs() {
        return this.slotDefs;
    }

    @Nullable
    public BlueprintData.SlotDef getSlotDef(int slot) {
        return slot >= 0 && slot < this.slotDefs.size() ? this.slotDefs.get(slot) : null;
    }

    @Nullable
    public ItemStack getRequiredTemplate(int slot) {
        BlueprintData.SlotDef def = this.getSlotDef(slot);
        return def == null ? null : def.template();
    }

    public void applyBlueprint(BlueprintData blueprint) {
        this.blueprint = blueprint;
        this.slotDefs = blueprint == null ? List.of() : blueprint.createSlots(this.items.size());
        this.limits = new int[this.items.size()];
        for (int i = 0; i < Math.min(this.slotDefs.size(), this.limits.length); i++) {
            this.limits[i] = this.slotDefs.get(i).limit();
        }
        this.setChanged();
        this.syncToClient();
    }

    public void clearBlueprint() {
        this.blueprint = null;
        this.slotDefs = List.of();
        this.limits = new int[this.items.size()];
        this.setChanged();
        this.syncToClient();
    }

    public void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    public boolean hasContentsOrBlueprint() {
        return this.hasBlueprint() || !this.isEmpty();
    }

    /** 比较器：已填数量 / 总需求数量，线性映射到 0-15。 */
    public int getComparatorOutput() {
        int required = 0;
        int filled = 0;
        for (int i = 0; i < this.items.size(); i++) {
            int limit = this.getLimit(i);
            if (limit <= 0) {
                continue;
            }
            required += limit;
            filled += Math.min(this.getItem(i).getCount(), limit);
        }
        if (required <= 0) {
            return 0;
        }
        return Math.min(15, (int) Math.floor((double) filled / (double) required * 15.0D));
    }


    /** 蓝图箱物品不能作为材料放进另一个蓝图箱，避免嵌套。 */
    private static boolean isNestedBlueprintBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlueprintBoxItem;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        int limit = this.getLimit(slot);
        BlueprintData.SlotDef def = this.getSlotDef(slot);
        return limit > 0 && def != null && !stack.isEmpty() && !isNestedBlueprintBox(stack)
                && def.matches(stack) && this.items.get(slot).getCount() < limit;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.items.size() || isNestedBlueprintBox(stack)) {
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
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    // ------------------------------------------------------------------
    // WorldlyContainer：漏斗等自动化
    // ------------------------------------------------------------------

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[this.items.size()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        int limit = this.getLimit(slot);
        BlueprintData.SlotDef def = this.getSlotDef(slot);
        // 漏斗一次只搬 1 个，这里允许“当前 + 本次”不超过上限
        return limit > 0 && def != null && !isNestedBlueprintBox(stack) && def.matches(stack)
                && this.items.get(slot).getCount() + stack.getCount() <= limit;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putIntArray(BlueprintBoxData.TAG_LIMITS, this.limits);
        if (this.blueprint != null && !this.blueprint.isEmpty()) {
            tag.put(BlueprintBoxData.TAG_BLUEPRINT, this.blueprint.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int slots = this.items.size();
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
        if (tag.contains("Items", Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
        // 旧存档里可能已经有嵌套蓝图箱，载入时清理掉，避免继续嵌套。
        for (int i = 0; i < this.items.size(); i++) {
            if (isNestedBlueprintBox(this.items.get(i))) {
                this.items.set(i, ItemStack.EMPTY);
            }
        }
        this.limits = new int[slots];
        int[] stored = tag.getIntArray(BlueprintBoxData.TAG_LIMITS);
        System.arraycopy(stored, 0, this.limits, 0, Math.min(stored.length, slots));
        if (tag.contains(BlueprintBoxData.TAG_BLUEPRINT, Tag.TAG_COMPOUND)) {
            this.blueprint = BlueprintData.load(tag.getCompound(BlueprintBoxData.TAG_BLUEPRINT), registries);
        } else {
            this.blueprint = null;
        }
        this.slotDefs = this.blueprint == null ? List.of() : this.blueprint.createSlots(slots);
        boolean noLimits = true;
        for (int limit : this.limits) {
            if (limit != 0) {
                noLimits = false;
                break;
            }
        }
        if (noLimits && this.blueprint != null) {
            for (int i = 0; i < Math.min(this.slotDefs.size(), this.limits.length); i++) {
                this.limits[i] = this.slotDefs.get(i).limit();
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

