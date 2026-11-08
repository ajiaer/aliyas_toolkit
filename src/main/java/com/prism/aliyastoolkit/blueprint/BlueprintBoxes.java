package com.prism.aliyastoolkit.blueprint;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import com.prism.aliyastoolkit.AliyasToolkit;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 蓝图箱各等级方块、物品、方块实体的注册入口。
 * <p>
 * 所有等级共用一个方块实体类型，容量由方块等级决定。
 */
public final class BlueprintBoxes {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AliyasToolkit.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AliyasToolkit.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AliyasToolkit.MODID);

    private static final Map<BlueprintTier, DeferredBlock<BlueprintBoxBlock>> BLOCK_MAP =
            new EnumMap<>(BlueprintTier.class);
    private static final Map<BlueprintTier, DeferredItem<BlueprintBoxItem>> ITEM_MAP =
            new EnumMap<>(BlueprintTier.class);

    static {
        for (BlueprintTier tier : BlueprintTier.values()) {
            BLOCK_MAP.put(tier, BLOCKS.register(tier.id(),
                    () -> new BlueprintBoxBlock(tier, BlueprintBoxBlock.boxProperties(tier))));
        }
        for (BlueprintTier tier : BlueprintTier.values()) {
            ITEM_MAP.put(tier, ITEMS.register(tier.id(), () -> {
                Item.Properties properties = new Item.Properties().stacksTo(1);
                // 只有下界合金掉落物本身防火/防岩浆；铜制及以上保护的是放在世界里的方块，不是掉落物。
                if (tier.voidSafe()) {
                    properties.fireResistant();
                }
                return new BlueprintBoxItem(BLOCK_MAP.get(tier).get(), properties, tier);
            }));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlueprintBoxBlockEntity>> BLUEPRINT_BOX_BE =
            BLOCK_ENTITIES.register("blueprint_box", () -> {
                Block[] blocks = BLOCK_MAP.values().stream().map(DeferredBlock::get).toArray(Block[]::new);
                return BlockEntityType.Builder.of(BlueprintBoxBlockEntity::new, blocks).build(null);
            });

    private BlueprintBoxes() {
    }

    public static DeferredBlock<BlueprintBoxBlock> block(BlueprintTier tier) {
        return BLOCK_MAP.get(tier);
    }

    public static DeferredItem<BlueprintBoxItem> item(BlueprintTier tier) {
        return ITEM_MAP.get(tier);
    }

    public static Collection<DeferredBlock<BlueprintBoxBlock>> blocks() {
        return BLOCK_MAP.values();
    }

    public static Collection<DeferredItem<BlueprintBoxItem>> items() {
        return ITEM_MAP.values();
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
