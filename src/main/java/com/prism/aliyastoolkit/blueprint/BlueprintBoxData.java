package com.prism.aliyastoolkit.blueprint;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 蓝图箱物品上的数据读写：物品内容（原版 {@code minecraft:container} 组件）+ 蓝图 / 格子上限
 * （原版 {@code minecraft:block_entity_data} 组件）。
 * <p>
 * 蓝图里的容器内容物也作为材料需求；匹配使用 {@link ItemStack#isSameItemSameComponents}，
 * 所以附魔、名称等数据组件不会被忽略。
 */
public final class BlueprintBoxData {
    public static final String TAG_LIMITS = "Limits";
    public static final String TAG_BLUEPRINT = "Blueprint";

    private BlueprintBoxData() {
    }

    public static CompoundTag getBlockEntityTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    public static boolean hasBlueprint(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data != null && data.contains(TAG_BLUEPRINT);
    }

    /** 蓝图是否包含方块实体/容器数据（用于 tooltip）。 */
    public static boolean hasBlockEntities(ItemStack stack) {
        CompoundTag tag = getBlockEntityTag(stack);
        if (!tag.contains(TAG_BLUEPRINT, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag blueprint = tag.getCompound(TAG_BLUEPRINT);
        return !blueprint.getList("BlockEntities", Tag.TAG_COMPOUND).isEmpty()
                || !blueprint.getList("Contents", Tag.TAG_COMPOUND).isEmpty();
    }

    public static BlueprintData readBlueprint(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = getBlockEntityTag(stack);
        if (!tag.contains(TAG_BLUEPRINT, Tag.TAG_COMPOUND)) {
            return null;
        }
        return BlueprintData.load(tag.getCompound(TAG_BLUEPRINT), registries);
    }

    /** 蓝图里的方块总数（用于 tooltip，不需要解析方块状态）。 */
    public static int blueprintBlockCount(ItemStack stack) {
        CompoundTag tag = getBlockEntityTag(stack);
        if (!tag.contains(TAG_BLUEPRINT, Tag.TAG_COMPOUND)) {
            return 0;
        }
        return tag.getCompound(TAG_BLUEPRINT).getList("Blocks", Tag.TAG_COMPOUND).size();
    }

    public static int[] readLimits(ItemStack stack, int slots) {
        int[] limits = new int[slots];
        int[] stored = getBlockEntityTag(stack).getIntArray(TAG_LIMITS);
        System.arraycopy(stored, 0, limits, 0, Math.min(stored.length, slots));
        return limits;
    }

    /** 读取格子上限；老数据没有 Limits 时按蓝图重新计算。 */
    public static int[] readOrCreateLimits(ItemStack stack, BlueprintData blueprint, int slots) {
        int[] limits = readLimits(stack, slots);
        boolean empty = true;
        for (int limit : limits) {
            if (limit != 0) {
                empty = false;
                break;
            }
        }
        if (empty && blueprint != null) {
            List<BlueprintData.SlotDef> defs = blueprint.createSlots(slots);
            for (int i = 0; i < Math.min(defs.size(), slots); i++) {
                limits[i] = defs.get(i).limit();
            }
        }
        return limits;
    }

    public static NonNullList<ItemStack> readItems(ItemStack stack, int slots) {
        NonNullList<ItemStack> items = NonNullList.withSize(slots, ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    public static void writeItems(ItemStack stack, List<ItemStack> items) {
        boolean empty = true;
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                empty = false;
                break;
            }
        }
        if (empty) {
            stack.remove(DataComponents.CONTAINER);
        } else {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
    }

    public static boolean hasItems(ItemStack stack) {
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        return contents.nonEmptyStream().findAny().isPresent();
    }

    public static boolean isEmpty(ItemStack stack) {
        return !hasItems(stack);
    }

    /** 精确匹配：物品类型 + 数据组件一致。 */
    public static boolean matches(ItemStack template, ItemStack stack) {
        return !template.isEmpty() && ItemStack.isSameItemSameComponents(template, stack);
    }

    /** 箱子里是否有足够材料（方块物品 + 容器内容物，精确匹配）。 */
    public static boolean hasEnoughItems(ItemStack stack, BlueprintData blueprint, int slots) {
        if (blueprint == null) {
            return false;
        }
        NonNullList<ItemStack> items = readItems(stack, slots);
        for (BlueprintData.Requirement requirement : blueprint.requirements()) {
            int available = 0;
            for (ItemStack item : items) {
                if (matches(requirement.template(), item)) {
                    available += item.getCount();
                }
            }
            if (available < requirement.count()) {
                return false;
            }
        }
        return true;
    }

    /** 写入蓝图 + 格子上限，不改动物品内容。 */
    public static void writeBlueprint(ItemStack stack, BlueprintData blueprint, int[] limits,
            HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(TAG_LIMITS, limits);
        if (blueprint != null && !blueprint.isEmpty()) {
            tag.put(TAG_BLUEPRINT, blueprint.save(registries));
        }
        BlockItem.setBlockEntityData(stack, BlueprintBoxes.BLUEPRINT_BOX_BE.get(), tag);
    }

    /** 一次性写入物品、蓝图和格子上限。 */
    public static void writeAll(ItemStack stack, List<ItemStack> items, int[] limits, BlueprintData blueprint,
            HolderLookup.Provider registries) {
        writeItems(stack, items);
        writeBlueprint(stack, blueprint, limits, registries);
    }

    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof BlueprintBoxItem item ? item.capacity() : 0;
    }

    public static BlueprintBoxItem asBoxItem(ItemStack stack) {
        return stack.getItem() instanceof BlueprintBoxItem item ? item : null;
    }
}

