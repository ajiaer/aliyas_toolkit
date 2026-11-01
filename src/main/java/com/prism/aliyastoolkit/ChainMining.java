package com.prism.aliyastoolkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 连锁破坏。
 * <p>
 * 双端共用：
 * <ul>
 * <li>{@link #collectChain} 负责“从指向的方块出发，在 3x3x3 的范围内找相同的方块，再以找到的方块继续找”，
 * 客户端用它画白边预览，服务端用它决定真正要破坏哪些方块。</li>
 * <li>服务端在玩家破坏方块时（{@link BlockEvent.BreakEvent}）补破坏连锁到的方块，掉落物统一放在
 * 最开始被破坏的那个方块位置，并按“一个一个挖”的规则扣耐久、掉经验、算统计。</li>
 * </ul>
 * 只有客户端把“连锁是否激活”同步过来（{@link #setActive}）之后才会连锁，所以没按快捷键时完全不影响原版玩法。
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID)
public final class ChainMining {
    /** 已激活连锁的玩家（客户端按下/切换快捷键后同步过来）。 */
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();

    /** 正在连锁破坏中：避免自己触发自己的 BreakEvent 造成递归。 */
    private static boolean breakingChain;

    private ChainMining() {
    }

    /**
     * 客户端同步过来的“连锁是否处于激活状态”。
     */
    public static void setActive(Player player, boolean active) {
        if (active) {
            ACTIVE_PLAYERS.add(player.getUUID());
        } else {
            ACTIVE_PLAYERS.remove(player.getUUID());
        }
    }

    /** 玩家离线时清掉状态，防止残留。 */
    public static void forget(Player player) {
        ACTIVE_PLAYERS.remove(player.getUUID());
    }

    public static boolean isActive(Player player) {
        return ACTIVE_PLAYERS.contains(player.getUUID());
    }

    /**
     * 从 {@code origin} 开始收集连锁破坏的方块列表（第一个元素一定是 origin 本身）。
     * <p>
     * 石头、泥土、下界岩这类大量生成的方块（{@link ModBlockTags#CHAIN_VERTICAL_ONLY}）使用玩家当前选中的
     * {@link ChainPreset}（横截面 + 延伸长度）计算，见 {@link #collectShapedChain}；
     * 其余方块按 3x3x3 连通搜索，见 {@link #collectConnectedChain}。
     * <p>
     * 本方法不修改世界，客户端/服务端都可以调用。
     */
    public static List<BlockPos> collectChain(BlockGetter level, BlockPos origin, BlockState originState,
            ToolkitSettings settings, Player player) {
        if (originState.is(ModBlockTags.CHAIN_VERTICAL_ONLY)) {
            return collectShapedChain(level, origin, originState, settings.preset(), player,
                    settings.allowBreakingOtherBlocks());
        }
        return collectConnectedChain(level, origin, originState, settings.chainLimit());
    }

    /**
     * 普通方块的连锁：从 origin 开始，在 3x3x3 范围内找相同方块，再以找到的方块为中心继续找。
     * <p>
     * 最多 {@code maxBlocks} 个（包含 origin 本身）；超出时优先保留离 origin 近的。
     */
    public static List<BlockPos> collectConnectedChain(BlockGetter level, BlockPos origin, BlockState originState,
            int maxBlocks) {
        int limit = Mth.clamp(maxBlocks, ToolkitSettings.MIN_CHAIN_LIMIT, ToolkitSettings.MAX_CHAIN_LIMIT);
        List<BlockPos> chain = new ArrayList<>(limit);
        chain.add(origin);
        if (limit <= 1 || originState.isAir()) {
            return chain;
        }

        Block targetBlock = originState.getBlock();

        // 以 origin 为起点做“最近优先”的广度搜索
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin);
        PriorityQueue<BlockPos> frontier = new PriorityQueue<>(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        frontier.add(origin);

        while (!frontier.isEmpty() && chain.size() < limit) {
            BlockPos current = frontier.poll();
            for (int dx = -1; dx <= 1 && chain.size() < limit; dx++) {
                for (int dy = -1; dy <= 1 && chain.size() < limit; dy++) {
                    for (int dz = -1; dz <= 1 && chain.size() < limit; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        if (!visited.add(neighbor)) {
                            continue;
                        }
                        if (!isChainable(level, neighbor, targetBlock)) {
                            continue;
                        }
                        chain.add(neighbor);
                        frontier.add(neighbor);
                    }
                }
            }
        }

        return chain;
    }

    private static boolean isChainable(BlockGetter level, BlockPos pos, Block targetBlock) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() != targetBlock) {
            return false;
        }
        // 箱子之类带方块实体的方块不参与连锁，避免一次挖掉一整排箱子
        if (state.hasBlockEntity()) {
            return false;
        }
        // 基岩等不可破坏的方块（destroySpeed < 0）也跳过
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    /**
     * 形状连锁（大量方块）里允许破坏的方块。
     * <p>
     * 空气、流体（水/岩浆等，镐子处理不了）和不可破坏的方块（基岩、屏障等）永远跳过；
     * 其余方块在 {@code allowOtherBlocks} 为真时都可以破坏，为假时只允许和 origin 相同的方块。
     */
    private static boolean isShapedCandidate(BlockGetter level, BlockPos pos, Block targetBlock,
            boolean allowOtherBlocks) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return allowOtherBlocks || state.getBlock() == targetBlock;
    }

    /**
     * 大量生成方块（石头/泥土/下界岩等）的“形状连锁”。
     * <p>
     * 以被挖的方块为基准，横截面由 {@code up/down/left/right}（0-4）决定（例如 上2 下2 左0 右1 = 2x5），
     * 再沿延伸方向走 {@code length}（1-8）层：
     * <ul>
     * <li>正常情况：锁定方向开 → 水平朝前；锁定方向关 → 跟随视线，朝前/正上/正下</li>
     * <li>潜行时：跟随视线，可朝前、正斜向上、正斜向下（45°，横截面仍是“朝向前方”时的形状）；
     * 锁定方向关时还会额外允许正上/正下</li>
     * </ul>
     * 形状里只保留可破坏的方块（空气、流体、不可破坏的方块除外）；{@code allowOtherBlocks} 为真时
     * 不要求与 origin 是同种方块，否则只保留和 origin 相同的方块。形状范围是精确配置的，不受“连锁数量”限制。
     */
    public static List<BlockPos> collectShapedChain(BlockGetter level, BlockPos origin, BlockState originState,
            ChainPreset preset, Player player, boolean allowOtherBlocks) {
        return collectShapedChain(level, origin, originState, preset, player.getDirection(),
                selectShapeStep(preset, player), allowOtherBlocks);
    }

    /**
     * 根据玩家视线 / 潜行 / 锁定方向，选出大量方块的延伸方向。
     */
    public static ShapeStep selectShapeStep(ChainPreset preset, Player player) {
        return selectShapeStep(preset.lockDirection(), player.getDirection(), player.getLookAngle(),
                player.isShiftKeyDown());
    }

    /**
     * 根据视线 / 潜行 / 锁定方向，选出大量方块的延伸方向。
     *
     * @param lockDirection 预设里的“锁定方向”
     * @param facing        玩家水平朝向
     * @param look          玩家视线向量
     * @param sneaking      是否潜行
     */
    public static ShapeStep selectShapeStep(boolean lockDirection, Direction facing, Vec3 look, boolean sneaking) {
        if (!sneaking) {
            // 不潜行：锁定方向开就永远水平朝前；关则跟随视线取最近的坐标轴（前/上/下）
            if (lockDirection) {
                return ShapeStep.FORWARD;
            }
            Direction nearest = Direction.getNearest(look.x, look.y, look.z);
            if (nearest == Direction.UP) {
                return ShapeStep.UP;
            }
            if (nearest == Direction.DOWN) {
                return ShapeStep.DOWN;
            }
            return ShapeStep.FORWARD;
        }

        // 潜行：在“朝前 / 正斜上 / 正斜下”（锁定方向关时再加“正上 / 正下”）里选最接近视线的
        Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 diagonalUp = new Vec3(forward.x, 1.0D, forward.z).normalize();
        Vec3 diagonalDown = new Vec3(forward.x, -1.0D, forward.z).normalize();

        ShapeStep best = ShapeStep.FORWARD;
        double bestDot = forward.dot(look);
        double dot = diagonalUp.dot(look);
        if (dot > bestDot) {
            bestDot = dot;
            best = ShapeStep.DIAGONAL_UP;
        }
        dot = diagonalDown.dot(look);
        if (dot > bestDot) {
            bestDot = dot;
            best = ShapeStep.DIAGONAL_DOWN;
        }
        if (!lockDirection) {
            dot = Vec3.atLowerCornerOf(Direction.UP.getNormal()).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = ShapeStep.UP;
            }
            dot = Vec3.atLowerCornerOf(Direction.DOWN.getNormal()).dot(look);
            if (dot > bestDot) {
                best = ShapeStep.DOWN;
            }
        }
        return best;
    }

    /**
     * 形状连锁的实际计算（方向已经确定）。
     *
     * @param facing           玩家的水平朝向（决定横截面的左右；正斜延伸时也作为水平分量）
     * @param step             延伸方向
     * @param allowOtherBlocks 是否允许破坏与 origin 不同的方块
     */
    public static List<BlockPos> collectShapedChain(BlockGetter level, BlockPos origin, BlockState originState,
            ChainPreset preset, Direction facing, ShapeStep step, boolean allowOtherBlocks) {
        Set<BlockPos> chain = new LinkedHashSet<>();
        chain.add(origin);
        if (originState.isAir()) {
            return new ArrayList<>(chain);
        }

        Block targetBlock = originState.getBlock();
        Direction rightAxis = facing.getClockWise(Direction.Axis.Y);
        // 朝前 / 正斜延伸时横截面保持“朝向前方”的竖直面；
        // 竖直延伸时横截面必须是水平面，否则会和延伸方向重叠。
        Direction upAxis = step == ShapeStep.UP || step == ShapeStep.DOWN ? facing : Direction.UP;

        int stepX = 0;
        int stepY = 0;
        int stepZ = 0;
        switch (step) {
            case FORWARD -> {
                stepX = facing.getStepX();
                stepZ = facing.getStepZ();
            }
            case DIAGONAL_UP -> {
                stepX = facing.getStepX();
                stepY = 1;
                stepZ = facing.getStepZ();
            }
            case DIAGONAL_DOWN -> {
                stepX = facing.getStepX();
                stepY = -1;
                stepZ = facing.getStepZ();
            }
            case UP -> stepY = 1;
            case DOWN -> stepY = -1;
        }

        for (int layer = 0; layer < preset.length(); layer++) {
            BlockPos base = origin.offset(stepX * layer, stepY * layer, stepZ * layer);
            for (int upOffset = -preset.down(); upOffset <= preset.up(); upOffset++) {
                for (int rightOffset = -preset.left(); rightOffset <= preset.right(); rightOffset++) {
                    BlockPos pos = base.relative(upAxis, upOffset).relative(rightAxis, rightOffset);
                    if (pos.equals(origin) || !isShapedCandidate(level, pos, targetBlock, allowOtherBlocks)) {
                        continue;
                    }
                    chain.add(pos);
                }
            }
        }

        return new ArrayList<>(chain);
    }

    /**
     * 大量方块的延伸方向。
     */
    public enum ShapeStep {
        /** 水平朝前。 */
        FORWARD,
        /** 正斜向上（45°）。 */
        DIAGONAL_UP,
        /** 正斜向下（45°）。 */
        DIAGONAL_DOWN,
        /** 正上方（仅锁定方向为关时可选）。 */
        UP,
        /** 正下方（仅锁定方向为关时可选）。 */
        DOWN
    }


    /**
     * 判断方块中心是否在玩家的（连锁）可达范围内。
     * <p>
     * 客户端拾取是“眼睛 → 方块表面 ≤ 交互距离”，所以这里也从眼睛量到方块中心，
     * 再留 2 格余量（方块中心最多比命中点远半条对角线，约 0.87 格）。
     */
    public static boolean isWithinReach(Vec3 eyePosition, Vec3 blockCenter, double interactionRange) {
        double maxDistance = interactionRange + 2.0D;
        return eyePosition.distanceToSqr(blockCenter) <= maxDistance * maxDistance;
    }

    /**
     * 玩家破坏方块时补破坏连锁到的方块。
     * <p>
     * 原版客户端只会破坏准星指向的那一个方块，这里在服务端把它周围连锁的方块也一起破坏。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (breakingChain || event.isCanceled()) {
            return;
        }
        if (!Config.ENABLE_CHAIN_MINING.getAsBoolean()) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!isActive(player) || player.isSpectator()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos origin = event.getPos();
        BlockState originState = level.getBlockState(origin);
        if (originState.isAir()) {
            return;
        }
        // 简单校验一下距离（防止异常客户端让远处方块连锁）。
        // 必须从“眼睛”量：客户端拾取就是以眼睛为起点、到方块表面的距离不超过 blockInteractionRange()，
        // 用脚的位置会少算约 1.6 格（眼高），挖正上方且刚好到极限距离时会被误判成超距。
        if (!isWithinReach(player.getEyePosition(), Vec3.atCenterOf(origin), player.blockInteractionRange())) {
            return;
        }

        ToolkitSettings settings = player.getData(AliyasToolkit.SETTINGS);
        List<BlockPos> chain = collectChain(level, origin, originState, settings, player);
        if (chain.size() <= 1) {
            return;
        }

        breakingChain = true;
        try {
            Block originBlock = originState.getBlock();
            // 大量方块的“允许其它方块”开启时，连锁范围里与指向方块不同的方块也要破坏
            Block expectedBlock = originState.is(ModBlockTags.CHAIN_VERTICAL_ONLY)
                    && settings.allowBreakingOtherBlocks() ? null : originBlock;
            List<ItemStack> collectedDrops = new ArrayList<>();
            int experience = 0;
            for (BlockPos pos : chain) {
                // origin 本身交给原版处理（掉落物本来就落在 origin）
                if (pos.equals(origin) || !level.isLoaded(pos)) {
                    continue;
                }
                experience += breakBlock(player, level, pos, expectedBlock, collectedDrops);
            }
            // 所有连锁方块的掉落物合并成尽量少的堆，统一放在最开始破坏的那个方块位置
            spawnMergedDrops(level, origin, collectedDrops);
            if (experience > 0) {
                originBlock.popExperience(level, origin, experience);
            }
            // 指向方块本身是原版破坏的，它的掉落物在本事件之后才生成：
            // 等这一 tick 结束后再把那个位置上的所有掉落物合并到一起
            scheduleDropMerge(level, origin);
        } finally {
            breakingChain = false;
        }
    }

    /**
     * 按原版“玩家挖掉一个方块”的流程破坏单个方块。
     * <p>
     * 掉落物不直接生成世界实体，而是追加到 {@code collectedDrops}，最后统一合并后再放到
     * 最开始破坏的方块位置，避免一次连锁刷出一大堆散落的物品。
     *
     * @param expectedBlock   只允许破坏这个方块；为 {@code null} 表示任意方块都允许（大量方块的“允许其它方块”）
     * @return 这个方块掉落多少经验（创造模式/无法收获时为 0）
     */
    private static int breakBlock(ServerPlayer player, ServerLevel level, BlockPos pos, Block expectedBlock,
            List<ItemStack> collectedDrops) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || (expectedBlock != null && state.getBlock() != expectedBlock)) {
            return 0;
        }
        Block block = state.getBlock();
        if (block instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) {
            return 0;
        }

        GameType gameType = player.gameMode.getGameModeForPlayer();
        if (player.blockActionRestricted(level, pos, gameType)) {
            return 0;
        }
        // 让保护类模组也有机会阻止（事件被取消就跳过这一块）
        if (CommonHooks.fireBlockBreak(level, gameType, player, pos, state).isCanceled()) {
            return 0;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState willDestroyState = block.playerWillDestroy(level, pos, state, player);

        boolean creative = player.isCreative();
        boolean canHarvest = !creative && willDestroyState.canHarvestBlock(level, pos, player);

        ItemStack tool = player.getMainHandItem();
        ItemStack toolSnapshot = tool.copy();

        List<ItemStack> drops = creative
                ? List.of()
                : Block.getDrops(willDestroyState, level, pos, blockEntity, player, toolSnapshot);
        int experience = creative
                ? 0
                : EnchantmentHelper.processBlockExperience(level, toolSnapshot,
                        willDestroyState.getExpDrop(level, pos, blockEntity, player, toolSnapshot));

        // 和原版一样：每挖掉一个方块扣一次工具耐久
        if (!creative) {
            tool.mineBlock(level, willDestroyState, pos, player);
        }

        boolean removed = willDestroyState.onDestroyedByPlayer(level, pos, player, canHarvest,
                level.getFluidState(pos));
        if (!removed) {
            return 0;
        }
        willDestroyState.getBlock().destroy(level, pos, willDestroyState);

        if (creative) {
            return 0;
        }

        int droppedExperience = 0;
        if (canHarvest) {
            collectedDrops.addAll(drops);
            willDestroyState.spawnAfterBreak(level, pos, toolSnapshot, false);
            droppedExperience = experience;

            // 原版 playerDestroy 里的统计与消耗
            player.awardStat(Stats.BLOCK_MINED.get(block));
            player.causeFoodExhaustion(0.005F);
        }

        // 工具在连锁过程中被用坏时，补发原版的“物品损坏”事件
        if (tool.isEmpty() && !toolSnapshot.isEmpty()) {
            EventHooks.onPlayerDestroyItem(player, toolSnapshot, InteractionHand.MAIN_HAND);
        }

        return droppedExperience;
    }

    /**
     * 一条描边线段：从 {@code start} 沿 {@code axis} 正向走 1 格。
     */
    public record OutlineEdge(BlockPos start, Direction.Axis axis) {
    }

    /**
     * 计算连锁方块的白色描边线段。
     * <p>
     * 只保留“表面上的转角线”：
     * <ul>
     * <li>完全被连锁方块围住的内部棱（4 个相邻方块都是连锁方块）不画</li>
     * <li>两个表面共面、夹角 180° 的平直棱（相邻两个方块都是连锁方块）不画</li>
     * <li>只剩一个面（90° 外凸）或三个面（270° 内凹）的折角线才画</li>
     * </ul>
     * 这样描边看起来就是连锁区域外表面的轮廓，而不是每个方块都画一个方框。
     */
    public static List<OutlineEdge> collectOutlineEdges(Collection<BlockPos> chain) {
        if (chain.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> positions = new HashSet<>(chain);
        Set<OutlineEdge> edges = new LinkedHashSet<>();

        // 每个方块生成 12 条候选棱，重复的（相邻方块共享的棱）会被集合去重
        for (BlockPos pos : positions) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            for (int a = 0; a <= 1; a++) {
                for (int b = 0; b <= 1; b++) {
                    edges.add(new OutlineEdge(new BlockPos(x, y + a, z + b), Direction.Axis.X));
                    edges.add(new OutlineEdge(new BlockPos(x + a, y, z + b), Direction.Axis.Y));
                    edges.add(new OutlineEdge(new BlockPos(x + a, y + b, z), Direction.Axis.Z));
                }
            }
        }

        List<OutlineEdge> result = new ArrayList<>();
        for (OutlineEdge edge : edges) {
            if (isCornerEdge(positions, edge)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * 判断一条棱是不是“转角”棱（90° 或 270°），而不是平直的 180° 棱或内部棱。
     */
    private static boolean isCornerEdge(Set<BlockPos> positions, OutlineEdge edge) {
        BlockPos start = edge.start();
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();
        boolean[] solid = new boolean[4];
        switch (edge.axis()) {
            case X -> {
                solid[0] = positions.contains(new BlockPos(x, y - 1, z - 1));
                solid[1] = positions.contains(new BlockPos(x, y - 1, z));
                solid[2] = positions.contains(new BlockPos(x, y, z - 1));
                solid[3] = positions.contains(new BlockPos(x, y, z));
            }
            case Y -> {
                solid[0] = positions.contains(new BlockPos(x - 1, y, z - 1));
                solid[1] = positions.contains(new BlockPos(x - 1, y, z));
                solid[2] = positions.contains(new BlockPos(x, y, z - 1));
                solid[3] = positions.contains(new BlockPos(x, y, z));
            }
            case Z -> {
                solid[0] = positions.contains(new BlockPos(x - 1, y - 1, z));
                solid[1] = positions.contains(new BlockPos(x - 1, y, z));
                solid[2] = positions.contains(new BlockPos(x, y - 1, z));
                solid[3] = positions.contains(new BlockPos(x, y, z));
            }
        }

        int count = 0;
        for (boolean value : solid) {
            if (value) {
                count++;
            }
        }
        if (count == 1 || count == 3) {
            return true;
        }
        // 两个方块只在棱上对角接触时是两条 90° 折角，仍然画出来；
        // 两个方块共面（在 2x2 里相邻）时是 180° 平直棱，不画。
        return count == 2 && ((solid[0] && solid[3]) || (solid[1] && solid[2]));
    }

    /**
     * 把合并后的掉落物放到指定方块位置。
     */
    private static void spawnMergedDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        for (ItemStack stack : mergeStacks(drops)) {
            Block.popResource(level, pos, stack);
        }
    }

    /**
     * 在本次 tick 结束后合并 origin 位置上的掉落物。
     * <p>
     * 指向方块本身由原版破坏，它的掉落物在本事件之后才生成，所以这里延后一点统一合并，
     * 避免出现“单独一个掉落物 + 连锁合并后的一大堆”分开放着的情况。
     */
    private static void scheduleDropMerge(ServerLevel level, BlockPos origin) {
        level.getServer().execute(() -> mergeDroppedItems(level, origin));
    }

    /**
     * 把指定方块位置上的掉落物合并成尽量少的堆（单堆上限遵循物品自身的最大堆叠数量），
     * 再重新生成在同一个位置。
     */
    static void mergeDroppedItems(ServerLevel level, BlockPos pos) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.75D),
                entity -> entity.isAlive()
                        && !entity.getItem().isEmpty()
                        && entity.blockPosition().equals(pos));
        if (items.size() < 2) {
            return;
        }

        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (ItemEntity entity : items) {
            stacks.add(entity.getItem().copy());
            entity.discard();
        }
        for (ItemStack stack : mergeStacks(stacks)) {
            Block.popResource(level, pos, stack);
        }
    }

    /**
     * 合并物品堆：相同物品 + 相同数据组件的堆会尽量合并，单堆上限遵循物品自身的最大堆叠数量
     * （例如石头 64、鸡蛋 16、工具 1）。
     * <p>
     * 不会修改传入的列表，也不会修改传入的 {@link ItemStack}。
     */
    public static List<ItemStack> mergeStacks(List<ItemStack> drops) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }
            ItemStack remaining = drop.copy();
            for (ItemStack existing : merged) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (!ItemStack.isSameItemSameComponents(existing, remaining)) {
                    continue;
                }
                int max = Math.min(existing.getMaxStackSize(), remaining.getMaxStackSize());
                int space = max - existing.getCount();
                if (space <= 0) {
                    continue;
                }
                int moved = Math.min(space, remaining.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
            }
            while (!remaining.isEmpty()) {
                int max = Math.max(1, remaining.getMaxStackSize());
                ItemStack piece = remaining.copyWithCount(Math.min(remaining.getCount(), max));
                merged.add(piece);
                remaining.shrink(piece.getCount());
            }
        }
        return merged;
    }
}
