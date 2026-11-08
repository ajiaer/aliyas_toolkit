package com.prism.aliyastoolkit.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.ChainMining;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.CopyPaste;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxData;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.blueprint.BlueprintCapture;
import com.prism.aliyastoolkit.blueprint.BlueprintData;
import com.prism.aliyastoolkit.blueprint.BlueprintRotation;
import com.prism.aliyastoolkit.network.BlueprintBuildPayload;
import com.prism.aliyastoolkit.network.BlueprintCreatePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * 客户端“蓝图建造”：
 * <ul>
 * <li>V：进入/退出选区模式。手里不能拿蓝图箱，右键多次划定区域，实时显示范围。</li>
 * <li>选区完成后，换成空蓝图箱（没有物品；可以已有蓝图），右键选区把结构收进箱子并退出。容量不够时提示换更高级的箱子且不退出。</li>
 * <li>手持带蓝图的蓝图箱，Ctrl+右键进入虚影预览；滚轮调整与玩家的距离，Tab 绕 Y 轴旋转。</li>
 * <li>右键固定虚影，左键取消固定；固定后再次右键确认建造。</li>
 * </ul>
 * 旧的 Ctrl+C / Ctrl+X / Ctrl+V 快捷键已移除。
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public final class CopyPasteClient {
    /** 选区轮廓：直接可见的黄色。 */
    private static final float SELECTION_RED = 1.0F;
    private static final float SELECTION_GREEN = 0.86F;
    private static final float SELECTION_BLUE = 0.20F;
    /** 选区轮廓：被方块挡住部分的暗黄色。 */
    private static final float SELECTION_BEHIND_RED = 0.45F;
    private static final float SELECTION_BEHIND_GREEN = 0.36F;
    private static final float SELECTION_BEHIND_BLUE = 0.06F;
    /** 正在动态预览的选区：亮黄色。 */
    private static final float PENDING_SELECTION_RED = 1.0F;
    private static final float PENDING_SELECTION_GREEN = 1.0F;
    private static final float PENDING_SELECTION_BLUE = 0.18F;
    private static final float PENDING_SELECTION_BEHIND_RED = 0.55F;
    private static final float PENDING_SELECTION_BEHIND_GREEN = 0.55F;
    private static final float PENDING_SELECTION_BEHIND_BLUE = 0.08F;
    /** 预览轮廓：蓝色。 */
    private static final float PREVIEW_RED = 0.35F;
    private static final float PREVIEW_GREEN = 0.65F;
    private static final float PREVIEW_BLUE = 1.0F;
    private static final float PREVIEW_BEHIND_RED = 0.10F;
    private static final float PREVIEW_BEHIND_GREEN = 0.24F;
    private static final float PREVIEW_BEHIND_BLUE = 0.50F;
    /** 预览无效（材料不足 / 范围过大 / 超出距离）。 */
    private static final float INVALID_RED = 1.0F;
    private static final float INVALID_GREEN = 0.35F;
    private static final float INVALID_BLUE = 0.30F;
    private static final float INVALID_BEHIND_RED = 0.50F;
    private static final float INVALID_BEHIND_GREEN = 0.12F;
    private static final float INVALID_BEHIND_BLUE = 0.10F;

    /** 单次最多渲染的虚影方块数。 */
    private static final int GHOST_RENDER_LIMIT = 4096;

    /** 虚影距离限制（玩家眼睛到中心底部）。 */
    private static final double MIN_GHOST_DISTANCE = 2.0D;
    private static final double MAX_GHOST_DISTANCE = 20.0D;
    private static final double DEFAULT_GHOST_DISTANCE = 8.0D;
    /** 自动避开玩家后额外增加的距离。 */
    private static final double SAFE_DISTANCE_PADDING = 2.0D;

    private static final RenderType OUTLINE_BEHIND = createLineType(
            "aliyastoolkit_copy_paste_outline_behind", RenderStateShard.NO_DEPTH_TEST, 2.0D);
    private static final RenderType OUTLINE_FRONT = createLineType(
            "aliyastoolkit_copy_paste_outline_front", RenderStateShard.LEQUAL_DEPTH_TEST, 2.0D);

    /** 低透明度方块虚影；颜色由 ColorModulator 控制。 */
    private static final RenderType GHOST_BLOCK = RenderType.create(
            "aliyastoolkit_copy_paste_ghost",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static Object lastLevel;

    /** 已经划定的矩形区域。 */
    private static final List<Region> REGIONS = new ArrayList<>();
    /** 选区里所有坐标（包含空气，便于右键点击判定）。 */
    private static List<BlockPos> selectionPositions = List.of();
    private static List<ChainMining.OutlineEdge> selectionOutline = List.of();
    /** 正在等待第二个点的选区起点。 */
    private static BlockPos pendingPoint;

    private enum Mode {
        NONE,
        SELECTING,
        PLACING
    }

    private static Mode mode = Mode.NONE;

    /** 当前预览的蓝图（相对坐标、原始状态）。 */
    private static List<BlockPos> sourcePositions = List.of();
    private static List<BlockState> sourceStates = List.of();
    private static BlueprintData activeBlueprint;
    /** 进入预览时主手上的蓝图箱快照；切换物品/蓝图后自动退出，避免虚影与实际建造内容不一致。 */
    private static ItemStack previewBox = ItemStack.EMPTY;
    private static int rotation;
    private static double centerX;
    private static double centerZ;
    private static int minY;

    /** 固定后的中心底部位置；未固定时为 null。 */
    private static BlockPos fixedTarget;
    private static BlockPos previewTarget;
    private static List<BlockPos> previewPositions = List.of();
    /** 与 previewPositions 一一对应，已经应用 rotation 的状态。 */
    private static List<BlockState> previewStates = List.of();
    private static List<ChainMining.OutlineEdge> previewOutline = List.of();
    private static boolean previewInvalid;
    private static double placementDistance = DEFAULT_GHOST_DISTANCE;
    /** 以当前指示器为中心，在半径 4 的球内寻找最近的可放置位置。 */
    private static final int PLACEMENT_SEARCH_RADIUS = 4;
    private static final List<BlockPos> PLACEMENT_SEARCH_OFFSETS = buildPlacementSearchOffsets();
    private static int noSpaceMessageCooldown;
    /** 没有合法位置时缓存 10 tick，避免每 tick 对大型蓝图重复做完整搜索。 */
    private static BlockPos lastNoSpaceTarget;
    private static int lastNoSpaceRotation = -1;
    private static int noSpaceSearchCooldown;

    private CopyPasteClient() {
    }

    private static List<BlockPos> buildPlacementSearchOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -PLACEMENT_SEARCH_RADIUS; x <= PLACEMENT_SEARCH_RADIUS; x++) {
            for (int y = -PLACEMENT_SEARCH_RADIUS; y <= PLACEMENT_SEARCH_RADIUS; y++) {
                for (int z = -PLACEMENT_SEARCH_RADIUS; z <= PLACEMENT_SEARCH_RADIUS; z++) {
                    if (x * x + y * y + z * z <= PLACEMENT_SEARCH_RADIUS * PLACEMENT_SEARCH_RADIUS) {
                        offsets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        offsets.sort(Comparator.comparingInt(pos -> pos.getX() * pos.getX()
                + pos.getY() * pos.getY() + pos.getZ() * pos.getZ()));
        return List.copyOf(offsets);
    }

    private static RenderType createLineType(String name, RenderStateShard.DepthTestStateShard depthTest,
            double lineWidth) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                1536,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(lineWidth)))
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(depthTest)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    /** 一个矩形选区。 */
    private record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Region(BlockPos first, BlockPos second) {
            this(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()), Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        }

        long volume() {
            return (long) (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1)
                    * (this.maxZ - this.minZ + 1);
        }

        boolean contains(BlockPos pos) {
            return pos.getX() >= this.minX && pos.getX() <= this.maxX
                    && pos.getY() >= this.minY && pos.getY() <= this.maxY
                    && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
        }

        void collectInto(Set<BlockPos> out) {
            for (int x = this.minX; x <= this.maxX; x++) {
                for (int y = this.minY; y <= this.maxY; y++) {
                    for (int z = this.minZ; z <= this.maxZ; z++) {
                        out.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        List<BlockPos> collectPositions() {
            List<BlockPos> positions = new ArrayList<>((int) Math.min(this.volume(), 4096L));
            for (int x = this.minX; x <= this.maxX; x++) {
                for (int y = this.minY; y <= this.maxY; y++) {
                    for (int z = this.minZ; z <= this.maxZ; z++) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
            return positions;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.getConnection() == null) {
            lastLevel = null;
            forceDisable();
            return;
        }
        // 换世界 / 退出重进时清空旧选区
        if (lastLevel != minecraft.level) {
            if (lastLevel != null) {
                forceDisable();
            }
            lastLevel = minecraft.level;
        }
        if (!Config.ENABLE_COPY_PASTE.getAsBoolean() || !player.isAlive() || player.isDeadOrDying()
                || player.isSpectator()) {
            forceDisable();
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        if (mode == Mode.SELECTING) {
            updateSelectionPreview(minecraft);
        } else if (mode == Mode.PLACING) {
            updatePlacementPreview(minecraft);
        }
    }

    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        KeyMapping toggle = AliyasToolkitClient.COPY_PASTE_KEY;
        if (toggle.matches(event.getKey(), event.getScanCode()) && toggle.isDown()) {
            toggleFeature(minecraft);
            return;
        }
        if (mode == Mode.PLACING && event.getKey() == GLFW.GLFW_KEY_TAB) {
            rotate(minecraft);
        }
    }

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        KeyMapping toggle = AliyasToolkitClient.COPY_PASTE_KEY;
        if (toggle.matchesMouse(event.getButton()) && toggle.isDown()) {
            toggleFeature(minecraft);
            return;
        }

        if (mode == Mode.PLACING) {
            if (event.getButton() == 1) {
                event.setCanceled(true);
                handlePlacementRightClick(minecraft);
            } else if (event.getButton() == 0) {
                event.setCanceled(true);
                handlePlacementLeftClick(minecraft);
            }
            return;
        }
        if (mode == Mode.SELECTING) {
            if (event.getButton() == 1) {
                event.setCanceled(true);
                handleSelectionRightClick(minecraft);
            } else if (event.getButton() == 0) {
                event.setCanceled(true);
                handleSelectionLeftClick(minecraft);
            }
            return;
        }

        // 未进入选区模式时：手持蓝图箱 + Ctrl + 右键，直接进入虚影预览
        if (event.getButton() == 1 && isControlDown(minecraft)
                && minecraft.player.getMainHandItem().getItem() instanceof BlueprintBoxItem) {
            event.setCanceled(true);
            startPlacement(minecraft);
        }
    }

    @SubscribeEvent
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (mode != Mode.PLACING) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        // 预览期间滚轮只调整距离，不切换快捷栏物品
        event.setCanceled(true);
        if (fixedTarget != null) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        // 反转滚轮方向：滚轮向前 = 拉远，向后 = 拉近（与用户习惯一致）
        placementDistance = clamp(placementDistance + delta * 0.5D,
                MIN_GHOST_DISTANCE, MAX_GHOST_DISTANCE);
        updatePlacementPreview(minecraft);
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (mode == Mode.NONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (event.isAttack() || event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }

    private static void toggleFeature(Minecraft minecraft) {
        if (mode != Mode.NONE) {
            forceDisable();
            message("message.aliyastoolkit.copy_paste_off");
            return;
        }
        if (!Config.ENABLE_COPY_PASTE.getAsBoolean() || minecraft.player == null) {
            return;
        }
        resetAll();
        mode = Mode.SELECTING;
        ExclusiveModes.activate(ExclusiveModes.COPY_PASTE);
        message("message.aliyastoolkit.copy_paste_on");
    }

    /** 被其它互斥模式关闭，或玩家退出世界时调用。 */
    public static void forceDisable() {
        resetAll();
        ExclusiveModes.deactivate(ExclusiveModes.COPY_PASTE);
    }

    private static void resetAll() {
        REGIONS.clear();
        selectionPositions = List.of();
        selectionOutline = List.of();
        pendingPoint = null;
        mode = Mode.NONE;
        sourcePositions = List.of();
        sourceStates = List.of();
        activeBlueprint = null;
        previewBox = ItemStack.EMPTY;
        rotation = 0;
        centerX = 0.0D;
        centerZ = 0.0D;
        minY = 0;
        fixedTarget = null;
        previewTarget = null;
        previewPositions = List.of();
        previewStates = List.of();
        previewOutline = List.of();
        previewInvalid = false;
        placementDistance = DEFAULT_GHOST_DISTANCE;
        noSpaceMessageCooldown = 0;
        lastNoSpaceTarget = null;
        lastNoSpaceRotation = -1;
        noSpaceSearchCooldown = 0;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastLevel = null;
        forceDisable();
    }

    private static boolean isControlDown(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    // ---------------------------------------------------------------------
    // 选区
    // ---------------------------------------------------------------------

    private static void handleSelectionRightClick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof BlueprintBoxItem boxItem) {
            storeSelectionIntoBox(minecraft, held, boxItem);
            return;
        }

        BlockPos target = pickTarget(minecraft);
        if (target == null) {
            message("message.aliyastoolkit.copy_paste_no_target");
            return;
        }
        if (pendingPoint == null) {
            pendingPoint = target;
            updateSelectionPreview(minecraft);
            message("message.aliyastoolkit.copy_paste_first_point");
            return;
        }
        if (addRegion(pendingPoint, target)) {
            pendingPoint = null;
        }
        updateSelectionPreview(minecraft);
    }

    private static void handleSelectionLeftClick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (pendingPoint != null) {
            pendingPoint = null;
            updateSelectionPreview(minecraft);
            message("message.aliyastoolkit.copy_paste_pending_cancelled");
            return;
        }
        BlockPos target = pickTarget(minecraft);
        if (target == null) {
            return;
        }
        for (int i = 0; i < REGIONS.size(); i++) {
            if (REGIONS.get(i).contains(target)) {
                REGIONS.remove(i);
                rebuildSelection();
                updateSelectionPreview(minecraft);
                message("message.aliyastoolkit.copy_paste_regions_removed", 1);
                return;
            }
        }
    }

    private static boolean addRegion(BlockPos first, BlockPos second) {
        Region region = new Region(first, second);
        if (region.volume() > CopyPaste.MAX_VOLUME) {
            message("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME);
            return false;
        }
        Set<BlockPos> union = new LinkedHashSet<>();
        for (Region existing : REGIONS) {
            existing.collectInto(union);
        }
        region.collectInto(union);
        if (union.size() > CopyPaste.MAX_VOLUME) {
            message("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME);
            return false;
        }
        REGIONS.add(region);
        rebuildSelection();
        message("message.aliyastoolkit.copy_paste_region_added", union.size());
        return true;
    }

    private static void rebuildSelection() {
        Set<BlockPos> union = new LinkedHashSet<>();
        for (Region region : REGIONS) {
            region.collectInto(union);
        }
        selectionPositions = List.copyOf(union);
        selectionOutline = union.isEmpty() ? List.of() : ChainMining.collectOutlineEdges(union);
    }

    /**
     * 把当前选区收进主手的蓝图箱。
     * 箱子必须没有物品；可以已经存有蓝图（会被新蓝图覆盖）。
     * 容量不够时只提示，不退出选区模式。
     */
    private static void storeSelectionIntoBox(Minecraft minecraft, ItemStack held, BlueprintBoxItem boxItem) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (selectionPositions.isEmpty()) {
            message("message.aliyastoolkit.copy_paste_no_region");
            return;
        }
        if (!BlueprintBoxData.isEmpty(held)) {
            message("message.aliyastoolkit.blueprint.not_empty");
            return;
        }
        BlockPos target = pickTarget(minecraft);
        if (target == null || !selectionPositions.contains(target)) {
            message("message.aliyastoolkit.blueprint.click_selection");
            return;
        }

        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : selectionPositions) {
            BlockState state = minecraft.level.getBlockState(pos);
            if (!state.isAir()) {
                positions.add(pos);
            }
        }
        if (positions.isEmpty()) {
            message("message.aliyastoolkit.copy_paste_no_blocks");
            return;
        }

        BlueprintCreatePayload payload = BlueprintCreatePayload.of(true, positions);
        BlueprintCapture.Result result = BlueprintCapture.capture(minecraft.level, payload.min(),
                payload.offsets(), player);
        if (result.errorKey() != null || result.blueprint() == null) {
            message(result.errorKey() == null
                    ? "message.aliyastoolkit.copy_paste_no_blocks" : result.errorKey());
            return;
        }
        int capacity = boxItem.capacity();
        if (result.blueprint().createSlots(capacity).size() > capacity) {
            message("message.aliyastoolkit.blueprint.too_many_advanced");
            return;
        }
        if (minecraft.getConnection() == null || !NetworkRegistry.hasChannel(
                minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY,
                BlueprintCreatePayload.TYPE.id())) {
            message("message.aliyastoolkit.copy_paste_unavailable");
            return;
        }

        PacketDistributor.sendToServer(payload);
        // 服务端会再次校验并真正写入；成功后客户端退出蓝图模式
        forceDisable();
    }

    // ---------------------------------------------------------------------
    // 蓝图虚影预览 / 建造
    // ---------------------------------------------------------------------

    private static boolean startPlacement(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !Config.ENABLE_COPY_PASTE.getAsBoolean()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlueprintBoxItem boxItem)) {
            message("message.aliyastoolkit.blueprint.need_box");
            return false;
        }
        BlueprintData blueprint = BlueprintBoxData.readBlueprint(held, minecraft.level.registryAccess());
        if (blueprint == null || blueprint.isEmpty()) {
            message("message.aliyastoolkit.blueprint.no_blueprint");
            return false;
        }
        if (blueprint.entries().size() > CopyPaste.MAX_VOLUME) {
            message("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME);
            return false;
        }
        if (!BlueprintBoxData.hasEnoughItems(held, blueprint, boxItem.capacity())) {
            message("message.aliyastoolkit.blueprint.not_enough");
            return false;
        }

        mode = Mode.PLACING;
        activeBlueprint = blueprint;
        previewBox = held.copy();
        sourcePositions = blueprint.previewPositions();
        sourceStates = blueprint.previewStates();
        centerX = blueprint.sizeX() / 2.0D;
        centerZ = blueprint.sizeZ() / 2.0D;
        minY = 0;
        rotation = 0;
        placementDistance = DEFAULT_GHOST_DISTANCE;
        fixedTarget = null;
        previewTarget = null;
        pendingPoint = null;
        REGIONS.clear();
        selectionPositions = List.of();
        selectionOutline = List.of();
        ExclusiveModes.activate(ExclusiveModes.COPY_PASTE);
        updatePlacementPreview(minecraft);
        message("message.aliyastoolkit.blueprint.preview");
        return true;
    }

    private static void handlePlacementRightClick(Minecraft minecraft) {
        if (fixedTarget == null) {
            if (previewTarget == null || previewPositions.isEmpty()) {
                message("message.aliyastoolkit.copy_paste_no_target");
                return;
            }
            fixedTarget = previewTarget.immutable();
            updatePlacementPreview(minecraft);
            message("message.aliyastoolkit.blueprint.pinned");
            return;
        }
        commitPlacement(minecraft);
    }

    private static void handlePlacementLeftClick(Minecraft minecraft) {
        if (fixedTarget != null) {
            fixedTarget = null;
            updatePlacementPreview(minecraft);
            message("message.aliyastoolkit.blueprint.unpinned");
        }
    }

    private static void rotate(Minecraft minecraft) {
        if (mode != Mode.PLACING) {
            return;
        }
        if (fixedTarget != null) {
            message("message.aliyastoolkit.copy_paste_rotate_locked");
            return;
        }
        rotation = (rotation + 1) % CopyPaste.ROTATIONS;
        updatePlacementPreview(minecraft);
        message("message.aliyastoolkit.copy_paste_rotated", rotation * 90);
    }

    private static void updatePlacementPreview(Minecraft minecraft) {
        if (mode != Mode.PLACING) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || sourcePositions.isEmpty()) {
            clearPreview();
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!ItemStack.isSameItemSameComponents(previewBox, held)) {
            message("message.aliyastoolkit.blueprint.box_changed");
            forceDisable();
            return;
        }
        if (noSpaceMessageCooldown > 0) {
            noSpaceMessageCooldown--;
        }
        if (fixedTarget == null) {
            ensureSafeDistance(minecraft);
        }
        if (noSpaceSearchCooldown > 0) {
            noSpaceSearchCooldown--;
        }
        // 指示器位置可能在地里/墙里：在半径 4 的球内找最近的、所有目标方块都可替换的位置。
        BlockPos rawTarget = fixedTarget != null ? fixedTarget : anchor(player, placementDistance);
        BlockPos target;
        if (fixedTarget != null) {
            target = rawTarget;
        } else if (rawTarget.equals(lastNoSpaceTarget) && rotation == lastNoSpaceRotation
                && noSpaceSearchCooldown > 0) {
            target = null;
        } else {
            target = findPlacementTarget(player, rawTarget);
            if (target == null) {
                lastNoSpaceTarget = rawTarget;
                lastNoSpaceRotation = rotation;
                noSpaceSearchCooldown = 10;
            } else {
                lastNoSpaceTarget = null;
                lastNoSpaceRotation = -1;
                noSpaceSearchCooldown = 0;
            }
        }
        previewTarget = target;
        if (target == null) {
            clearPreview();
            previewInvalid = true;
            if (noSpaceMessageCooldown <= 0) {
                message("message.aliyastoolkit.blueprint.no_space", PLACEMENT_SEARCH_RADIUS);
                noSpaceMessageCooldown = 40;
            }
            return;
        }

        previewPositions = computeDestinations(target);
        previewStates = rotatedSourceStates();
        previewOutline = ChainMining.collectOutlineEdges(previewPositions);
        previewInvalid = !(held.getItem() instanceof BlueprintBoxItem boxItem)
                || activeBlueprint == null
                || !BlueprintBoxData.hasEnoughItems(held, activeBlueprint, boxItem.capacity());
    }

    /**
     * 初始距离 8 格；如果结构 AABB 会碰到玩家，就逐步推远到刚好碰不到，再额外加 2 格。
     * 玩家用滚轮主动靠近时也会再次执行，避免虚影穿进玩家身体。
     */
    private static void ensureSafeDistance(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        AABB playerBox = player.getBoundingBox().inflate(0.05D);
        double distance = clamp(placementDistance, MIN_GHOST_DISTANCE, MAX_GHOST_DISTANCE);
        List<BlockPos> positions = computeDestinations(anchor(player, distance));
        AABB bounds = boundsOf(positions);
        if (bounds == null || !bounds.intersects(playerBox)) {
            placementDistance = distance;
            return;
        }

        while (distance < MAX_GHOST_DISTANCE) {
            distance += 0.5D;
            positions = computeDestinations(anchor(player, distance));
            bounds = boundsOf(positions);
            if (bounds == null || !bounds.intersects(playerBox)) {
                break;
            }
        }
        placementDistance = Math.min(MAX_GHOST_DISTANCE, distance + SAFE_DISTANCE_PADDING);
    }

    /** 在指示器周围的球内按距离从近到远寻找第一个合法位置。 */
    private static BlockPos findPlacementTarget(LocalPlayer player, BlockPos rawTarget) {
        if (rawTarget == null) {
            return null;
        }
        AABB playerBox = player.getBoundingBox().inflate(0.05D);
        for (BlockPos offset : PLACEMENT_SEARCH_OFFSETS) {
            BlockPos candidate = rawTarget.offset(offset);
            if (canPlaceBlueprintAt(player.level(), playerBox, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 所有蓝图目标方块都必须可替换，且整体不能碰到玩家。 */
    private static boolean canPlaceBlueprintAt(net.minecraft.world.level.Level level, AABB playerBox,
            BlockPos target) {
        List<BlockPos> destinations = computeDestinations(target);
        if (destinations.isEmpty()) {
            return false;
        }
        AABB bounds = boundsOf(destinations);
        if (bounds != null && bounds.intersects(playerBox)) {
            return false;
        }
        if (!level.isLoaded(target) || !level.getBlockState(target).canBeReplaced()) {
            return false;
        }
        for (BlockPos pos : destinations) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos anchor(LocalPlayer player, double distance) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        return BlockPos.containing(eye.add(look.scale(distance)));
    }

    private static AABB boundsOf(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static List<BlockState> rotatedSourceStates() {
        List<BlockState> states = new ArrayList<>(sourceStates.size());
        for (BlockState state : sourceStates) {
            states.add(BlueprintRotation.rotate(state, rotation));
        }
        return states;
    }

    private static void clearPreview() {
        previewTarget = null;
        previewPositions = List.of();
        previewStates = List.of();
        previewOutline = List.of();
        previewInvalid = false;
    }

    private static void commitPlacement(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || fixedTarget == null) {
            message("message.aliyastoolkit.copy_paste_need_fixed");
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!ItemStack.isSameItemSameComponents(previewBox, held)) {
            message("message.aliyastoolkit.blueprint.box_changed");
            forceDisable();
            return;
        }
        if (!(held.getItem() instanceof BlueprintBoxItem boxItem)) {
            message("message.aliyastoolkit.blueprint.need_box");
            return;
        }
        BlueprintData blueprint = BlueprintBoxData.readBlueprint(held, minecraft.level.registryAccess());
        if (blueprint == null || blueprint.isEmpty()) {
            message("message.aliyastoolkit.blueprint.no_blueprint");
            return;
        }
        if (!BlueprintBoxData.hasEnoughItems(held, blueprint, boxItem.capacity())) {
            message("message.aliyastoolkit.blueprint.not_enough");
            return;
        }

        List<BlockPos> destinations = computeDestinations(fixedTarget);
        if (destinations.isEmpty()) {
            message("message.aliyastoolkit.copy_paste_no_blocks");
            return;
        }
        Vec3 eye = player.getEyePosition();
        double maxDistance = CopyPaste.MAX_DISTANCE;
        for (BlockPos pos : destinations) {
            if (eye.distanceToSqr(Vec3.atCenterOf(pos)) > maxDistance * maxDistance) {
                message("message.aliyastoolkit.copy_paste_too_far");
                return;
            }
        }
        if (minecraft.getConnection() == null || !NetworkRegistry.hasChannel(
                minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY,
                BlueprintBuildPayload.TYPE.id())) {
            message("message.aliyastoolkit.copy_paste_unavailable");
            return;
        }

        PacketDistributor.sendToServer(new BlueprintBuildPayload(fixedTarget, rotation));
        forceDisable();
    }

    /**
     * 计算虚影/建造位置：以蓝图水平中心为轴心，{@code target} 是中心底部的方块位置。
     * 从上往下看顺时针 90° 时，水平偏移 (x, z) 变成 (-z, x)。
     */
    private static List<BlockPos> computeDestinations(BlockPos target) {
        List<BlockPos> result = new ArrayList<>(sourcePositions.size());
        for (BlockPos source : sourcePositions) {
            double offsetX = source.getX() + 0.5D - centerX;
            double offsetZ = source.getZ() + 0.5D - centerZ;
            for (int i = 0; i < rotation; i++) {
                double rotated = offsetX;
                offsetX = -offsetZ;
                offsetZ = rotated;
            }
            result.add(new BlockPos(
                    target.getX() + (int) Math.round(offsetX),
                    target.getY() + (source.getY() - minY),
                    target.getZ() + (int) Math.round(offsetZ)));
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // 选区实时预览
    // ---------------------------------------------------------------------

    private static void updateSelectionPreview(Minecraft minecraft) {
        if (mode != Mode.SELECTING || minecraft.level == null) {
            return;
        }
        if (pendingPoint == null) {
            clearPreview();
            return;
        }
        BlockPos target = pickTarget(minecraft);
        if (target == null) {
            clearPreview();
            return;
        }
        Region region = new Region(pendingPoint, target);
        previewTarget = target;
        if (region.volume() > CopyPaste.MAX_VOLUME) {
            previewPositions = List.of();
            previewStates = List.of();
            previewOutline = List.of();
            previewInvalid = true;
            return;
        }

        List<BlockPos> positions = region.collectPositions();
        List<BlockState> states = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            states.add(minecraft.level.getBlockState(pos));
        }
        previewPositions = positions;
        previewStates = states;
        previewOutline = ChainMining.collectOutlineEdges(positions);
        previewInvalid = false;
    }

    /** 指向的方块；没有指向方块时取准星前方约 2 格的位置。 */
    private static BlockPos pickTarget(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().immutable();
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        return BlockPos.containing(eye.add(look.scale(2.0D)));
    }

    private static void message(String key, Object... args) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    // ---------------------------------------------------------------------
    // 渲染
    // ---------------------------------------------------------------------

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (mode == Mode.NONE || minecraft.level == null) {
            return;
        }
        if (selectionOutline.isEmpty() && previewOutline.isEmpty() && pendingPoint == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // 蓝图虚影：低透明度，但保留方块贴图，便于观察实际效果
        if (mode == Mode.PLACING && !previewPositions.isEmpty()) {
            renderGhostBlocks(poseStack, buffers, previewPositions, previewStates,
                    1.0F, 1.0F, 1.0F, previewInvalid ? 0.20F : 0.35F);
        }
        // 选区还没确定第二点时，用更淡的虚影实时预览范围
        if (mode == Mode.SELECTING && pendingPoint != null && !previewPositions.isEmpty()) {
            renderGhostBlocks(poseStack, buffers, previewPositions, previewStates,
                    0.85F, 0.92F, 1.0F, 0.22F);
        }

        // 先画被方块挡住部分的暗色描边
        VertexConsumer behind = buffers.getBuffer(OUTLINE_BEHIND);
        for (ChainMining.OutlineEdge edge : selectionOutline) {
            renderEdge(poseStack, behind, edge,
                    SELECTION_BEHIND_RED, SELECTION_BEHIND_GREEN, SELECTION_BEHIND_BLUE);
        }
        // 已确定的选区用黄色；正在跟随准星的动态选区用更亮的黄色。
        boolean selectingPending = mode == Mode.SELECTING;
        float behindRed = previewInvalid ? INVALID_BEHIND_RED
                : selectingPending ? PENDING_SELECTION_BEHIND_RED : PREVIEW_BEHIND_RED;
        float behindGreen = previewInvalid ? INVALID_BEHIND_GREEN
                : selectingPending ? PENDING_SELECTION_BEHIND_GREEN : PREVIEW_BEHIND_GREEN;
        float behindBlue = previewInvalid ? INVALID_BEHIND_BLUE
                : selectingPending ? PENDING_SELECTION_BEHIND_BLUE : PREVIEW_BEHIND_BLUE;
        for (ChainMining.OutlineEdge edge : previewOutline) {
            renderEdge(poseStack, behind, edge, behindRed, behindGreen, behindBlue);
        }
        buffers.endBatch(OUTLINE_BEHIND);

        // 再画直接可见的亮色描边
        VertexConsumer front = buffers.getBuffer(OUTLINE_FRONT);
        for (ChainMining.OutlineEdge edge : selectionOutline) {
            renderEdge(poseStack, front, edge, SELECTION_RED, SELECTION_GREEN, SELECTION_BLUE);
        }
        float frontRed = previewInvalid ? INVALID_RED
                : selectingPending ? PENDING_SELECTION_RED : PREVIEW_RED;
        float frontGreen = previewInvalid ? INVALID_GREEN
                : selectingPending ? PENDING_SELECTION_GREEN : PREVIEW_GREEN;
        float frontBlue = previewInvalid ? INVALID_BLUE
                : selectingPending ? PENDING_SELECTION_BLUE : PREVIEW_BLUE;
        for (ChainMining.OutlineEdge edge : previewOutline) {
            renderEdge(poseStack, front, edge, frontRed, frontGreen, frontBlue);
        }
        buffers.endBatch(OUTLINE_FRONT);

        poseStack.popPose();
    }

    /**
     * 画出半透明方块虚影。和快速建造一样，只渲染普通模型方块；
     * 箱子这类 ENTITYBLOCK_ANIMATED 方块不渲染模型（仍然有描边）。
     */
    private static void renderGhostBlocks(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            List<BlockPos> positions, List<BlockState> states,
            float red, float green, float blue, float alpha) {
        if (positions.isEmpty() || positions.size() != states.size() || positions.size() > GHOST_RENDER_LIMIT) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Map<BlockPos, BlockState> previewMap = new HashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            previewMap.put(positions.get(i), states.get(i));
        }
        GhostPreviewLevel ghostLevel = new GhostPreviewLevel(level, previewMap);

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        boolean hasEntityBlock = states.stream()
                .anyMatch(state -> state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED);
        if (hasEntityBlock) {
            // 箱子等 ENTITYBLOCK_ANIMATED 虚影先写一遍深度，再半透明绘制：
            // 被前表面挡住的箱内纹理/背面不会透出来。
            RenderSystem.setShaderColor(red, green, blue, 1.0F);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            try {
                for (int i = 0; i < positions.size(); i++) {
                    BlockState state = states.get(i);
                    if (state.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED) {
                        continue;
                    }
                    BlockPos pos = positions.get(i);
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    if (!renderEntityBlockModel(state, pos, poseStack, buffers)) {
                        dispatcher.renderSingleBlock(state, poseStack, buffers,
                                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, GHOST_BLOCK);
                    }
                    poseStack.popPose();
                }
                buffers.endBatch();
            } finally {
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
            }
        }

        MultiBufferSource ghostEntityBuffers = renderType -> buffers.getBuffer(GhostRenderTypes.translucent(renderType));
        RenderSystem.setShaderColor(red, green, blue, alpha);
        for (int i = 0; i < positions.size(); i++) {
            BlockState state = states.get(i);
            if (state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            BlockPos pos = positions.get(i);
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            if (state.getRenderShape() == RenderShape.MODEL) {
                // checkSides=true 会让 ModelBlockRenderer 用 GhostPreviewLevel 检查邻居，
                // 自动剔除两个相邻虚影方块之间的隐藏面。
                dispatcher.renderBatched(state, pos, ghostLevel, poseStack, buffers.getBuffer(GHOST_BLOCK),
                        true, RandomSource.create(state.getSeed(pos)), ModelData.EMPTY, GHOST_BLOCK);
            } else {
                // ENTITYBLOCK_ANIMATED（箱子、潜影盒、床、旗帜、头颅等）
                // 优先用带真实 BlockState 的方块实体渲染器，保证朝向/床半等状态正确；
                // 没有渲染器时回退到 BlockRenderDispatcher 的 BEWLR 路径。
                boolean rendered = state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED
                        && renderEntityBlockModel(state, pos, poseStack, ghostEntityBuffers);
                if (!rendered) {
                    dispatcher.renderSingleBlock(state, poseStack, ghostEntityBuffers,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, GHOST_BLOCK);
                }
            }
            poseStack.popPose();
        }
        // 先 flush 所有缓冲（MODEL 用 GHOST_BLOCK；ENTITYBLOCK_ANIMATED 用 BEWLR 自己的实体渲染类型），
        // 再恢复颜色，否则实体方块可能在颜色恢复后才真正绘制，失去虚影透明度。
        buffers.endBatch(GHOST_BLOCK);
        buffers.endBatch();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 用真实 BlockState 创建一个临时方块实体并调用对应的方块实体渲染器。
     * 这样箱子、潜影盒、床等 ENTITYBLOCK_ANIMATED 方块在虚影里也能显示实际模型和朝向。
     *
     * @return true 表示已经渲染；false 表示需要回退到普通模型/BEWLR 渲染
     */
    private static boolean renderEntityBlockModel(BlockState state, BlockPos pos, PoseStack poseStack,
            MultiBufferSource buffers) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return false;
        }
        BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
        if (blockEntity == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            blockEntity.setLevel(minecraft.level);
        }

        @SuppressWarnings("unchecked")
        BlockEntityRenderer<BlockEntity> renderer =
                (BlockEntityRenderer<BlockEntity>) minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (renderer == null) {
            return false;
        }
        try {
            renderer.render(blockEntity, 0.0F, poseStack, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException exception) {
            // 某些模组方块实体渲染器依赖完整世界数据，失败时回退到 BEWLR / 普通模型
            return false;
        }
    }

    /** 画一条 1 格长的线。 */
    private static void renderEdge(PoseStack poseStack, VertexConsumer consumer, ChainMining.OutlineEdge edge,
            float red, float green, float blue) {
        BlockPos start = edge.start();
        float x1 = start.getX();
        float y1 = start.getY();
        float z1 = start.getZ();
        float x2 = x1;
        float y2 = y1;
        float z2 = z1;
        float normalX = 0.0F;
        float normalY = 0.0F;
        float normalZ = 0.0F;
        switch (edge.axis()) {
            case X -> {
                x2 += 1.0F;
                normalX = 1.0F;
            }
            case Y -> {
                y2 += 1.0F;
                normalY = 1.0F;
            }
            case Z -> {
                z2 += 1.0F;
                normalZ = 1.0F;
            }
        }

        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, x1, y1, z1).setColor(red, green, blue, 1.0F)
                .setNormal(pose, normalX, normalY, normalZ);
        consumer.addVertex(pose, x2, y2, z2).setColor(red, green, blue, 1.0F)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
