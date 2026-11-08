package com.prism.aliyastoolkit.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一份蓝图：方块状态 + 方块实体数据 + 容器内容。
 * <p>
 * 方块状态用原版的 {@code Name + Properties} NBT 保存。
 * 容器/方块实体数据用 {@link net.minecraft.world.level.block.entity.BlockEntity#saveWithoutMetadata}
 * 保存；其中容器里的物品会额外记录“原容器位置 + 原槽位 + 精确 ItemStack”，
 * 作为建造材料成本，并在建造时放回原槽位。
 * <p>
 * 蓝图箱的格子按“精确 ItemStack（含数据组件）”分配；同一种物品数量超过堆叠上限时占用多个格子。
 */
public final class BlueprintData {
    /** 一个方块：相对蓝图最小角的坐标 + 状态。 */
    public record Entry(int x, int y, int z, BlockState state) {
    }

    /** 一个方块实体：坐标 + 类型 + 过滤后的 NBT（不含物品数据）。 */
    public record BlockEntityEntry(int x, int y, int z, ResourceLocation type, CompoundTag tag) {
    }

    /** 容器里的一个物品：所在方块相对坐标 + 原槽位 + 精确物品。 */
    public record ContainerContent(int x, int y, int z, int slot, ItemStack stack) {
    }

    /** 一项材料需求：精确模板 + 总数量。 */
    public record Requirement(ItemStack template, int count) {
    }

    /** 蓝图箱里的一格占位。 */
    public record SlotDef(ItemStack template, int limit) {
        public boolean matches(ItemStack stack) {
            return !this.template.isEmpty() && ItemStack.isSameItemSameComponents(this.template, stack);
        }

        public ItemStack displayStack() {
            return this.template.copyWithCount(this.limit);
        }
    }

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<Entry> entries;
    private final List<BlockEntityEntry> blockEntities;
    private final List<ContainerContent> contents;

    public BlueprintData(int sizeX, int sizeY, int sizeZ, List<Entry> entries,
            List<BlockEntityEntry> blockEntities, List<ContainerContent> contents) {
        this.sizeX = Math.max(0, sizeX);
        this.sizeY = Math.max(0, sizeY);
        this.sizeZ = Math.max(0, sizeZ);
        this.entries = List.copyOf(entries);
        this.blockEntities = List.copyOf(blockEntities);
        this.contents = List.copyOf(contents);
    }

    public BlueprintData(int sizeX, int sizeY, int sizeZ, List<Entry> entries) {
        this(sizeX, sizeY, sizeZ, entries, List.of(), List.of());
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public List<BlockEntityEntry> blockEntities() {
        return this.blockEntities;
    }

    public List<ContainerContent> contents() {
        return this.contents;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public int blockCount() {
        return this.entries.size();
    }

    public boolean hasBlockEntities() {
        return !this.blockEntities.isEmpty();
    }

    public boolean hasContainerContents() {
        return !this.contents.isEmpty();
    }

    /** 预览用的相对坐标（最小角为原点）。 */
    public List<BlockPos> previewPositions() {
        List<BlockPos> positions = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            positions.add(new BlockPos(entry.x(), entry.y(), entry.z()));
        }
        return positions;
    }

    /** 与 {@link #previewPositions()} 一一对应的方块状态。 */
    public List<BlockState> previewStates() {
        List<BlockState> states = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            states.add(entry.state());
        }
        return states;
    }

    /**
     * 需要的全部材料：各方块自身的物品 + 容器内容物。
     * <p>
     * 相同精确 ItemStack 会合并；没有对应物品的方块（火、水等）不消耗物品。
     */
    public List<Requirement> requirements() {
        List<Requirement> list = new ArrayList<>();
        for (Entry entry : this.entries) {
            Item item = entry.state().getBlock().asItem();
            if (item != Items.AIR) {
                addRequirement(list, new ItemStack(item));
            }
        }
        for (ContainerContent content : this.contents) {
            addRequirement(list, content.stack());
        }
        list.sort(Comparator.comparing(BlueprintData::requirementSortKey));
        return list;
    }

    private static void addRequirement(List<Requirement> list, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack template = stack.copyWithCount(1);
        for (int i = 0; i < list.size(); i++) {
            Requirement existing = list.get(i);
            if (ItemStack.isSameItemSameComponents(existing.template(), template)) {
                list.set(i, new Requirement(existing.template(), existing.count() + stack.getCount()));
                return;
            }
        }
        list.add(new Requirement(template, stack.getCount()));
    }

    private static String requirementSortKey(Requirement requirement) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(requirement.template().getItem());
        return (id == null ? requirement.template().getItem().toString() : id.toString())
                + "|" + requirement.template().getComponents();
    }

    /** 按物品和堆叠上限把蓝图分配成蓝图箱格子。 */
    public List<SlotDef> createSlots(int capacity) {
        return createSlots(this.requirements(), capacity);
    }

    public static List<SlotDef> createSlots(List<Requirement> requirements, int capacity) {
        if (capacity <= 0 || requirements.isEmpty()) {
            return List.of();
        }
        List<SlotDef> slots = new ArrayList<>();
        for (Requirement requirement : requirements) {
            int remaining = Math.max(0, requirement.count());
            int maxStack = Math.max(1, requirement.template().getMaxStackSize());
            while (remaining > 0) {
                int limit = Math.min(remaining, maxStack);
                slots.add(new SlotDef(requirement.template(), limit));
                remaining -= limit;
                if (slots.size() > capacity) {
                    // 已经放不下，调用方只关心是否超过容量
                    return slots;
                }
            }
        }
        return slots;
    }

    /** 某个方块坐标对应的所有容器内容。 */
    public List<ContainerContent> contentsAt(int x, int y, int z) {
        List<ContainerContent> result = new ArrayList<>();
        for (ContainerContent content : this.contents) {
            if (content.x() == x && content.y() == y && content.z() == z) {
                result.add(content);
            }
        }
        return result;
    }


    /** 保存到 NBT（蓝图箱方块实体 / 蓝图箱物品共用）。 */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SizeX", this.sizeX);
        tag.putInt("SizeY", this.sizeY);
        tag.putInt("SizeZ", this.sizeZ);

        ListTag blocks = new ListTag();
        for (Entry entry : this.entries) {
            CompoundTag blockTag = NbtUtils.writeBlockState(entry.state());
            blockTag.putInt("X", entry.x());
            blockTag.putInt("Y", entry.y());
            blockTag.putInt("Z", entry.z());
            blocks.add(blockTag);
        }
        tag.put("Blocks", blocks);

        ListTag blockEntities = new ListTag();
        for (BlockEntityEntry entry : this.blockEntities) {
            CompoundTag beTag = new CompoundTag();
            beTag.putInt("X", entry.x());
            beTag.putInt("Y", entry.y());
            beTag.putInt("Z", entry.z());
            beTag.putString("Type", entry.type().toString());
            beTag.put("Data", entry.tag().copy());
            blockEntities.add(beTag);
        }
        tag.put("BlockEntities", blockEntities);

        ListTag contents = new ListTag();
        for (ContainerContent content : this.contents) {
            CompoundTag contentTag = new CompoundTag();
            contentTag.putInt("X", content.x());
            contentTag.putInt("Y", content.y());
            contentTag.putInt("Z", content.z());
            contentTag.putInt("Slot", content.slot());
            contentTag.put("Item", content.stack().save(registries));
            contents.add(contentTag);
        }
        tag.put("Contents", contents);
        return tag;
    }

    /** 从 NBT 读取蓝图。 */
    public static BlueprintData load(CompoundTag tag, HolderLookup.Provider registries) {
        int sizeX = tag.getInt("SizeX");
        int sizeY = tag.getInt("SizeY");
        int sizeZ = tag.getInt("SizeZ");

        List<Entry> entries = new ArrayList<>();
        ListTag blocks = tag.getList("Blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockTag = blocks.getCompound(i);
            BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), blockTag);
            if (state.isAir()) {
                continue;
            }
            entries.add(new Entry(blockTag.getInt("X"), blockTag.getInt("Y"), blockTag.getInt("Z"), state));
        }

        List<BlockEntityEntry> blockEntities = new ArrayList<>();
        ListTag beList = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < beList.size(); i++) {
            CompoundTag beTag = beList.getCompound(i);
            ResourceLocation type = ResourceLocation.tryParse(beTag.getString("Type"));
            if (type == null) {
                continue;
            }
            blockEntities.add(new BlockEntityEntry(beTag.getInt("X"), beTag.getInt("Y"), beTag.getInt("Z"),
                    type, beTag.getCompound("Data").copy()));
        }

        List<ContainerContent> contents = new ArrayList<>();
        ListTag contentList = tag.getList("Contents", Tag.TAG_COMPOUND);
        for (int i = 0; i < contentList.size(); i++) {
            CompoundTag contentTag = contentList.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, contentTag.getCompound("Item"));
            if (stack.isEmpty()) {
                continue;
            }
            contents.add(new ContainerContent(contentTag.getInt("X"), contentTag.getInt("Y"),
                    contentTag.getInt("Z"), contentTag.getInt("Slot"), stack));
        }
        return new BlueprintData(sizeX, sizeY, sizeZ, entries, blockEntities, contents);
    }

    /**
     * 计算旋转后目标位置：以蓝图水平中心为轴心，{@code target} 是中心底部的方块位置。
     * 从上往下看顺时针 90° 时，水平偏移 (x, z) 变成 (-z, x)。
     */
    public BlockPos destination(int x, int y, int z, BlockPos target, int rotation) {
        double offsetX = x + 0.5D - this.sizeX / 2.0D;
        double offsetZ = z + 0.5D - this.sizeZ / 2.0D;
        for (int i = 0; i < rotation; i++) {
            double rotated = offsetX;
            offsetX = -offsetZ;
            offsetZ = rotated;
        }
        return new BlockPos(
                target.getX() + (int) Math.round(offsetX),
                target.getY() + y,
                target.getZ() + (int) Math.round(offsetZ));
    }
}

