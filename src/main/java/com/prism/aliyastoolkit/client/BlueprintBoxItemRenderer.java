package com.prism.aliyastoolkit.client;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlockEntity;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxes;
import com.prism.aliyastoolkit.blueprint.BlueprintTier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 物品栏、掉落物里也使用和世界中相同的箱子模型与等级贴图。
 * <p>
 * 自己实现 {@link BlockEntityWithoutLevelRenderer}，把对应等级的假方块实体交给
 * 已经注册的 {@link BlueprintBoxRenderer} 渲染，避免物品模型和实际方块外观不一致。
 */
@OnlyIn(Dist.CLIENT)
public class BlueprintBoxItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final Map<BlueprintTier, BlueprintBoxBlockEntity> boxes = new EnumMap<>(BlueprintTier.class);

    public BlueprintBoxItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
        for (BlueprintTier tier : BlueprintTier.values()) {
            BlockState state = BlueprintBoxes.block(tier).get().defaultBlockState();
            this.boxes.put(tier, new BlueprintBoxBlockEntity(
                    BlueprintBoxes.BLUEPRINT_BOX_BE.get(), BlockPos.ZERO, state));
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!(stack.getItem() instanceof BlueprintBoxItem item)) {
            return;
        }
        BlueprintBoxBlockEntity box = this.boxes.get(item.tier());
        if (box != null) {
            // 假方块实体没有 level，ChestRenderer 会使用单箱默认朝向，正好适合物品展示。
            Minecraft.getInstance().getBlockEntityRenderDispatcher()
                    .renderItem(box, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }
}
