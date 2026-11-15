package com.prism.aliyastoolkit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * 流体显示颜色：取流体静止贴图的平均色，再乘上流体自己的染色值。
 * <p>
 * 这样水和岩浆这类贴图本身带色的流体会得到接近实际的颜色，
 * 而染色型流体（例如水）也会乘上对应染色，和游戏内看到的一致。
 */
public final class FluidTint {
    private FluidTint() {
    }

    public static int color(Fluid fluid) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        int tint = extensions.getTintColor();
        float tintRed = ((tint >> 16) & 0xFF) / 255.0F;
        float tintGreen = ((tint >> 8) & 0xFF) / 255.0F;
        float tintBlue = (tint & 0xFF) / 255.0F;

        TextureAtlasSprite sprite = sprite(extensions.getStillTexture());
        if (sprite == null) {
            return 0xFF000000 | (tint & 0xFFFFFF);
        }
        int width = Math.max(1, sprite.contents().width());
        int height = Math.max(1, sprite.contents().height());
        int stepX = Math.max(1, width / 8);
        int stepY = Math.max(1, height / 8);
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long count = 0L;
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = sprite.getPixelRGBA(0, x, y);
                if (((pixel >>> 24) & 0xFF) < 8) {
                    continue;
                }
                red += (long) ((pixel & 0xFF) * tintRed);
                green += (long) (((pixel >> 8) & 0xFF) * tintGreen);
                blue += (long) (((pixel >> 16) & 0xFF) * tintBlue);
                count++;
            }
        }
        if (count == 0L) {
            return 0xFF000000 | (tint & 0xFFFFFF);
        }
        int r = (int) Math.min(255L, red / count);
        int g = (int) Math.min(255L, green / count);
        int b = (int) Math.min(255L, blue / count);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        if (texture == null) {
            return null;
        }
        var atlas = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        return atlas == null ? null : atlas.apply(texture);
    }
}
