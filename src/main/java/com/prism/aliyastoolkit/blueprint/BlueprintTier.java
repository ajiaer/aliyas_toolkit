package com.prism.aliyastoolkit.blueprint;

/**
 * 蓝图箱等级：行数、包边颜色、防火/防爆/防意外销毁特性。
 * <p>
 * 所有等级都是 9 列；行数 = 容量/9。
 * 包边使用原版方块纹理（青金石/铜/铁/金/黑曜石/钻石/下界合金），木箱主体保持橡木。
 */
public enum BlueprintTier {
    LAPIS("blueprint_box", 3, "minecraft:block/lapis_block", false, false, false),
    COPPER("copper_blueprint_box", 5, "minecraft:block/copper_block", true, false, false),
    IRON("iron_blueprint_box", 7, "minecraft:block/iron_block", true, false, false),
    GOLD("gold_blueprint_box", 9, "minecraft:block/gold_block", true, false, false),
    OBSIDIAN("obsidian_blueprint_box", 12, "minecraft:block/crying_obsidian", true, true, false),
    DIAMOND("diamond_blueprint_box", 15, "minecraft:block/diamond_block", true, true, false),
    NETHERITE("netherite_blueprint_box", 18, "minecraft:block/netherite_block", true, true, true);

    private final String id;
    private final int rows;
    private final String trimTexture;
    private final boolean fireResistant;
    private final boolean explosionProof;
    private final boolean voidSafe;

    BlueprintTier(String id, int rows, String trimTexture, boolean fireResistant,
            boolean explosionProof, boolean voidSafe) {
        this.id = id;
        this.rows = rows;
        this.trimTexture = trimTexture;
        this.fireResistant = fireResistant;
        this.explosionProof = explosionProof;
        this.voidSafe = voidSafe;
    }

    public String id() {
        return this.id;
    }

    public int rows() {
        return this.rows;
    }

    public int slots() {
        return this.rows * 9;
    }

    public String trimTexture() {
        return this.trimTexture;
    }

    /** 铜制及以上：放在世界里的方块本体不会被火/岩浆销毁（掉落物不享受此保护）。 */
    public boolean fireResistant() {
        return this.fireResistant;
    }

    /** 黑曜石及以上：物品实体不会被爆炸销毁。 */
    public boolean explosionProof() {
        return this.explosionProof;
    }

    /** 下界合金：物品本身防火/防岩浆、岩浆中漂浮、掉入虚空会传送回最近安全位置。 */
    public boolean voidSafe() {
        return this.voidSafe;
    }

    public String translationKey() {
        return "block.aliyastoolkit." + this.id;
    }

    public static BlueprintTier byId(String id) {
        for (BlueprintTier tier : values()) {
            if (tier.id.equals(id)) {
                return tier;
            }
        }
        return LAPIS;
    }
}
