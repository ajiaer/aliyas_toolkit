package com.prism.aliyastoolkit.blueprint;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * 蓝图箱物品：不可堆叠，不再使用附魔炫彩，等级通过箱子渲染器的包边贴图区分。
 * <p>
 * 铜制及以上保护的是放在世界里的方块本体；掉落物只有下界合金防火/防岩浆。
 * 爆炸/虚空保护由物品实体行为处理。铁砧改名、幽灵占位、容器内容与蓝图数据保持原版组件方式。
 */
public class BlueprintBoxItem extends BlockItem {
    private final BlueprintTier tier;

    public BlueprintBoxItem(Block block, Item.Properties properties, BlueprintTier tier) {
        super(block, properties);
        this.tier = tier;
    }

    public BlueprintTier tier() {
        return this.tier;
    }

    public int capacity() {
        return this.tier.slots();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // 只保留主要信息：是否已经装入建造材料。
        // 不写操作方式，也不提示是否记录过蓝图。
        if (BlueprintBoxData.hasItems(stack)) {
            tooltip.add(Component.translatable("tooltip.aliyastoolkit.blueprint_box.has_items"));
        }
    }
}
