package com.prism.aliyastoolkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;


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
public record ToolkitSettings(boolean requireExactItemMatch, int chainLimit, List<ResourceLocation> chainBlacklist,
        int swapRow, List<ChainPreset> presets, int selectedPreset, boolean allowBreakingOtherBlocks,
        List<BuildPreset> buildPresets, int selectedBuildPreset, int quickBuildDistance, boolean collectFluids,
        boolean recordContainerContents, SwapMode swapMode, CycleDirection cycleDirection) {
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
    /** 连锁破坏黑名单最多能放多少个方块（界面里按 16×9 显示）。 */
    public static final int MAX_CHAIN_BLACKLIST = 144;
    /** 快速建造：指示器没有指向方块时的默认位置距离（格）。 */
    public static final int MIN_BUILD_DISTANCE = 2;
    public static final int MAX_BUILD_DISTANCE = 8;
    public static final int DEFAULT_BUILD_DISTANCE = 2;
    /** 黑名单的默认内容：常见的大量方块（这些方块使用“预设连锁破坏方案”）。 */
    public static final List<ResourceLocation> DEFAULT_CHAIN_BLACKLIST = List.of(
            vanilla("stone"), vanilla("granite"), vanilla("diorite"), vanilla("andesite"),
            vanilla("deepslate"), vanilla("tuff"), vanilla("calcite"), vanilla("dripstone_block"),
            vanilla("cobblestone"), vanilla("mossy_cobblestone"), vanilla("cobbled_deepslate"),
            vanilla("dirt"), vanilla("grass_block"), vanilla("podzol"), vanilla("coarse_dirt"),
            vanilla("mycelium"), vanilla("rooted_dirt"), vanilla("mud"), vanilla("clay"),
            vanilla("gravel"), vanilla("sand"), vanilla("red_sand"), vanilla("sandstone"),
            vanilla("red_sandstone"), vanilla("netherrack"), vanilla("basalt"), vanilla("smooth_basalt"),
            vanilla("blackstone"), vanilla("soul_sand"), vanilla("soul_soil"), vanilla("magma_block"),
            vanilla("end_stone"), vanilla("snow_block"));

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    /** 新玩家的默认设置：物品完全一致、连锁 64 个、交换最后一行、各一个默认预设、允许破坏其它方块、自动收集流体、记录容器内物品。 */
    public static final ToolkitSettings DEFAULT = new ToolkitSettings(true, DEFAULT_CHAIN_LIMIT,
            DEFAULT_CHAIN_BLACKLIST, DEFAULT_SWAP_ROW,
            List.of(ChainPreset.DEFAULT), 0, true, List.of(BuildPreset.DEFAULT), 0, DEFAULT_BUILD_DISTANCE,
            true, true, DEFAULT_SWAP_MODE, DEFAULT_CYCLE_DIRECTION);

    /** 存档用的编解码器（字段都带默认值，老的玩家数据也能读）。 */
    public static final Codec<ToolkitSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("require_exact_item_match", DEFAULT.requireExactItemMatch())
                            .forGetter(ToolkitSettings::requireExactItemMatch),
                    Codec.INT.optionalFieldOf("chain_limit", DEFAULT_CHAIN_LIMIT)
                            .forGetter(ToolkitSettings::chainLimit),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("chain_blacklist", DEFAULT_CHAIN_BLACKLIST)
                            .forGetter(ToolkitSettings::chainBlacklist),
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
                    Codec.INT.optionalFieldOf("quick_build_distance", DEFAULT_BUILD_DISTANCE)
                            .forGetter(ToolkitSettings::quickBuildDistance),
                    Codec.BOOL.optionalFieldOf("collect_fluids", true)
                            .forGetter(ToolkitSettings::collectFluids),
                    Codec.BOOL.optionalFieldOf("record_container_contents", true)
                            .forGetter(ToolkitSettings::recordContainerContents),
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
                buffer.writeVarInt(value.chainBlacklist().size());
                for (ResourceLocation id : value.chainBlacklist()) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, id);
                }
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
                buffer.writeVarInt(value.quickBuildDistance());
                buffer.writeBoolean(value.collectFluids());
                buffer.writeBoolean(value.recordContainerContents());
                buffer.writeBoolean(value.swapMode() == SwapMode.CYCLE);
                buffer.writeBoolean(value.cycleDirection() == CycleDirection.REVERSE);
            },
            buffer -> {
                boolean exactMatch = buffer.readBoolean();
                int chainLimit = buffer.readVarInt();
                int blacklistCount = buffer.readVarInt();
                List<ResourceLocation> chainBlacklist = new ArrayList<>(blacklistCount);
                for (int i = 0; i < blacklistCount; i++) {
                    chainBlacklist.add(ResourceLocation.STREAM_CODEC.decode(buffer));
                }
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
                int quickBuildDistance = buffer.readVarInt();
                boolean collectFluids = buffer.readBoolean();
                boolean recordContainerContents = buffer.readBoolean();
                boolean cycleMode = buffer.readBoolean();
                boolean reverseCycle = buffer.readBoolean();
                return new ToolkitSettings(exactMatch, chainLimit, chainBlacklist, swapRow, presets, selectedPreset,
                        allowOtherBlocks, buildPresets, selectedBuildPreset, quickBuildDistance, collectFluids,
                        recordContainerContents,
                        cycleMode ? SwapMode.CYCLE : SwapMode.SPECIFIC_ROW,
                        reverseCycle ? CycleDirection.REVERSE : CycleDirection.FORWARD);
            });

    public ToolkitSettings {
        chainLimit = Mth.clamp(chainLimit, MIN_CHAIN_LIMIT, MAX_CHAIN_LIMIT);
        chainBlacklist = normalizeChainBlacklist(chainBlacklist);
        quickBuildDistance = Mth.clamp(quickBuildDistance, MIN_BUILD_DISTANCE, MAX_BUILD_DISTANCE);
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

    /** 黑名单去重并限制数量（NULL / 空列表表示黑名单为空）。 */
    private static List<ResourceLocation> normalizeChainBlacklist(List<ResourceLocation> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<ResourceLocation> result = new ArrayList<>(Math.min(input.size(), MAX_CHAIN_BLACKLIST));
        for (ResourceLocation id : input) {
            if (id != null && !result.contains(id) && result.size() < MAX_CHAIN_BLACKLIST) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    /** 当前选中的“大量方块”预设。 */
    public ChainPreset preset() {
        return this.presets.get(this.selectedPreset);
    }

    /** 修改设置时保留当前物品切换模式 / 循环方向 / 记录容器内物品 / 黑名单。 */
    private ToolkitSettings copySettings(boolean requireExactItemMatch, int chainLimit, int swapRow,
            List<ChainPreset> presets, int selectedPreset, boolean allowBreakingOtherBlocks,
            List<BuildPreset> buildPresets, int selectedBuildPreset, boolean collectFluids) {
        return new ToolkitSettings(requireExactItemMatch, chainLimit, this.chainBlacklist, swapRow, presets,
                selectedPreset, allowBreakingOtherBlocks, buildPresets, selectedBuildPreset,
                this.quickBuildDistance, collectFluids, this.recordContainerContents, this.swapMode,
                this.cycleDirection);
    }

    /** 修改连锁破坏黑名单。 */
    public ToolkitSettings withChainBlacklist(List<ResourceLocation> ids) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, ids, this.swapRow,
                this.presets, this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets,
                this.selectedBuildPreset, this.quickBuildDistance, this.collectFluids,
                this.recordContainerContents, this.swapMode, this.cycleDirection);
    }

    /** 修改快速建造“默认位置”的远近（2-8 格）。 */
    public ToolkitSettings withQuickBuildDistance(int distance) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.chainBlacklist, this.swapRow,
                this.presets, this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets,
                this.selectedBuildPreset, distance, this.collectFluids, this.recordContainerContents,
                this.swapMode, this.cycleDirection);
    }

    /** 往黑名单里添加一个方块；已经在里面或已经满了就保持不变。 */
    public ToolkitSettings withAddedChainBlacklist(ResourceLocation id) {
        if (id == null || this.chainBlacklist.contains(id) || this.chainBlacklist.size() >= MAX_CHAIN_BLACKLIST) {
            return this;
        }
        List<ResourceLocation> ids = new ArrayList<>(this.chainBlacklist);
        ids.add(id);
        return this.withChainBlacklist(ids);
    }

    /** 从黑名单里移除若干个方块。 */
    public ToolkitSettings withRemovedChainBlacklist(Collection<ResourceLocation> removed) {
        if (removed == null || removed.isEmpty() || this.chainBlacklist.isEmpty()) {
            return this;
        }
        List<ResourceLocation> ids = new ArrayList<>(this.chainBlacklist);
        if (!ids.removeAll(removed)) {
            return this;
        }
        return this.withChainBlacklist(ids);
    }

    /** 这个方块是否在黑名单里（黑名单里的方块使用“预设连锁破坏方案”）。 */
    public boolean isChainBlacklisted(Block block) {
        if (block == null || this.chainBlacklist.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && this.chainBlacklist.contains(id);
    }

    /** 修改“记录容器内物品”。 */
    public ToolkitSettings withRecordContainerContents(boolean value) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.chainBlacklist, this.swapRow,
                this.presets, this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets,
                this.selectedBuildPreset, this.quickBuildDistance, this.collectFluids, value, this.swapMode,
                this.cycleDirection);
    }

    /** 修改物品切换模式。 */
    public ToolkitSettings withSwapMode(SwapMode mode) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.chainBlacklist, this.swapRow,
                this.presets, this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets,
                this.selectedBuildPreset, this.quickBuildDistance, this.collectFluids,
                this.recordContainerContents, mode, this.cycleDirection);
    }

    /** 修改循环方向。 */
    public ToolkitSettings withCycleDirection(CycleDirection direction) {
        return new ToolkitSettings(this.requireExactItemMatch, this.chainLimit, this.chainBlacklist, this.swapRow,
                this.presets, this.selectedPreset, this.allowBreakingOtherBlocks, this.buildPresets,
                this.selectedBuildPreset, this.quickBuildDistance, this.collectFluids,
                this.recordContainerContents, this.swapMode, direction);
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
