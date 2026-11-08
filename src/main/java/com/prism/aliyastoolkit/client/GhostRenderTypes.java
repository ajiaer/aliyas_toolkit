package com.prism.aliyastoolkit.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;

/**
 * 虚影渲染用的 RenderType 映射。
 * <p>
 * 箱子、潜影盒、床、旗帜等的 BEWLR 使用各自的图集 + cutout/solid，
 * 这些类型不支持 alpha 混合；虚影需要换成同图集的 translucent 类型。
 */
final class GhostRenderTypes {
    private static final Map<RenderType, RenderType> TRANSLUCENT = new HashMap<>();

    static {
        put(RenderType.entityCutout(Sheets.CHEST_SHEET),
                RenderType.entityTranslucentCull(Sheets.CHEST_SHEET));
        put(RenderType.entityCutoutNoCull(Sheets.SHULKER_SHEET),
                RenderType.entityTranslucent(Sheets.SHULKER_SHEET));
        put(RenderType.entitySolid(Sheets.BED_SHEET),
                RenderType.entityTranslucentCull(Sheets.BED_SHEET));
        put(RenderType.entitySolid(Sheets.BANNER_SHEET),
                RenderType.entityTranslucentCull(Sheets.BANNER_SHEET));
        put(RenderType.entityNoOutline(Sheets.BANNER_SHEET),
                RenderType.entityTranslucent(Sheets.BANNER_SHEET));
        put(RenderType.entityNoOutline(Sheets.SHIELD_SHEET),
                RenderType.entityTranslucent(Sheets.SHIELD_SHEET));
        put(RenderType.entityCutoutNoCull(Sheets.SIGN_SHEET),
                RenderType.entityTranslucent(Sheets.SIGN_SHEET));
        put(RenderType.entityCutoutNoCull(Sheets.DECORATED_POT_SHEET),
                RenderType.entityTranslucent(Sheets.DECORATED_POT_SHEET));
    }

    private GhostRenderTypes() {
    }

    private static void put(RenderType opaque, RenderType translucent) {
        TRANSLUCENT.put(opaque, translucent);
    }

    static RenderType translucent(RenderType renderType) {
        return TRANSLUCENT.getOrDefault(renderType, renderType);
    }
}
