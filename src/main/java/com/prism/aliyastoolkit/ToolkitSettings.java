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
        List<ChainPreset> presets, int selectedPreset, boolean allowBreakingOtherBlocks,
        List<BuildPreset> buildPresets, int selectedBuildPreset) {
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
            List.of(ChainPreset.DEFAULT), 0, true, List.of(BuildPreset.DEFAULT), 0);

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
                            .forGetter(ToolkitSettings::allowBreakingOtherBlocks),
                    BuildPreset.CODEC.listOf().optionalFieldOf("build_presets", List.of(BuildPreset.DEFAULT))
                            .forGetter(ToolkitSettings::buildPresets),
                    Codec.INT.optionalFieldOf("selected_build_preset", 0)
                            .forGetter(ToolkitSettings::selectedBuildPreset))
            .apply(instance, ToolkitSettings::new));

    /** 网络同步用的编解码器（字段较多，手动编解码）。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolkitSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.requireExactItemMatch());
                buffer.writeVarInt(value.chainLimit());
                buffer.writeVarInt(value.presets().size());
                for (ChainPreset preset : value.presets()) {
                    ChainPreset.STREAM_CODEC.encode(buffer, preset);
                }
                buffer.writeVarInt(value.selectedPreset());
                buffer.writeBoolean(value.allowBreakingOtherBlocks());
                buffer.writeVarInt(value.buildPresets().size());
                for (BuildPreset preset : value.buildPresets()) {
                    BuildPreset.STREAM_CODEC.encode(buffer, preset);
                }
                buffer.writeVarInt(value.selectedBuildPreset());
            },
            buffer -> {
                boolean exactMatch = buffer.readBoolean();
                int chainLimit = buffer.readVarInt();
                int presetCount = buffer.readVarInt();
                List<ChainPreset> presets = new ArrayList<>(presetCount);
                for (int i = 0; i < presetCount; i++) {
                    presets.add(ChainPreset.STREAM_CODEC.decode(buffer));
                }
                int selectedPreset = buffer.readVarInt();
                boolean allowOtherBlocks = buffer.readBoolean();
                int buildPresetCount = buffer.readVarInt();
                List<BuildPreset> buildPresets = new ArrayList<>(buildPresetCount);
                for (int i = 0; i < buildPresetCount; i++) {
                    buildPresets.add(BuildPreset.STREAM_CODEC.decode(buffer));
                }
                int selectedBuildPreset = buffer.readVarInt();
                return new ToolkitSettings(exactMatch, chainLimit, presets, selectedPreset, allowOtherBlocks,
                        buildPresets, selectedBuildPreset);
            });

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

        if (buildPresets == null || buildPresets.isEmpty()) {
            buildPresets = List.of(BuildPreset.DEFAULT);
        } else if (buildPresets.size() > MAX_PRESETS) {
            buildPresets = List.copyOf(buildPresets.subList(0, MAX_PRESETS));
        } else {
            buildPresets = List.copyOf(buildPresets);
        }

        selectedBuildPreset = Mth.clamp(selectedBuildPreset, 0, buildPresets.size() - 1);
    }

    /** 当前选中的“大量方块”预设。 */
    public ChainPreset preset() {
        return this.presets.get(this.selectedPreset);
    }

    public ToolkitSettings withRequireExactItemMatch(boolean value) {
        return new ToolkitSettings(value, this.chainLimit, this.presets, this.selectedPreset,
                this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset);
    }

    public ToolkitSettings withChainLimit(int limit) {
        return new ToolkitSettings(this.requireExactItemMatch, limit, this.presets, this.selectedPreset,
                this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset);
    }

    /** 大量方块的连锁范围里是否也破坏“非指向”的方块。 */
    public ToolkitSettings withAllowBreakingOtherBlocks(boolean value) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets,
                this.selectedPreset, value, this.buildPresets, this.selectedBuildPreset);
    }

    /** 修改“当前预设”的内容。 */
    public ToolkitSettings withPreset(ChainPreset preset) {
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.set(this.selectedPreset, preset);
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, copy,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset);
    }

    /** 切换到指定预设（超出范围会被夹到合法值）。 */
    public ToolkitSettings withSelectedPreset(int index) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets, index,
                this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset);
    }

    /** 复制当前预设并新建一个（达到上限时不做任何事）。 */
    public ToolkitSettings withAddedPreset() {
        if (this.presets.size() >= MAX_PRESETS) {
            return this;
        }
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.add(this.preset());
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, copy,
                copy.size() - 1, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset);
    }

    /** 删除当前预设（至少保留 1 个）。 */
    public ToolkitSettings withRemovedPreset() {
        if (this.presets.size() <= 1) {
            return this;
        }
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.remove(this.selectedPreset);
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, copy,
                Math.min(this.selectedPreset, copy.size() - 1), this.allowBreakingOtherBlocks,
                this.buildPresets, this.selectedBuildPreset);
    }

    /** 当前选中的“快速建造”预设。 */
    public BuildPreset buildPreset() {
        return this.buildPresets.get(this.selectedBuildPreset);
    }

    /** 修改“当前快速建造预设”的内容。 */
    public ToolkitSettings withBuildPreset(BuildPreset preset) {
        List<BuildPreset> copy = new ArrayList<>(this.buildPresets);
        copy.set(this.selectedBuildPreset, preset);
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, copy, this.selectedBuildPreset);
    }

    /** 切换到指定快速建造预设（超出范围会被夹到合法值）。 */
    public ToolkitSettings withSelectedBuildPreset(int index) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, index);
    }

    /** 复制当前快速建造预设并新建一个（达到上限时不做任何事）。 */
    public ToolkitSettings withAddedBuildPreset() {
        if (this.buildPresets.size() >= MAX_PRESETS) {
            return this;
        }
        List<BuildPreset> copy = new ArrayList<>(this.buildPresets);
        copy.add(this.buildPreset());
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, copy, copy.size() - 1);
    }

    /** 删除当前快速建造预设（至少保留 1 个）。 */
    public ToolkitSettings withRemovedBuildPreset() {
        if (this.buildPresets.size() <= 1) {
            return this;
        }
        List<BuildPreset> copy = new ArrayList<>(this.buildPresets);
        copy.remove(this.selectedBuildPreset);
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, copy,
                Math.min(this.selectedBuildPreset, copy.size() - 1));
    }
}
