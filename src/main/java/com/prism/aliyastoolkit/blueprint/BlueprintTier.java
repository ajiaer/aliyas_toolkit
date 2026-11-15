package com.prism.aliyastoolkit.blueprint;

/**
 * 蓝图箱等级：行数、包边颜色、防火/防爆/防意外销毁特性。
 * <p>
 * 所有等级都是 9 列；行数 = 容量/9。
 * 包边使用原版方块纹理（青金石/铜/铁/金/钻石/下界合金），木箱主体保持橡木。
 */
public enum BlueprintTier {
    LAPIS("blueprint_box", 3, 1, "minecraft:block/lapis_block", false, false, false, 2_000),
    COPPER("copper_blueprint_box", 5, 2, "minecraft:block/copper_block", true, false, false, 4_000),
    IRON("iron_blueprint_box", 7, 3, "minecraft:block/iron_block", true, false, false, 8_000),
    GOLD("gold_blueprint_box", 9, 4, "minecraft:block/gold_block", true, false, false, 16_000),
    DIAMOND("diamond_blueprint_box", 12, 5, "minecraft:block/diamond_block", true, true, false, 32_000),
    NETHERITE("netherite_blueprint_box", 16, 6, "minecraft:block/netherite_block", true, true, true, 64_000);

    private final String id;
    private final int rows;
    /** 第二栏（蓝图内容器里的物品）的格数行数。 */
    private final int contentRows;
    private final String trimTexture;
    private final boolean fireResistant;
    private final boolean explosionProof;
    private final boolean voidSafe;
    /** 流体储罐容量（mB）；0 表示该等级没有储罐。 */
    private final int tankCapacity;

    BlueprintTier(String id, int rows, int contentRows, String trimTexture, boolean fireResistant,
            boolean explosionProof, boolean voidSafe, int tankCapacity) {
        this.id = id;
        this.rows = rows;
        this.contentRows = contentRows;
        this.trimTexture = trimTexture;
        this.fireResistant = fireResistant;
        this.explosionProof = explosionProof;
        this.voidSafe = voidSafe;
        this.tankCapacity = tankCapacity;
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

    /** 第二栏（蓝图内容器里的物品）的格数。 */
    public int contentSlots() {
        return this.contentRows * 9;
    }

    public String trimTexture() {
        return this.trimTexture;
    }

    /** 铜制及以上：放在世界里的方块本体不会被火/岩浆销毁（掉落物不享受此保护）。 */
    public boolean fireResistant() {
        return this.fireResistant;
    }

    /** 钻石及以上：物品实体不会被爆炸销毁。 */
    public boolean explosionProof() {
        return this.explosionProof;
    }

    /** 下界合金：物品本身防火/防岩浆、岩浆中漂浮、掉入虚空会传送回最近安全位置。 */
    public boolean voidSafe() {
        return this.voidSafe;
    }

    /** 流体储罐容量（mB）。 */
    public int tankCapacity() {
        return this.tankCapacity;
    }

    /** 金制及以上才有流体储罐。 */
    public boolean hasTank() {
        return this.tankCapacity > 0;
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
