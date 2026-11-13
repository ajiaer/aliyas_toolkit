package com.prism.aliyastoolkit;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
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
public record ToolkitSettings(boolean requireExactItemMatch, int chainLimit, int swapRow,
        List<ChainPreset> presets, int selectedPreset, boolean allowBreakingOtherBlocks,
        List<BuildPreset> buildPresets, int selectedBuildPreset, boolean collectFluids,
        SwapMode swapMode, CycleDirection cycleDirection) {
    /** 物品切换模式：指定背包行，或让四行循环轮换。 */
    public enum SwapMode {
        SPECIFIC_ROW("gui.aliyastoolkit.swap_mode.specific_row"),
        CYCLE("gui.aliyastoolkit.swap_mode.cycle");

        private final String translationKey;

        SwapMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public static SwapMode fromName(String name) {
            for (SwapMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
            return SPECIFIC_ROW;
        }
    }

    /** 循环模式的方向。 */
    public enum CycleDirection {
        FORWARD("gui.aliyastoolkit.cycle_direction.forward"),
        REVERSE("gui.aliyastoolkit.cycle_direction.reverse");

        private final String translationKey;

        CycleDirection(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public static CycleDirection fromName(String name) {
            for (CycleDirection direction : values()) {
                if (direction.name().equals(name)) {
                    return direction;
                }
            }
            return FORWARD;
        }
    }

    /** 连锁数量最小值。 */
    public static final int MIN_CHAIN_LIMIT = 1;
    /** 连锁数量最大值。 */
    public static final int MAX_CHAIN_LIMIT = 256;
    /** 连锁数量的默认值。 */
    public static final int DEFAULT_CHAIN_LIMIT = 64;
    /** “交换行”：与快捷栏交换的背包行，1 = 最上面一行，3 = 最后一行。 */
    public static final int MIN_SWAP_ROW = 1;
    public static final int MAX_SWAP_ROW = 3;
    public static final int DEFAULT_SWAP_ROW = 3;
    /** 物品切换默认使用“指定行”，并默认使用正序循环。 */
    public static final SwapMode DEFAULT_SWAP_MODE = SwapMode.SPECIFIC_ROW;
    public static final CycleDirection DEFAULT_CYCLE_DIRECTION = CycleDirection.FORWARD;
    /** 最多可以保存多少个预设（连锁破坏、快速建造各自）。 */
    public static final int MAX_PRESETS = 16;
    /** 预设名称的最大长度。 */
    public static final int MAX_PRESET_NAME_LENGTH = PresetNames.MAX_LENGTH;
    /** 新预设的默认名称前缀：预设1、预设2…… */
    public static final String DEFAULT_PRESET_NAME_PREFIX = PresetNames.DEFAULT_PREFIX;

    /** 新玩家的默认设置：物品完全一致、连锁 64 个、交换最后一行、各一个默认预设、允许破坏其它方块、自动收集流体。 */
    public static final ToolkitSettings DEFAULT = new ToolkitSettings(true, DEFAULT_CHAIN_LIMIT, DEFAULT_SWAP_ROW,
            List.of(ChainPreset.DEFAULT), 0, true, List.of(BuildPreset.DEFAULT), 0, true,
            DEFAULT_SWAP_MODE, DEFAULT_CYCLE_DIRECTION);

    /** 存档用的编解码器（字段都带默认值，老的玩家数据也能读）。 */
    public static final Codec<ToolkitSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("require_exact_item_match", DEFAULT.requireExactItemMatch())
                            .forGetter(ToolkitSettings::requireExactItemMatch),
                    Codec.INT.optionalFieldOf("chain_limit", DEFAULT_CHAIN_LIMIT)
                            .forGetter(ToolkitSettings::chainLimit),
                    Codec.INT.optionalFieldOf("swap_row", DEFAULT_SWAP_ROW)
                            .forGetter(ToolkitSettings::swapRow),
                    ChainPreset.CODEC.listOf().optionalFieldOf("chain_presets", List.of(ChainPreset.DEFAULT))
                            .forGetter(ToolkitSettings::presets),
                    Codec.INT.optionalFieldOf("selected_preset", 0)
                            .forGetter(ToolkitSettings::selectedPreset),
                    Codec.BOOL.optionalFieldOf("allow_breaking_other_blocks", true)
                            .forGetter(ToolkitSettings::allowBreakingOtherBlocks),
                    BuildPreset.CODEC.listOf().optionalFieldOf("build_presets", List.of(BuildPreset.DEFAULT))
                            .forGetter(ToolkitSettings::buildPresets),
                    Codec.INT.optionalFieldOf("selected_build_preset", 0)
                            .forGetter(ToolkitSettings::selectedBuildPreset),
                    Codec.BOOL.optionalFieldOf("collect_fluids", true)
                            .forGetter(ToolkitSettings::collectFluids),
                    Codec.STRING.optionalFieldOf("swap_mode", DEFAULT_SWAP_MODE.name())
                            .xmap(SwapMode::fromName, SwapMode::name)
                            .forGetter(ToolkitSettings::swapMode),
                    Codec.STRING.optionalFieldOf("swap_cycle_direction", DEFAULT_CYCLE_DIRECTION.name())
                            .xmap(CycleDirection::fromName, CycleDirection::name)
                            .forGetter(ToolkitSettings::cycleDirection))
            .apply(instance, ToolkitSettings::new));

    /** 网络同步用的编解码器（字段较多，手动编解码）。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolkitSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.requireExactItemMatch());
                buffer.writeVarInt(value.chainLimit());
                buffer.writeVarInt(value.swapRow());
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
                buffer.writeBoolean(value.collectFluids());
                buffer.writeBoolean(value.swapMode() == SwapMode.CYCLE);
                buffer.writeBoolean(value.cycleDirection() == CycleDirection.REVERSE);
            },
            buffer -> {
                boolean exactMatch = buffer.readBoolean();
                int chainLimit = buffer.readVarInt();
                int swapRow = buffer.readVarInt();
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
                boolean collectFluids = buffer.readBoolean();
                boolean cycleMode = buffer.readBoolean();
                boolean reverseCycle = buffer.readBoolean();
                return new ToolkitSettings(exactMatch, chainLimit, swapRow, presets, selectedPreset,
                        allowOtherBlocks, buildPresets, selectedBuildPreset, collectFluids,
                        cycleMode ? SwapMode.CYCLE : SwapMode.SPECIFIC_ROW,
                        reverseCycle ? CycleDirection.REVERSE : CycleDirection.FORWARD);
            });

    public ToolkitSettings {
        chainLimit = Mth.clamp(chainLimit, MIN_CHAIN_LIMIT, MAX_CHAIN_LIMIT);
        swapRow = Mth.clamp(swapRow, MIN_SWAP_ROW, MAX_SWAP_ROW);
        swapMode = swapMode == null ? DEFAULT_SWAP_MODE : swapMode;
        cycleDirection = cycleDirection == null ? DEFAULT_CYCLE_DIRECTION : cycleDirection;

        presets = normalizeChainPresets(presets);
        selectedPreset = Mth.clamp(selectedPreset, 0, presets.size() - 1);

        buildPresets = normalizeBuildPresets(buildPresets);
        selectedBuildPreset = Mth.clamp(selectedBuildPreset, 0, buildPresets.size() - 1);
    }

    /** 补齐空名称（旧存档没有名字）并限制数量。 */
    private static List<ChainPreset> normalizeChainPresets(List<ChainPreset> input) {
        if (input == null || input.isEmpty()) {
            return List.of(ChainPreset.DEFAULT);
        }
        int size = Math.min(input.size(), MAX_PRESETS);
        List<ChainPreset> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ChainPreset preset = input.get(i);
            if (preset.name().isBlank()) {
                preset = preset.withName(DEFAULT_PRESET_NAME_PREFIX + (i + 1));
            }
            result.add(preset);
        }
        return List.copyOf(result);
    }

    /** 补齐空名称（旧存档没有名字）并限制数量。 */
    private static List<BuildPreset> normalizeBuildPresets(List<BuildPreset> input) {
        if (input == null || input.isEmpty()) {
            return List.of(BuildPreset.DEFAULT);
        }
        int size = Math.min(input.size(), MAX_PRESETS);
        List<BuildPreset> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BuildPreset preset = input.get(i);
            if (preset.name().isBlank()) {
                preset = preset.withName(DEFAULT_PRESET_NAME_PREFIX + (i + 1));
            }
            result.add(preset);
        }
        return List.copyOf(result);
    }

    /** 当前选中的“大量方块”预设。 */
    public ChainPreset preset() {
        return this.presets.get(this.selectedPreset);
    }

    /** 修改设置时保留当前物品切换模式 / 循环方向。 */
    private ToolkitSettings copySettings(boolean requireExactItemMatch, int chainLimit, int swapRow,
            List<ChainPreset> presets, int selectedPreset, boolean allowBreakingOtherBlocks,
            List<BuildPreset> buildPresets, int selectedBuildPreset, boolean collectFluids) {
        return new ToolkitSettings(requireExactItemMatch, chainLimit, swapRow, presets, selectedPreset,
                allowBreakingOtherBlocks, buildPresets, selectedBuildPreset, collectFluids,
                this.swapMode, this.cycleDirection);
    }

    /** 修改物品切换模式。 */
    public ToolkitSettings withSwapMode(SwapMode mode) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset,
                this.collectFluids, mode, this.cycleDirection);
    }

    /** 修改循环方向。 */
    public ToolkitSettings withCycleDirection(CycleDirection direction) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset,
                this.collectFluids, this.swapMode, direction);
    }

    public ToolkitSettings withRequireExactItemMatch(boolean value) {
        return this.copySettings(value, this.chainLimit, this.swapRow, this.presets, this.selectedPreset,
                this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset, this.collectFluids);
    }

    public ToolkitSettings withChainLimit(int limit) {
        return this.copySettings(this.requireExactItemMatch, limit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset,
                this.collectFluids);
    }

    /** “交换行”：与快捷栏交换的背包行（1-3，超出范围会被夹到合法值）。 */
    public ToolkitSettings withSwapRow(int row) {
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, row, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset,
                this.collectFluids);
    }

    /** 大量方块的连锁范围里是否也破坏“非指向”的方块。 */
    public ToolkitSettings withAllowBreakingOtherBlocks(boolean value) {
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, value, this.buildPresets, this.selectedBuildPreset, this.collectFluids);
    }

    /** 是否在制作蓝图时自动收集可用桶接取的流体源头。 */
    public ToolkitSettings withCollectFluids(boolean value) {
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset,
                value);
    }

    /** 修改“当前预设”的内容。 */
    public ToolkitSettings withPreset(ChainPreset preset) {
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.set(this.selectedPreset, preset);
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, copy,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset,
                this.collectFluids);
    }

    /** 修改“当前大量方块预设”的名称。 */
    public ToolkitSettings withPresetName(String name) {
        return this.withPreset(this.preset().withName(name));
    }

    /** 切换到指定预设（超出范围会被夹到合法值）。 */
    public ToolkitSettings withSelectedPreset(int index) {
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets, index,
                this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset, this.collectFluids);
    }

    /** 复制当前预设并新建一个（名称自动取“预设N”；达到上限时不做任何事）。 */
    public ToolkitSettings withAddedPreset() {
        if (this.presets.size() >= MAX_PRESETS) {
            return this;
        }
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.add(this.preset().withName(this.nextChainPresetName()));
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, copy,
                copy.size() - 1, this.allowBreakingOtherBlocks, this.buildPresets, this.selectedBuildPreset,
                this.collectFluids);
    }

    /** 删除当前预设（至少保留 1 个）。 */
    public ToolkitSettings withRemovedPreset() {
        if (this.presets.size() <= 1) {
            return this;
        }
        List<ChainPreset> copy = new ArrayList<>(this.presets);
        copy.remove(this.selectedPreset);
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, copy,
                Math.min(this.selectedPreset, copy.size() - 1), this.allowBreakingOtherBlocks,
                this.buildPresets, this.selectedBuildPreset, this.collectFluids);
    }

    /** 当前选中的“快速建造”预设。 */
    public BuildPreset buildPreset() {
        return this.buildPresets.get(this.selectedBuildPreset);
    }

    /** 修改“当前快速建造预设”的内容。 */
    public ToolkitSettings withBuildPreset(BuildPreset preset) {
        List<BuildPreset> copy = new ArrayList<>(this.buildPresets);
        copy.set(this.selectedBuildPreset, preset);
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, copy, this.selectedBuildPreset,
                this.collectFluids);
    }

    /** 修改“当前快速建造预设”的名称。 */
    public ToolkitSettings withBuildPresetName(String name) {
        return this.withBuildPreset(this.buildPreset().withName(name));
    }

    /** 切换到指定快速建造预设（超出范围会被夹到合法值）。 */
    public ToolkitSettings withSelectedBuildPreset(int index) {
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets, index, this.collectFluids);
    }

    /** 复制当前快速建造预设并新建一个（名称自动取“预设N”；达到上限时不做任何事）。 */
    public ToolkitSettings withAddedBuildPreset() {
        if (this.buildPresets.size() >= MAX_PRESETS) {
            return this;
        }
        List<BuildPreset> copy = new ArrayList<>(this.buildPresets);
        copy.add(this.buildPreset().withName(this.nextBuildPresetName()));
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, copy, copy.size() - 1, this.collectFluids);
    }

    /** 删除当前快速建造预设（至少保留 1 个）。 */
    public ToolkitSettings withRemovedBuildPreset() {
        if (this.buildPresets.size() <= 1) {
            return this;
        }
        List<BuildPreset> copy = new ArrayList<>(this.buildPresets);
        copy.remove(this.selectedBuildPreset);
        return this.copySettings(this.requireExactItemMatch, this.chainLimit, this.swapRow, this.presets,
                this.selectedPreset, this.allowBreakingOtherBlocks, copy,
                Math.min(this.selectedBuildPreset, copy.size() - 1), this.collectFluids);
    }

    /** 下一个未被占用的默认名称：“预设1”“预设2”…… */
    private String nextChainPresetName() {
        for (int n = 1; n <= MAX_PRESETS + 1; n++) {
            String candidate = DEFAULT_PRESET_NAME_PREFIX + n;
            boolean used = false;
            for (ChainPreset preset : this.presets) {
                if (preset.name().equals(candidate)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
        }
        return DEFAULT_PRESET_NAME_PREFIX + (this.presets.size() + 1);
    }

    /** 下一个未被占用的默认名称：“预设1”“预设2”…… */
    private String nextBuildPresetName() {
        for (int n = 1; n <= MAX_PRESETS + 1; n++) {
            String candidate = DEFAULT_PRESET_NAME_PREFIX + n;
            boolean used = false;
            for (BuildPreset preset : this.buildPresets) {
                if (preset.name().equals(candidate)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
        }
        return DEFAULT_PRESET_NAME_PREFIX + (this.buildPresets.size() + 1);
    }
}
