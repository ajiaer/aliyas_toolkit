package com.prism.photbar;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 白名单数据，保存在世界存档的 {@code data/photbar_whitelist.dat} 里。
 * <p>
 * 白名单是服务端权威数据：界面里修改后由服务端保存，再通过容器槽位同步给客户端显示。
 */
public class WhitelistData extends SavedData {
    /** 白名单格子数量（3x3）。 */
    public static final int SIZE = 9;
    private static final String DATA_NAME = "photbar_whitelist";
    private static final String TAG_ITEMS = "Items";

    private final NonNullList<ItemStack> stacks = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public static WhitelistData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(WhitelistData::new, WhitelistData::load), DATA_NAME);
    }

    private static WhitelistData load(CompoundTag tag, HolderLookup.Provider registries) {
        WhitelistData data = new WhitelistData();
        ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), SIZE); i++) {
            data.stacks.set(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : this.stacks) {
            list.add(stack.isEmpty() ? new CompoundTag() : stack.save(registries, new CompoundTag()));
        }
        tag.put(TAG_ITEMS, list);
        return tag;
    }

    /** 读取某一格（返回的是内部实例，调用方不要修改它）。 */
    public ItemStack getSlot(int index) {
        return index >= 0 && index < SIZE ? this.stacks.get(index) : ItemStack.EMPTY;
    }

    /**
     * 设置某一格。白名单只记录“物品本身”，所以数量统一存 1。
     */
    public void setSlot(int index, ItemStack stack) {
        if (index < 0 || index >= SIZE) {
            return;
        }
        this.stacks.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        this.setDirty();
    }
}
