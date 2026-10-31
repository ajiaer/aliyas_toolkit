package com.prism.aliyastoolkit;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 模组自定义的方块标签。
 */
public final class ModBlockTags {
    /**
     * “大量方块”的方块（大量重复生成的实体方块，如石头、圆石、花岗岩、下界岩、泥土等）。
     * <p>
     * 对这类方块做连锁破坏时，使用玩家当前选中的 {@link ChainPreset}（横截面 上/下/左/右 + 延伸长度 +
     * 锁定方向）来计算范围，而不是普通的 3x3x3 连通搜索。
     * <p>
     * 标签内容在 {@code data/aliyastoolkit/tags/block/chain_vertical_only.json}，
     * 整合包/其它模组可以继续往里追加方块。
     */
    public static final TagKey<Block> CHAIN_VERTICAL_ONLY = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "chain_vertical_only"));

    private ModBlockTags() {
    }
}
