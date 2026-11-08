package com.prism.aliyastoolkit.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.prism.aliyastoolkit.ChainMining;
import com.prism.aliyastoolkit.CopyPaste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 从世界读取选区，生成蓝图（方块状态 + 方块实体数据 + 容器内容）。
 * <p>
 * 这个类的逻辑由客户端预检查和服务器实际记录共用，保证两边算出的材料/格子需求一致。
 */
public final class BlueprintCapture {
    /** 单个方块实体 NBT 的粗略字符上限。 */
    private static final int MAX_TAG_CHARS = 32768;
    /** 整张蓝图的方块实体 NBT 粗略字符上限。 */
    private static final int MAX_TOTAL_TAG_CHARS = 262144;

    /** 不允许复制的方块实体类型。 */
    private static final Set<BlockEntityType<?>> BLOCKED_TYPES = Set.of(
            BlockEntityType.MOB_SPAWNER,
            BlockEntityType.COMMAND_BLOCK,
            BlockEntityType.STRUCTURE_BLOCK,
            BlockEntityType.JIGSAW,
            BlockEntityType.END_GATEWAY);

    /** 允许复制的非容器方块实体（不含物品，安全）。 */
    private static final Set<BlockEntityType<?>> SAFE_NON_CONTAINER = Set.of(
            BlockEntityType.SIGN,
            BlockEntityType.HANGING_SIGN,
            BlockEntityType.BANNER,
            BlockEntityType.BED,
            BlockEntityType.SKULL,
            BlockEntityType.BEACON,
            BlockEntityType.CONDUIT,
            BlockEntityType.BELL,
            BlockEntityType.ENCHANTING_TABLE);

    public record Result(@Nullable BlueprintData blueprint, @Nullable String errorKey) {
    }

    private BlueprintCapture() {
    }

    public static Result capture(Level level, BlockPos min, List<BlockPos> offsets, @Nullable Player player) {
        if (offsets.isEmpty() || offsets.size() > CopyPaste.MAX_VOLUME) {
            return new Result(null, "message.aliyastoolkit.copy_paste_no_blocks");
        }
        List<BlueprintData.Entry> entries = new ArrayList<>();
        List<BlueprintData.BlockEntityEntry> blockEntities = new ArrayList<>();
        List<BlueprintData.ContainerContent> contents = new ArrayList<>();
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        int totalTagChars = 0;

        for (BlockPos offset : offsets) {
            if (offset.getX() < 0 || offset.getY() < 0 || offset.getZ() < 0) {
                return new Result(null, "message.aliyastoolkit.copy_paste_no_blocks");
            }
            BlockPos pos = min.offset(offset);
            if (!level.isLoaded(pos)) {
                return new Result(null, "message.aliyastoolkit.copy_paste_not_loaded");
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || ChainMining.isFluidBlock(state)) {
                continue;
            }
            entries.add(new BlueprintData.Entry(offset.getX(), offset.getY(), offset.getZ(), state));
            maxX = Math.max(maxX, offset.getX());
            maxY = Math.max(maxY, offset.getY());
            maxZ = Math.max(maxZ, offset.getZ());

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || !shouldCapture(blockEntity, player)) {
                continue;
            }
            CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
            tag.remove("id");
            tag.remove("LootTable");
            tag.remove("LootTableSeed");
            if (blockEntity instanceof Container container) {
                // 容器物品单独记录为材料成本；NBT 里不需要重复保存
                tag.remove("Items");
                tag.remove("RecordItem");
                tag.remove("Book");
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        contents.add(new BlueprintData.ContainerContent(offset.getX(), offset.getY(), offset.getZ(),
                                slot, stack.copy()));
                    }
                }
            } else if (blockEntity.getType() == BlockEntityType.CAMPFIRE) {
                // 营火不是 Container，但物品在 Items 列表里
                ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag itemTag = items.getCompound(i);
                    ItemStack stack = ItemStack.parseOptional(level.registryAccess(), itemTag);
                    if (!stack.isEmpty()) {
                        int slot = itemTag.getByte("Slot") & 255;
                        contents.add(new BlueprintData.ContainerContent(offset.getX(), offset.getY(), offset.getZ(),
                                slot, stack.copy()));
                    }
                }
                tag.remove("Items");
            } else if (blockEntity.getType() == BlockEntityType.LECTERN) {
                // 讲台的书在 Book 字段里
                ItemStack book = ItemStack.parseOptional(level.registryAccess(), tag.getCompound("Book"));
                if (!book.isEmpty()) {
                    contents.add(new BlueprintData.ContainerContent(offset.getX(), offset.getY(), offset.getZ(), 0,
                            book.copy()));
                }
                tag.remove("Book");
            }
            int tagChars = tag.toString().length();
            if (tagChars > MAX_TAG_CHARS || totalTagChars + tagChars > MAX_TOTAL_TAG_CHARS) {
                return new Result(null, "message.aliyastoolkit.blueprint.be_too_large");
            }
            totalTagChars += tagChars;
            var type = BlockEntityType.getKey(blockEntity.getType());
            if (type != null) {
                blockEntities.add(new BlueprintData.BlockEntityEntry(offset.getX(), offset.getY(), offset.getZ(),
                        type, tag));
            }
        }

        if (entries.isEmpty()) {
            return new Result(null, "message.aliyastoolkit.copy_paste_no_blocks");
        }
        return new Result(new BlueprintData(maxX + 1, maxY + 1, maxZ + 1, entries, blockEntities, contents), null);
    }

    private static boolean shouldCapture(BlockEntity blockEntity, @Nullable Player player) {
        BlockEntityType<?> type = blockEntity.getType();
        if (type == BlueprintBoxes.BLUEPRINT_BOX_BE.get() || BLOCKED_TYPES.contains(type)) {
            return false;
        }
        if (blockEntity.onlyOpCanSetNbt() && (player == null || !player.hasPermissions(2))) {
            return false;
        }
        if (blockEntity instanceof Container) {
            return true;
        }
        if (type == BlockEntityType.CAMPFIRE || type == BlockEntityType.LECTERN) {
            return true;
        }
        return SAFE_NON_CONTAINER.contains(type);
    }
}
