package com.prism.aliyastoolkit;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置（Common）。
 * <p>
 * 这里只保留“功能开关”，也就是“模组列表 -> Aliya's Toolkit -> 配置”界面里能看到的选项。
 * 各项功能的具体参数（连锁触发方式、连锁数量、补充物品是否完全一致等）不在这里，
 * 而是按玩家保存在 {@link ToolkitSettings} 里，通过游戏内的
 * “Aliya's Toolkit 配置”界面（默认 Alt + X）修改。
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

    /** 连锁破坏的总开关。 */
    public static final ModConfigSpec.BooleanValue ENABLE_CHAIN_MINING = BUILDER
            .comment(
                    "Enable chain mining.",
                    "When enabled, breaking a block while the chain key is active (default Z) also breaks the connected identical blocks.",
                    "Chain trigger mode and chain limit are configured per player in the in-game \"Aliya's Toolkit\" screen.")
            .define("enableChainMining", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}

