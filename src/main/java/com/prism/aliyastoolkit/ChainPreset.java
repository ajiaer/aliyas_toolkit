package com.prism.aliyastoolkit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * 一个“大量方块连锁”预设。
 * <p>
 * 只对石头、泥土、下界岩这类大量生成的方块生效（{@link ModBlockTags#CHAIN_VERTICAL_ONLY}）：
 * <ul>
 * <li>{@code up/down/left/right}：以被破坏的方块为基准、朝前的横截面大小，每项 0-4。
 * 例如 上2 下2 左0 右1 得到的横截面是 2x5。</li>
 * <li>{@code length}：把这个横截面沿朝向前方延伸的层数，1-8（1 表示只有被挖的那一层）。</li>
 * <li>{@code lockDirection}：锁定方向。开启时平时永远沿水平朝向延伸，潜行时可额外选正斜向上/正斜向下；
 * 关闭时平时还可以朝正上/正下，潜行时朝前、正斜向、正上/正下都可选。</li>
 * <li>{@code name}：预设名称（默认“预设1”“预设2”……，可在配置界面里改）。</li>
 * </ul>
 * 本类是不可变对象，修改时用 {@code withXxx(...)} 生成新实例。
 */
public record ChainPreset(int up, int down, int left, int right, int length, boolean lockDirection, String name) {
    /** 上/下/左/右的最大值。 */
    public static final int MAX_SIDE = 4;
    /** 层数的最小值/最大值/默认值。 */
    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 8;

    /** 新预设的默认值：只挖被指向的那一格。 */
    public static final ChainPreset DEFAULT = new ChainPreset(0, 0, 0, 0, MIN_LENGTH, false,
            PresetNames.DEFAULT_PREFIX + "1");

    public static final Codec<ChainPreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("up", 0).forGetter(ChainPreset::up),
                    Codec.INT.optionalFieldOf("down", 0).forGetter(ChainPreset::down),
                    Codec.INT.optionalFieldOf("left", 0).forGetter(ChainPreset::left),
                    Codec.INT.optionalFieldOf("right", 0).forGetter(ChainPreset::right),
                    Codec.INT.optionalFieldOf("length", MIN_LENGTH).forGetter(ChainPreset::length),
                    Codec.BOOL.optionalFieldOf("lock_direction", false).forGetter(ChainPreset::lockDirection),
                    Codec.STRING.optionalFieldOf("name", "").forGetter(ChainPreset::name))
            .apply(instance, ChainPreset::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChainPreset> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.up());
                buffer.writeVarInt(value.down());
                buffer.writeVarInt(value.left());
                buffer.writeVarInt(value.right());
                buffer.writeVarInt(value.length());
                buffer.writeBoolean(value.lockDirection());
                buffer.writeUtf(value.name(), ToolkitSettings.MAX_PRESET_NAME_LENGTH);
            },
            buffer -> new ChainPreset(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readUtf(ToolkitSettings.MAX_PRESET_NAME_LENGTH)));

    public ChainPreset {
        up = Mth.clamp(up, 0, MAX_SIDE);
        down = Mth.clamp(down, 0, MAX_SIDE);
        left = Mth.clamp(left, 0, MAX_SIDE);
        right = Mth.clamp(right, 0, MAX_SIDE);
        length = Mth.clamp(length, MIN_LENGTH, MAX_LENGTH);
        name = PresetNames.sanitize(name);
    }

    /**
     * 横截面的宽（左+右+中间一格）。
     */
    public int width() {
        return this.left + this.right + 1;
    }

    /**
     * 横截面的高（上+下+中间一格）。
     */
    public int height() {
        return this.up + this.down + 1;
    }

    public int sideCount() {
        return this.up + this.down + this.left + this.right;
    }

    public ChainPreset withUp(int value) {
        return new ChainPreset(value, this.down, this.left, this.right, this.length, this.lockDirection, this.name);
    }

    public ChainPreset withDown(int value) {
        return new ChainPreset(this.up, value, this.left, this.right, this.length, this.lockDirection, this.name);
    }

    public ChainPreset withLeft(int value) {
        return new ChainPreset(this.up, this.down, value, this.right, this.length, this.lockDirection, this.name);
    }

    public ChainPreset withRight(int value) {
        return new ChainPreset(this.up, this.down, this.left, value, this.length, this.lockDirection, this.name);
    }

    public ChainPreset withLength(int value) {
        return new ChainPreset(this.up, this.down, this.left, this.right, value, this.lockDirection, this.name);
    }

    public ChainPreset withLockDirection(boolean value) {
        return new ChainPreset(this.up, this.down, this.left, this.right, this.length, value, this.name);
    }

    /** 修改预设名称（界面里“预设名称”那一行）。 */
    public ChainPreset withName(String value) {
        return new ChainPreset(this.up, this.down, this.left, this.right, this.length, this.lockDirection, value);
    }
}
