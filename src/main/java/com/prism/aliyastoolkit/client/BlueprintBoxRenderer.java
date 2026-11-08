package com.prism.aliyastoolkit.client;

import java.util.Locale;
import java.util.function.Function;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * 直接复用原版 {@link ChestRenderer} 的箱子模型和翻盖动画。
 * <p>
 * 每种等级使用一张单独的原版箱子贴图：木箱主体和金属锁扣不变，只把深色包边换成
 * 青金石/铜/铁/金/黑曜石/钻石/下界合金的颜色，因此不是给整个箱子染色。
 */
public class BlueprintBoxRenderer extends ChestRenderer<BlueprintBoxBlockEntity> {
    public BlueprintBoxRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected Material getMaterial(BlueprintBoxBlockEntity box, ChestType chestType) {
        String suffix = switch (chestType) {
            case LEFT -> "_left";
            case RIGHT -> "_right";
            case SINGLE -> "";
        };
        String texture = "block/blueprint_box_" + box.tier().name().toLowerCase(Locale.ROOT) + suffix;
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, texture);
        float[] shaderColor = RenderSystem.getShaderColor();
        if (shaderColor.length > 3 && shaderColor[3] < 0.99F) {
            // 虚影状态：ChestRenderer 默认使用 cutout，顶点 alpha 会失效，
            // 这里换成 translucent 材质，让蓝图箱虚影也能真正半透明。
            return new GhostMaterial(location);
        }
        return new Material(InventoryMenu.BLOCK_ATLAS, location);
    }

    private static final class GhostMaterial extends Material {
        GhostMaterial(ResourceLocation texture) {
            super(InventoryMenu.BLOCK_ATLAS, texture);
        }

        @Override
        public VertexConsumer buffer(MultiBufferSource buffer, Function<ResourceLocation, RenderType> renderTypeGetter) {
            return this.sprite().wrap(buffer.getBuffer(Sheets.translucentCullBlockSheet()));
        }
    }
}
