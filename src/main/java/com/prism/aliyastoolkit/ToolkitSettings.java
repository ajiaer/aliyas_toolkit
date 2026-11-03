package com.prism.aliyastoolkit;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * 玩家自己可配置的功能参数。
 * <p>
 * 通过 Data Attachment 绑定在玩家身上（见 {@link AliyasToolkit#SETTINGS}），所以：
 * <ul>
 * <li>每名玩家单独一份设置，互不影响</li>
 * <li>随玩家数据一起保存，死亡重生后保留</li>
 * <li>登录时由服务端同步给客户端，客户端界面和连锁预览都用这份数据</li>
 * </ul>
 * 本类是不可变对象：修改设置时用 {@code withXxx(...)} 生成新实例，再写回玩家数据。
 */
public record ToolkitSettings(boolean requireExactItemMatch, int chainLimit,
        List<ChainPreset> presets, int selectedPreset, boolean allowBreakingOtherBlocks) {
    /** 连锁数量最小值。 */
    public static final int MIN_CHAIN_LIMIT = 1;
    /** 连锁数量最大值。 */
    public static final int MAX_CHAIN_LIMIT = 256;
    /** 连锁数量的默认值。 */
    public static final int DEFAULT_CHAIN_LIMIT = 64;
    /** 最多可以保存多少个“大量方块”预设。 */
    public static final int MAX_PRESETS = 16;

    /** 新玩家的默认设置：物品完全一致、连锁 64 个、只有一个默认预设、允许破坏其它方块。 */
    public static final ToolkitSettings DEFAULT = new ToolkitSettings(true, DEFAULT_CHAIN_LIMIT,
            List.of(ChainPreset.DEFAULT), 0, true);

    /** 存档用的编解码器（字段都带默认值，老的玩家数据也能读）。 */
    public static final Codec<ToolkitSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("require_exact_item_match", DEFAULT.requireExactItemMatch())
                            .forGetter(ToolkitSettings::requireExactItemMatch),
                    Codec.INT.optionalFieldOf("chain_limit", DEFAULT_CHAIN_LIMIT)
                            .forGetter(ToolkitSettings::chainLimit),
                    ChainPreset.CODEC.listOf().optionalFieldOf("chain_presets", List.of(ChainPreset.DEFAULT))
                            .forGetter(ToolkitSettings::presets),
                    Codec.INT.optionalFieldOf("selected_preset", 0)
                            .forGetter(ToolkitSettings::selectedPreset),
                    Codec.BOOL.optionalFieldOf("allow_breaking_other_blocks", true)
                            .forGetter(ToolkitSettings::allowBreakingOtherBlocks))
            .apply(instance, ToolkitSettings::new));

    /** 网络同步用的编解码器。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolkitSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ToolkitSettings::requireExactItemMatch,
            ByteBufCodecs.VAR_INT, ToolkitSettings::chainLimit,
            ChainPreset.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PRESETS)), ToolkitSettings::presets,
            ByteBufCodecs.VAR_INT, ToolkitSettings::selectedPreset,
            ByteBufCodecs.BOOL, ToolkitSettings::allowBreakingOtherBlocks,
            ToolkitSettings::new);

    public ToolkitSettings {
        chainLimit = Mth.clamp(chainLimit, MIN_CHAIN_LIMIT, MAX_CHAIN_LIMIT);

        if (presets == null || presets.isEmpty()) {
            presets = List.of(ChainPreset.DEFAULT);
        } else if (presets.size() > MAX_PRESETS) {
            presets = List.copyOf(presets.subList(0, MAX_PRESETS));
        } else {
            presets = List.copyOf(presets);
        }

        selectedPreset = Mth.clamp(selectedPreset, 0, presets.size() - 1);
    }

    /** 当前选中的“大量方块”预设。 */
    public ChainPreset preset() {
        return this.presets.get(this.selectedPreset);
    }

    public ToolkitSettings withRequireExactItemMatch(boolean value) {
        return new ToolkitSettings(value, this.chainLimit, this.presets, this.selectedPreset,
                this.allowBreakingOtherBlocks);
    }

    public ToolkitSettings withChainLimit(int limit) {
        return new ToolkitSettings(this.requireExactItemMatch, limit, this.presets, this.selectedPreset,
                this.allowBreakingOtherBlocks);
    }

    /** 大量方块的连锁范围里是否也破坏“非指向”的方块。 */
    public ToolkitSettings withAllowBreakingOtherBlocks(boolean value) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets,
                this.selectedPreset, value);
    }

    /** 修改“当前预设”的内容。 */
    public ToolkitSettings withPreset(ChainPreset preset) {
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.set(this.selectedPreset, preset);
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, copy,
                this.selectedPreset, this.allowBreakingOtherBlocks);
    }

    /** 切换到指定预设（超出范围会被夹到合法值）。 */
    public ToolkitSettings withSelectedPreset(int index) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets, index,
                this.allowBreakingOtherBlocks);
    }

    /** 复制当前预设并新建一个（达到上限时不做任何事）。 */
    public ToolkitSettings withAddedPreset() {
        if (this.presets.size() >= MAX_PRESETS) {
            return this;
        }
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.add(this.preset());
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, copy,
                copy.size() - 1, this.allowBreakingOtherBlocks);
    }

    /** 删除当前预设（至少保留 1 个）。 */
    public ToolkitSettings withRemovedPreset() {
        if (this.presets.size() <= 1) {
            return this;
        }
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.remove(this.selectedPreset);
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, copy,
                Math.min(this.selectedPreset, copy.size() - 1), this.allowBreakingOtherBlocks);
    }
}
