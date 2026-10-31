package com.prism.aliyastoolkit;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置。所有选项都会显示在“模组列表 -> Aliya's Toolkit -> 配置”界面里。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 快捷键“快捷栏与背包最后一行交换”的开关。 */
    public static final ModConfigSpec.BooleanValue ENABLE_HOTBAR_SWAP = BUILDER
            .comment("Enable the hotbar swap key. When enabled, pressing the key swaps the hotbar with the last inventory row.")
            .define("enableHotbarSwap", true);

    /** 快捷栏物品消失后自动从背包补充的开关。 */
    public static final ModConfigSpec.BooleanValue ENABLE_AUTO_REFILL = BUILDER
            .comment("Enable automatic refill. When a whitelisted item disappears from a hotbar slot (dropped, used up, tool broken, ...), the matching item is moved from the inventory back into that slot.")
            .define("enableAutoRefill", true);

    /** 自动补充时是否要求物品“完全一样”。 */
    public static final ModConfigSpec.BooleanValue REQUIRE_EXACT_ITEM_MATCH = BUILDER
            .comment(
                    "true  - Items only count as the same when they are identical, including enchantments, custom names and every other data component.",
                    "false - Items count as the same when they are the same item type, ignoring enchantments, custom names and other data components.")
            .define("requireExactItemMatch", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}

