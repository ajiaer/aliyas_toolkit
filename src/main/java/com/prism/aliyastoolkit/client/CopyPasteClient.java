package com.prism.aliyastoolkit.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.ChainMining;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.CopyPaste;
import com.prism.aliyastoolkit.ToolkitSettingsCache;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxData;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.blueprint.BlueprintCapture;
import com.prism.aliyastoolkit.blueprint.BlueprintData;
import com.prism.aliyastoolkit.blueprint.BlueprintRotation;
import com.prism.aliyastoolkit.network.BlueprintBuildPayload;
import com.prism.aliyastoolkit.network.BlueprintCreatePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
 * <li>手持蓝图箱，按 Ctrl + 右键（默认，可改键）进入蓝图建造。没有蓝图进入绘制模式，
 * 有蓝图进入建造模式。</li>
 * <li>绘制模式：第一次选点前显示跟随准星的目标轮廓；右键两次新增选区（浅黄色动态预览），
 * 左键两次删除选区（浅红色动态预览），已确定选区为浅橙色；Ctrl + 左键记录蓝图并收取材料（剪切），
 * Ctrl + 右键只记录蓝图（复制），两者都会退出绘制模式。</li>
 * <li>建造模式：显示虚影，右键固定、左键解除固定，滚轮调整距离，V 旋转；
 * 固定后 Ctrl + 右键检查材料并建造（不足会提示并退出）。</li>
 * <li>箱子没有物品时 Tab 切换绘制/建造模式；有物品时只能建造。</li>
 * <li>旧的 V 蓝图快捷键已废弃，所有蓝图快捷键都可以在按键绑定里修改。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public final class CopyPasteClient {
    /** 已确定的选区轮廓：浅橙色。 */
    private static final float SELECTION_RED = 1.0F;
    private static final float SELECTION_GREEN = 0.78F;
    private static final float SELECTION_BLUE = 0.42F;
    /** 选区轮廓：被方块挡住部分的暗橙色。 */
    private static final float SELECTION_BEHIND_RED = 0.60F;
    private static final float SELECTION_BEHIND_GREEN = 0.42F;
    private static final float SELECTION_BEHIND_BLUE = 0.20F;
    /** 正在动态预览的新增选区：浅黄色。 */
    private static final float PENDING_ADD_RED = 1.0F;
    private static final float PENDING_ADD_GREEN = 0.95F;
    private static final float PENDING_ADD_BLUE = 0.55F;
    private static final float PENDING_ADD_BEHIND_RED = 0.55F;
    private static final float PENDING_ADD_BEHIND_GREEN = 0.52F;
    private static final float PENDING_ADD_BEHIND_BLUE = 0.30F;
    /** 正在动态预览的删除选区：浅红色。 */
    private static final float PENDING_REMOVE_RED = 1.0F;
    private static final float PENDING_REMOVE_GREEN = 0.55F;
    private static final float PENDING_REMOVE_BLUE = 0.55F;
    private static final float PENDING_REMOVE_BEHIND_RED = 0.55F;
    private static final float PENDING_REMOVE_BEHIND_GREEN = 0.28F;
    private static final float PENDING_REMOVE_BEHIND_BLUE = 0.28F;
    /** 预览轮廓：蓝色。 */
    private static final float PREVIEW_RED = 0.35F;
    private static final float PREVIEW_GREEN = 0.65F;
    private static final float PREVIEW_BLUE = 1.0F;
    private static final float PREVIEW_BEHIND_RED = 0.10F;
    private static final float PREVIEW_BEHIND_GREEN = 0.24F;
    private static final float PREVIEW_BEHIND_BLUE = 0.50F;
    /** 准星实时跟随目标方块的粗框宽度。 */
    private static final float TARGET_PREVIEW_WIDTH = 0.020F;
    /** 已确定选区可见前缘的粗轮廓宽度。 */
    private static final float VISIBLE_OUTLINE_WIDTH = 0.010F;
    /** 正在操作的选区预览轮廓宽度。 */
    private static final float PREVIEW_OUTLINE_WIDTH = 0.020F;
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
    /** 进入建造模式时的默认距离：视线前方 4 格。 */
    private static final double DEFAULT_GHOST_DISTANCE = 4.0D;
    /** 自动避开玩家后额外增加的距离。 */
    private static final double SAFE_DISTANCE_PADDING = 2.0D;

    private static final RenderType OUTLINE_BEHIND = createLineType(
            "aliyastoolkit_copy_paste_outline_behind", RenderStateShard.NO_DEPTH_TEST, 2.0D);
    private static final RenderType OUTLINE_FRONT = createLineType(
            "aliyastoolkit_copy_paste_outline_front", RenderStateShard.LEQUAL_DEPTH_TEST, 2.0D);

    /**
     * 低透明度方块虚影；颜色由 ColorModulator 控制。
     * CULL 让方块背面不绘制，相邻虚影方块之间的隐藏面由 renderBatched 的 checkSides 剔除；
     * 轮廓线使用独立的 RenderType，不受这里的剔除影响。
     */
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
                    .setCullState(RenderStateShard.CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private static Object lastLevel;

    /** 选区操作按顺序执行：后一次添加可以恢复之前删除的范围。 */
    private static final List<SelectionChange> SELECTION_CHANGES = new ArrayList<>();
    /** 选区里所有坐标（包含空气，便于右键点击判定）。 */
    private static List<BlockPos> selectionPositions = List.of();
    private static List<ChainMining.OutlineEdge> selectionOutline = List.of();
    /** 正在等待第二个点的选区起点（右键新增）。 */
    private static BlockPos pendingPoint;
    /** 正在等待第二个点的删除选区起点（左键删除）。 */
    private static BlockPos pendingRemovalPoint;

    private enum Mode {
        NONE,
        SELECTING,
        PLACING
    }

    private static Mode mode = Mode.NONE;

    /** 当前动态预览的用途，用于决定轮廓颜色。 */
    private enum PreviewKind {
        NONE,
        /** 未选第一个点时跟随准星的目标预览（淡黄色）。 */
        SELECTION_FOLLOW,
        SELECTION_ADD,
        SELECTION_REMOVE,
        BUILD
    }

    private static PreviewKind previewKind = PreviewKind.NONE;

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
    /** 以当前指示器为中心，在半径 8 的球内寻找最近的可放置位置。 */
    private static final int PLACEMENT_SEARCH_RADIUS = CopyPaste.BLUEPRINT_PLACEMENT_SEARCH_RADIUS;
    private static final List<BlockPos> PLACEMENT_SEARCH_OFFSETS = buildPlacementSearchOffsets();
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

    /** 一次添加 / 删除操作；按操作顺序重建，后一次操作覆盖前一次。 */
    private record SelectionChange(boolean add, Region region) {
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
        clearBlueprintKeyClicks();
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
        // 主手蓝图箱被切换/数据变化后自动退出，避免虚影和实际蓝图不一致。
        if (mode != Mode.NONE) {
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BlueprintBoxItem)
                    || !ItemStack.isSameItemSameComponents(previewBox, held)) {
                message("message.aliyastoolkit.blueprint.box_changed");
                forceDisable();
                return;
            }
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
        if (matchesKey(AliyasToolkitClient.BLUEPRINT_BUILD_KEY, event)) {
            if (mode == Mode.NONE) {
                if (Config.ENABLE_COPY_PASTE.getAsBoolean()
                        && minecraft.player.getMainHandItem().getItem() instanceof BlueprintBoxItem) {
                    enterBlueprintMode(minecraft);
                    minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            } else {
                handleBlueprintTrigger(minecraft);
            }
            return;
        }
        if (mode == Mode.NONE) {
            return;
        }
        if (mode == Mode.SELECTING && matchesKey(AliyasToolkitClient.BLUEPRINT_CUT_KEY, event)) {
            recordSelection(minecraft, true);
            minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
        if (matchesKey(AliyasToolkitClient.BLUEPRINT_MODE_KEY, event)) {
            // Tab 默认也是原版玩家列表：在蓝图模式里清掉它的点击，避免按 Tab 时闪出玩家列表。
            minecraft.options.keyPlayerList.setDown(false);
            while (minecraft.options.keyPlayerList.consumeClick()) {
            }
            switchMode(minecraft);
            return;
        }
        if (matchesKey(AliyasToolkitClient.BLUEPRINT_ROTATE_KEY, event)) {
            rotate(minecraft);
            return;
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

        // Alt + 右键交给 BlueprintBoxClient 打开蓝图箱界面。
        if (matchesMouse(AliyasToolkitClient.BLUEPRINT_OPEN_BOX_KEY, event.getButton())) {
            return;
        }

        if (matchesMouse(AliyasToolkitClient.BLUEPRINT_BUILD_KEY, event.getButton())) {
            if (mode == Mode.NONE) {
                if (!Config.ENABLE_COPY_PASTE.getAsBoolean()
                        || !(minecraft.player.getMainHandItem().getItem() instanceof BlueprintBoxItem)) {
                    return;
                }
                event.setCanceled(true);
                enterBlueprintMode(minecraft);
            } else {
                event.setCanceled(true);
                handleBlueprintTrigger(minecraft);
            }
            minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }

        if (mode == Mode.SELECTING && matchesMouse(AliyasToolkitClient.BLUEPRINT_CUT_KEY, event.getButton())) {
            event.setCanceled(true);
            recordSelection(minecraft, true);
            minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }

        if (mode != Mode.NONE) {
            if (matchesMouse(AliyasToolkitClient.BLUEPRINT_MODE_KEY, event.getButton())) {
                event.setCanceled(true);
                switchMode(minecraft);
                return;
            }
            if (matchesMouse(AliyasToolkitClient.BLUEPRINT_ROTATE_KEY, event.getButton())) {
                event.setCanceled(true);
                rotate(minecraft);
                return;
            }
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
        }
    }

    @SubscribeEvent
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (mode == Mode.NONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        // 蓝图模式期间滚轮不切换快捷栏；建造模式下用来调整虚影距离。
        event.setCanceled(true);
        if (mode != Mode.PLACING || fixedTarget != null) {
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

    /** 手持蓝图箱按主按键：没有蓝图时进入绘制模式，否则进入建造模式。 */
    private static void enterBlueprintMode(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !Config.ENABLE_COPY_PASTE.getAsBoolean()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlueprintBoxItem)) {
            message("message.aliyastoolkit.blueprint.need_box");
            return;
        }
        BlueprintData blueprint = BlueprintBoxData.readBlueprint(held, minecraft.level.registryAccess());
        if (blueprint == null || blueprint.isEmpty()) {
            startSelecting(minecraft);
        } else {
            startPlacement(minecraft);
        }
    }

    /** 模式 1：蓝图绘制。 */
    private static void startSelecting(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || !Config.ENABLE_COPY_PASTE.getAsBoolean()) {
            return;
        }
        resetAll();
        mode = Mode.SELECTING;
        previewBox = player.getMainHandItem().copy();
        ExclusiveModes.activate(ExclusiveModes.COPY_PASTE);
        message("message.aliyastoolkit.blueprint.mode_draw");
    }

    /** 在没有物品的蓝图箱上切换绘制 / 建造模式。 */
    private static void switchMode(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || mode == Mode.NONE) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlueprintBoxItem)) {
            message("message.aliyastoolkit.blueprint.need_box");
            forceDisable();
            return;
        }
        if (BlueprintBoxData.hasItems(held)) {
            message("message.aliyastoolkit.blueprint.has_items_no_switch");
            return;
        }
        if (mode == Mode.PLACING) {
            startSelecting(minecraft);
            message("message.aliyastoolkit.blueprint.mode_switched_draw");
            return;
        }
        BlueprintData blueprint = BlueprintBoxData.readBlueprint(held, minecraft.level.registryAccess());
        if (blueprint == null || blueprint.isEmpty()) {
            message("message.aliyastoolkit.blueprint.no_blueprint");
            return;
        }
        if (startPlacement(minecraft)) {
            message("message.aliyastoolkit.blueprint.mode_switched_build");
        }
    }

    /** Ctrl + 右键：绘制模式只记录蓝图（复制），建造模式检查材料并建造。 */
    private static void handleBlueprintTrigger(Minecraft minecraft) {
        if (mode == Mode.SELECTING) {
            recordSelection(minecraft, false);
        } else if (mode == Mode.PLACING) {
            commitPlacement(minecraft);
        }
    }

    /** 被其它互斥模式关闭，或玩家退出世界时调用。 */
    public static void forceDisable() {
        resetAll();
        ExclusiveModes.deactivate(ExclusiveModes.COPY_PASTE);
    }

    /** 当前是否处于蓝图绘制或建造模式。 */
    public static boolean isActive() {
        return mode != Mode.NONE;
    }

    private static void resetAll() {
        SELECTION_CHANGES.clear();
        selectionPositions = List.of();
        selectionOutline = List.of();
        pendingPoint = null;
        pendingRemovalPoint = null;
        mode = Mode.NONE;
        previewKind = PreviewKind.NONE;
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
        lastNoSpaceTarget = null;
        lastNoSpaceRotation = -1;
        noSpaceSearchCooldown = 0;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastLevel = null;
        forceDisable();
    }

    private static boolean matchesKey(KeyMapping mapping, InputEvent.Key event) {
        return mapping.matches(event.getKey(), event.getScanCode())
                && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }

    private static void clearBlueprintKeyClicks() {
        while (AliyasToolkitClient.BLUEPRINT_BUILD_KEY.consumeClick()) {
        }
        while (AliyasToolkitClient.BLUEPRINT_CUT_KEY.consumeClick()) {
        }
        while (AliyasToolkitClient.BLUEPRINT_MODE_KEY.consumeClick()) {
        }
        while (AliyasToolkitClient.BLUEPRINT_SNAP_KEY.consumeClick()) {
        }
        while (AliyasToolkitClient.BLUEPRINT_ROTATE_KEY.consumeClick()) {
        }
    }

    private static boolean matchesMouse(KeyMapping mapping, int button) {
        return mapping.matchesMouse(button)
                && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static int countEmptyBuckets(LocalPlayer player) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(net.minecraft.world.item.Items.BUCKET)) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(net.minecraft.world.item.Items.BUCKET)) {
            count += offhand.getCount();
        }
        return count;
    }

    // ---------------------------------------------------------------------
    // 选区
    // ---------------------------------------------------------------------

    private static void handleSelectionRightClick(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        // 右键是“新增”：先取消正在进行的删除预览。
        if (pendingRemovalPoint != null) {
            pendingRemovalPoint = null;
            updateSelectionPreview(minecraft);
            message("message.aliyastoolkit.copy_paste_pending_cancelled");
            return;
        }

        BlockPos target = pickTarget(minecraft);
        if (target == null) {
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

    /**
     * 左键是“删除”：第一次选起点，第二次确定一个删除范围，
     * 黄色已选区里被这个范围覆盖的部分会被取消。可反复删除。
     */
    private static void handleSelectionLeftClick(Minecraft minecraft) {
        if (minecraft.player == null) {
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
        if (pendingRemovalPoint == null) {
            pendingRemovalPoint = target;
            updateSelectionPreview(minecraft);
            message("message.aliyastoolkit.copy_paste_remove_first_point");
            return;
        }
        int before = selectionPositions.size();
        if (addRemovalRegion(pendingRemovalPoint, target)) {
            pendingRemovalPoint = null;
            updateSelectionPreview(minecraft);
            message("message.aliyastoolkit.copy_paste_regions_removed",
                    Math.max(0, before - selectionPositions.size()));
        }
    }

    private static boolean addRegion(BlockPos first, BlockPos second) {
        Region region = new Region(first, second);
        if (region.volume() > CopyPaste.MAX_VOLUME) {
            message("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME);
            return false;
        }
        Set<BlockPos> candidate = new LinkedHashSet<>(selectionPositions);
        region.collectInto(candidate);
        if (candidate.size() > CopyPaste.MAX_VOLUME) {
            message("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME);
            return false;
        }
        // 后添加的区域覆盖之前删除的区域，重建时按操作顺序执行。
        SELECTION_CHANGES.add(new SelectionChange(true, region));
        rebuildSelection();
        message("message.aliyastoolkit.copy_paste_region_added", selectionPositions.size());
        return true;
    }

    private static boolean addRemovalRegion(BlockPos first, BlockPos second) {
        Region region = new Region(first, second);
        if (region.volume() > CopyPaste.MAX_VOLUME) {
            message("message.aliyastoolkit.copy_paste_too_big", CopyPaste.MAX_VOLUME);
            return false;
        }
        SELECTION_CHANGES.add(new SelectionChange(false, region));
        rebuildSelection();
        return true;
    }

    private static void rebuildSelection() {
        Set<BlockPos> union = new LinkedHashSet<>();
        for (SelectionChange change : SELECTION_CHANGES) {
            if (change.add()) {
                change.region().collectInto(union);
            } else {
                union.removeIf(change.region()::contains);
            }
        }
        selectionPositions = List.copyOf(union);
        selectionOutline = union.isEmpty() ? List.of() : ChainMining.collectOutlineEdges(union);
    }

    /**
     * 记录当前选区：
     * <ul>
     * <li>{@code cut = true}（Ctrl + 左键）：把材料收进主手的蓝图箱，并让服务端清除源方块；</li>
     * <li>{@code cut = false}（Ctrl + 右键）：只记录蓝图，不拿取源方块和材料。</li>
     * </ul>
     * 箱子必须没有物品；可以已经存有蓝图（会被新蓝图覆盖）。
     * 容量不够时只提示，不退出绘制模式。
     */
    private static void recordSelection(Minecraft minecraft, boolean cut) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlueprintBoxItem boxItem)) {
            message("message.aliyastoolkit.blueprint.need_box");
            forceDisable();
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

        BlueprintCreatePayload payload = BlueprintCreatePayload.of(cut, positions);
        boolean collectFluids = ToolkitSettingsCache.get().collectFluids();
        BlueprintCapture.Result result = BlueprintCapture.capture(minecraft.level, payload.min(),
                payload.offsets(), player, collectFluids);
        if (cut && collectFluids && result.blueprint() != null
                && result.blueprint().fluidSourceCount() > countEmptyBuckets(player)) {
            result = BlueprintCapture.capture(minecraft.level, payload.min(), payload.offsets(), player, false);
        }
        if (result.errorKey() != null || result.blueprint() == null) {
            if (result.skippedBlueprintBoxes() > 0) {
                message("message.aliyastoolkit.blueprint.only_boxes_skipped", result.skippedBlueprintBoxes());
            } else {
                message(result.errorKey() == null
                        ? "message.aliyastoolkit.copy_paste_no_blocks" : result.errorKey());
            }
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
        if (!(held.getItem() instanceof BlueprintBoxItem)) {
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
        // 材料不足也允许进入建造预览；真正建造时再检查并提示。
        resetAll();
        mode = Mode.PLACING;
        previewKind = PreviewKind.BUILD;
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
        ExclusiveModes.activate(ExclusiveModes.COPY_PASTE);
        // 玩家与虚影的碰撞只在刚放出虚影时检测一次，之后不再把虚影从玩家身边推开，
        // 保证刚打开时能一眼看到完整结构。
        ensureSafeDistance(minecraft);
        updatePlacementPreview(minecraft);
        message("message.aliyastoolkit.blueprint.mode_build");
        return true;
    }

    private static void handlePlacementRightClick(Minecraft minecraft) {
        if (fixedTarget != null) {
            message("message.aliyastoolkit.blueprint.already_pinned");
            return;
        }
        if (previewTarget == null || previewPositions.isEmpty()) {
            return;
        }
        fixedTarget = previewTarget.immutable();
        updatePlacementPreview(minecraft);
        message("message.aliyastoolkit.blueprint.pinned");
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
        previewKind = PreviewKind.BUILD;
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
     * 初始距离 4 格；如果结构 AABB 会碰到玩家，就逐步推远到刚好碰不到，再额外加 2 格。
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
        for (BlockPos offset : PLACEMENT_SEARCH_OFFSETS) {
            BlockPos candidate = rawTarget.offset(offset);
            if (canPlaceBlueprintAt(player.level(), candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 所有蓝图目标方块都必须可替换。
     * <p>
     * 玩家与虚影的碰撞只在 {@link #startPlacement} 刚放出虚影时由
     * {@link #ensureSafeDistance} 检测一次，之后这里不再检查，保证移动视角时虚影不会突然被推开。
     */
    private static boolean canPlaceBlueprintAt(net.minecraft.world.level.Level level, BlockPos target) {
        List<BlockPos> destinations = computeDestinations(target);
        if (destinations.isEmpty()) {
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
        previewKind = PreviewKind.NONE;
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
            message("message.aliyastoolkit.blueprint.not_enough_exit");
            forceDisable();
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
        boolean removing = pendingPoint == null && pendingRemovalPoint != null;
        BlockPos base = pendingPoint != null ? pendingPoint : pendingRemovalPoint;
        if (base == null) {
            // 第一次选点之前也显示跟随准星的目标轮廓，方便确认当前处于蓝图绘制模式。
            BlockPos target = pickTarget(minecraft);
            if (target == null) {
                clearPreview();
                return;
            }
            previewTarget = target;
            previewKind = PreviewKind.SELECTION_FOLLOW;
            previewPositions = List.of(target);
            previewStates = List.of(minecraft.level.getBlockState(target));
            previewOutline = ChainMining.collectOutlineEdges(List.of(target));
            previewInvalid = false;
            return;
        }
        BlockPos target = pickTarget(minecraft);
        if (target == null) {
            clearPreview();
            return;
        }
        Region region = new Region(base, target);
        previewTarget = target;
        previewKind = removing ? PreviewKind.SELECTION_REMOVE : PreviewKind.SELECTION_ADD;
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
            BlockPos hitPos = blockHit.getBlockPos();
            // 按住吸附键（默认 Shift）时取指向方块的外侧面，和快速建造的选点一致。
            if (isSnapKeyDown()) {
                BlockPos adjacent = hitPos.relative(blockHit.getDirection());
                if (isReplaceable(minecraft.level, adjacent)) {
                    return adjacent.immutable();
                }
                if (isReplaceable(minecraft.level, hitPos)) {
                    return hitPos.immutable();
                }
                return adjacent.immutable();
            }
            return hitPos.immutable();
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        return BlockPos.containing(eye.add(look.scale(2.0D)));
    }

    private static boolean isReplaceable(net.minecraft.world.level.Level level, BlockPos pos) {
        return level != null && (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced());
    }

    /** 吸附键默认是 Shift：默认配置下左右 Shift 都生效；改键后只使用改后的键。 */
    private static boolean isSnapKeyDown() {
        return AliyasToolkitClient.BLUEPRINT_SNAP_KEY.isDown()
                || (AliyasToolkitClient.BLUEPRINT_SNAP_KEY.isDefault() && Screen.hasShiftDown());
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
        if (selectionOutline.isEmpty() && previewOutline.isEmpty() && previewTarget == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // 蓝图虚影同样不能使用 Iris 的延迟 bufferSource，否则颜色/光照状态
        // 可能在真正 flush 时已经改变。
        try (ByteBufferBuilder ghostBuffer = new ByteBufferBuilder(4096)) {
            MultiBufferSource.BufferSource ghostBuffers = MultiBufferSource.immediate(ghostBuffer);

            // 蓝图虚影：参考 Create 蓝图预览，使用满亮方块模型。
            // 保留原始贴图/颜色和现有轮廓线，不叠加表面色。
            if (mode == Mode.PLACING && !previewPositions.isEmpty()) {
                renderGhostBlocks(poseStack, ghostBuffers, previewPositions, previewStates,
                        1.0F, 1.0F, 1.0F, 1.0F, true);
            }
            // 绘制模式的选区范围只显示轮廓，不再复制区域方块虚影。
            // 这样和 Create 强力胶一致，也不会让 Iris 对整片虚影重新做一次光照/后处理。
        }

        // 其他模组可能在自己的渲染阶段改过 ColorModulator 后没有复位；
        // 线条颜色会再乘一次该值，导致看起来整体很暗。画线前强制恢复白色。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Iris 会把 renderBuffers().bufferSource() 替换为延迟绘制的
        // FullyBufferedMultiBufferSource；轮廓线必须在自己的渲染阶段立即绘制，
        // 否则真正绘制时全局颜色/深度状态可能已经被其它渲染阶段改变。
        try (ByteBufferBuilder lineBuffer = new ByteBufferBuilder(1536)) {
            MultiBufferSource.BufferSource lineBuffers = MultiBufferSource.immediate(lineBuffer);

            // 先画被方块挡住部分的暗色描边
            VertexConsumer behind = lineBuffers.getBuffer(OUTLINE_BEHIND);
            for (ChainMining.OutlineEdge edge : selectionOutline) {
                renderEdge(poseStack, behind, edge,
                        SELECTION_BEHIND_RED, SELECTION_BEHIND_GREEN, SELECTION_BEHIND_BLUE);
            }
            // 已确定的选区始终是黄色；动态预览颜色优先级更高，覆盖在黄色之上。
            float behindRed = previewInvalid ? INVALID_BEHIND_RED
                    : previewKind == PreviewKind.SELECTION_FOLLOW ? PENDING_ADD_BEHIND_RED
                    : previewKind == PreviewKind.SELECTION_ADD ? PENDING_ADD_BEHIND_RED
                    : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_BEHIND_RED
                    : PREVIEW_BEHIND_RED;
            float behindGreen = previewInvalid ? INVALID_BEHIND_GREEN
                    : previewKind == PreviewKind.SELECTION_FOLLOW ? PENDING_ADD_BEHIND_GREEN
                    : previewKind == PreviewKind.SELECTION_ADD ? PENDING_ADD_BEHIND_GREEN
                    : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_BEHIND_GREEN
                    : PREVIEW_BEHIND_GREEN;
            float behindBlue = previewInvalid ? INVALID_BEHIND_BLUE
                    : previewKind == PreviewKind.SELECTION_FOLLOW ? PENDING_ADD_BEHIND_BLUE
                    : previewKind == PreviewKind.SELECTION_ADD ? PENDING_ADD_BEHIND_BLUE
                    : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_BEHIND_BLUE
                    : PREVIEW_BEHIND_BLUE;
            for (ChainMining.OutlineEdge edge : previewOutline) {
                renderEdge(poseStack, behind, edge, behindRed, behindGreen, behindBlue);
            }
            lineBuffers.endBatch(OUTLINE_BEHIND);

            // 再画直接可见的亮色描边；可见前缘使用 Create 强力胶式 cuboid 粗线。
            VertexConsumer front = lineBuffers.getBuffer(ThickOutlineRenderer.type());
            for (ChainMining.OutlineEdge edge : selectionOutline) {
                ThickOutlineRenderer.renderEdge(poseStack, front, edge.start(), edge.axis(),
                        VISIBLE_OUTLINE_WIDTH, SELECTION_RED, SELECTION_GREEN, SELECTION_BLUE, 1.0F);
            }
            float frontRed = previewInvalid ? INVALID_RED
                    : previewKind == PreviewKind.SELECTION_FOLLOW ? PENDING_ADD_RED
                    : previewKind == PreviewKind.SELECTION_ADD ? PENDING_ADD_RED
                    : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_RED
                    : PREVIEW_RED;
            float frontGreen = previewInvalid ? INVALID_GREEN
                    : previewKind == PreviewKind.SELECTION_FOLLOW ? PENDING_ADD_GREEN
                    : previewKind == PreviewKind.SELECTION_ADD ? PENDING_ADD_GREEN
                    : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_GREEN
                    : PREVIEW_GREEN;
            float frontBlue = previewInvalid ? INVALID_BLUE
                    : previewKind == PreviewKind.SELECTION_FOLLOW ? PENDING_ADD_BLUE
                    : previewKind == PreviewKind.SELECTION_ADD ? PENDING_ADD_BLUE
                    : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_BLUE
                    : PREVIEW_BLUE;
            for (ChainMining.OutlineEdge edge : previewOutline) {
                ThickOutlineRenderer.renderEdge(poseStack, front, edge.start(), edge.axis(),
                        PREVIEW_OUTLINE_WIDTH, frontRed, frontGreen, frontBlue, 1.0F);
            }
            lineBuffers.endBatch(ThickOutlineRenderer.type());

            // 准星实时跟随的目标方块：比选区前缘轮廓略粗。
            // 选了第一个点后、第二个点确定前不显示跟随框。
            if (mode == Mode.SELECTING && previewTarget != null
                    && pendingPoint == null && pendingRemovalPoint == null) {
                float targetRed = previewInvalid ? INVALID_RED
                        : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_RED : PENDING_ADD_RED;
                float targetGreen = previewInvalid ? INVALID_GREEN
                        : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_GREEN : PENDING_ADD_GREEN;
                float targetBlue = previewInvalid ? INVALID_BLUE
                        : previewKind == PreviewKind.SELECTION_REMOVE ? PENDING_REMOVE_BLUE : PENDING_ADD_BLUE;
                VertexConsumer target = lineBuffers.getBuffer(ThickOutlineRenderer.type());
                ThickOutlineRenderer.renderBox(poseStack, target,
                        new AABB(previewTarget).inflate(ThickOutlineRenderer.BOX_INFLATE),
                        TARGET_PREVIEW_WIDTH, targetRed, targetGreen, targetBlue, 1.0F);
                lineBuffers.endBatch(ThickOutlineRenderer.type());
            }
        }

        poseStack.popPose();
    }

    /**
     * 画出半透明方块虚影。和快速建造一样，只渲染普通模型方块；
     * 箱子这类 ENTITYBLOCK_ANIMATED 方块不渲染模型（仍然有描边）。
     */
    private static void renderGhostBlocks(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            List<BlockPos> positions, List<BlockState> states,
            float red, float green, float blue, float alpha, boolean fullBright) {
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
        GhostPreviewLevel ghostLevel = new GhostPreviewLevel(level, previewMap, fullBright);

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
        try {
            for (int i = 0; i < positions.size(); i++) {
                BlockState state = states.get(i);
                if (state.getRenderShape() == RenderShape.INVISIBLE) {
                    continue;
                }
                BlockPos pos = positions.get(i);
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                if (state.getRenderShape() == RenderShape.MODEL) {
                    // 使用 GhostPreviewLevel 的真实光照，并按相邻虚影方块剔除隐藏面，
                    // 避免 renderSingleBlock 画出内部面造成光栅纹/闪烁。
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
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
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
