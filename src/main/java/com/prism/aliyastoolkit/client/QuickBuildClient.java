package com.prism.aliyastoolkit.client;

import java.util.List;
import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.BuildPreset;
import com.prism.aliyastoolkit.ChainMining;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.QuickBuild;
import com.prism.aliyastoolkit.ToolkitSettings;
import com.prism.aliyastoolkit.ToolkitSettingsCache;
import com.prism.aliyastoolkit.network.CycleBuildPresetPayload;
import com.prism.aliyastoolkit.network.QuickBuildPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端的“快速建造”：
 * <ul>
 * <li>快捷键（默认 C）切换开关；按住快捷键滚动滚轮切换建造预设（不循环）。</li>
 * <li>开启时右键第一次记录第一个点，之后每 tick 显示规划范围（蓝色轮廓）和辅助线，</li>
 * <li>右键第二次校验手持方块/数量后把请求发给服务端建造。</li>
 * <li>和连锁破坏互斥：开启一个会自动关闭另一个。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public final class QuickBuildClient {
    /** 直接可见的蓝色轮廓。 */
    private static final float FRONT_RED = 0.40F;
    private static final float FRONT_GREEN = 0.72F;
    private static final float FRONT_BLUE = 1.0F;
    /** 被方块挡住部分的暗蓝色。 */
    private static final float BEHIND_RED = 0.16F;
    private static final float BEHIND_GREEN = 0.30F;
    private static final float BEHIND_BLUE = 0.58F;
    /** 辅助线颜色（比轮廓暗一点）。 */
    private static final float HELPER_RED = 0.30F;
    private static final float HELPER_GREEN = 0.55F;
    private static final float HELPER_BLUE = 0.90F;

    private static final RenderType OUTLINE_BEHIND = createLineType(
            "aliyastoolkit_build_outline_behind", RenderStateShard.NO_DEPTH_TEST, 2.0D);
    private static final RenderType OUTLINE_FRONT = createLineType(
            "aliyastoolkit_build_outline_front", RenderStateShard.LEQUAL_DEPTH_TEST, 2.0D);
    private static final RenderType HELPER_LINE = createLineType(
            "aliyastoolkit_build_helper_line", RenderStateShard.LEQUAL_DEPTH_TEST, 1.5D);
    private static final RenderType FIRST_POINT_BOX = createLineType(
            "aliyastoolkit_build_first_point", RenderStateShard.LEQUAL_DEPTH_TEST, 4.0D);

    private static final double BOX_INFLATE = 0.002D;

    private static boolean enabled;
    private static boolean keyHeld;
    private static boolean scrolledWhileHeld;

    /** 第一个点（右键第一次选中的位置）。 */
    private static BlockPos firstPoint;
    /** 指示器当前指的位置（未吸附）。 */
    private static BlockPos rawTarget;
    /** 吸附后规划的第二个点（显示用：圆形会吸附到正方向）。 */
    private static BlockPos plannedPoint;
    /** 用来计算范围的第二个点（圆形会补齐成正方形/立方体）。 */
    private static BlockPos plannedBox;
    /** 规划范围的轮廓线。 */
    private static List<ChainMining.OutlineEdge> outline = List.of();
    /** 规划范围需要的方块数量。 */
    private static int plannedCount;
    /** 状态提示的刷新计时。 */
    private static int statusTimer;
    /** 第一个点是否落在上半位置（决定半砖放成上半砖还是下半砖）。 */
    private static boolean slabTop;

    private QuickBuildClient() {
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

    /** 键盘 keydown / keyup（和连锁快捷键一样，忽略按键重复）。 */
    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        KeyMapping key = AliyasToolkitClient.QUICK_BUILD_KEY;
        if (!key.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft minecraft = Minecraft.getInstance();
            onKeyDown(minecraft.screen == null && key.isDown());
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            onKeyUp(Minecraft.getInstance());
        }
    }

    /** 快捷键被改到鼠标按键时同样处理。 */
    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!AliyasToolkitClient.QUICK_BUILD_KEY.matchesMouse(event.getButton())) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS) {
            onKeyDown(Minecraft.getInstance().screen == null);
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            onKeyUp(Minecraft.getInstance());
        }
    }

    private static void onKeyDown(boolean active) {
        keyHeld = active;
        scrolledWhileHeld = false;
    }

    private static void onKeyUp(Minecraft minecraft) {
        boolean toggle = keyHeld && !scrolledWhileHeld;
        keyHeld = false;
        scrolledWhileHeld = false;
        if (toggle) {
            setEnabled(minecraft, !enabled);
        }
    }

    /** 开关快速建造；开启时会关掉其它互斥模式（连锁破坏等）。 */
    public static void setEnabled(Minecraft minecraft, boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        resetPlan();
        if (value) {
            ExclusiveModes.activate(ExclusiveModes.QUICK_BUILD);
        } else {
            ExclusiveModes.deactivate(ExclusiveModes.QUICK_BUILD);
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    value ? "message.aliyastoolkit.build_on" : "message.aliyastoolkit.build_off"), true);
        }
    }

    /** 由互斥模式管理器调用：强制关闭快速建造，不会再反过来关闭别人。 */
    public static void forceDisable() {
        if (!enabled) {
            return;
        }
        enabled = false;
        keyHeld = false;
        scrolledWhileHeld = false;
        resetPlan();
        ExclusiveModes.deactivate(ExclusiveModes.QUICK_BUILD);
    }

    private static void resetPlan() {
        firstPoint = null;
        rawTarget = null;
        plannedPoint = null;
        plannedBox = null;
        outline = List.of();
        plannedCount = 0;
        statusTimer = 0;
        slabTop = false;
    }


    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.getConnection() == null) {
            resetPlan();
            return;
        }
        // 原版点击队列在按住时会随按键重复不断入队，这里清空即可
        while (AliyasToolkitClient.QUICK_BUILD_KEY.consumeClick()) {
        }

        boolean usable = Config.ENABLE_QUICK_BUILD.getAsBoolean()
                && minecraft.screen == null
                && player.isAlive()
                && !player.isDeadOrDying()
                && !player.isSpectator();
        if (!usable) {
            keyHeld = false;
            scrolledWhileHeld = false;
            return;
        }
        if (!enabled) {
            resetPlan();
            return;
        }

        updatePreview(minecraft, player);

        // 每隔 10 tick 在动作栏显示一次“所需 / 拥有”的数量
        if (firstPoint != null && plannedPoint != null && plannedCount > 0) {
            if (--statusTimer <= 0) {
                statusTimer = 10;
                int available = countItem(player, player.getMainHandItem().getItem());
                player.displayClientMessage(Component.translatable(
                        "message.aliyastoolkit.build_status", plannedCount, available), true);
            }
        }
    }

    /** 按住快捷键滚动滚轮：切换建造预设（不循环）。 */
    @SubscribeEvent
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!Config.ENABLE_QUICK_BUILD.getAsBoolean()) {
            return;
        }
        if (!AliyasToolkitClient.QUICK_BUILD_KEY.isDown()) {
            return;
        }
        // 只有快速建造已经开启时才允许用滚轮切换预设
        if (!enabled) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        event.setCanceled(true);
        scrolledWhileHeld = true;
        switchPreset(minecraft, delta < 0.0D ? 1 : -1);
    }

    private static void switchPreset(Minecraft minecraft, int direction) {
        ToolkitSettings settings = ToolkitSettingsCache.get();
        int size = settings.buildPresets().size();
        if (size > 1) {
            int next = CycleBuildPresetPayload.nextIndex(settings.selectedBuildPreset(), size, direction);
            if (next != settings.selectedBuildPreset()) {
                settings = settings.withSelectedBuildPreset(next);
                ToolkitSettingsCache.set(settings);
                if (minecraft.getConnection() != null
                        && NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(),
                                ConnectionProtocol.PLAY, CycleBuildPresetPayload.TYPE.id())) {
                    PacketDistributor.sendToServer(new CycleBuildPresetPayload(direction));
                }
            }
        }
        if (minecraft.player != null) {
            BuildPreset preset = settings.buildPreset();
            minecraft.player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_preset",
                    settings.selectedBuildPreset() + 1, settings.buildPresets().size(),
                    Component.translatable(preset.operation().translationKey()),
                    Component.translatable(preset.dimension().translationKey()),
                    Component.translatable(preset.shape().translationKey()),
                    Component.translatable(preset.fill().translationKey())), true);
        }
    }


    /** 快速建造开启时，右键不交给原版（不放置/不交互），而是用来选点。 */
    @SubscribeEvent
    static void onRightClick(InputEvent.MouseButton.Pre event) {
        if (!enabled || event.getButton() != 1 || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!Config.ENABLE_QUICK_BUILD.getAsBoolean()) {
            return;
        }
        // 取消原版右键：keyUse 不会变成按下，所以不会放置方块、也不会连续触发
        event.setCanceled(true);
        handleRightClick(minecraft, minecraft.player);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
    }

    /** 兜底：如果是用键盘的“使用”键触发，也同样屏蔽原版交互。 */
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!enabled || !event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!Config.ENABLE_QUICK_BUILD.getAsBoolean()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
    }

    /** 右键：第一次选第一个点，第二次校验并建造。 */
    private static void handleRightClick(Minecraft minecraft, LocalPlayer player) {
        BlockPos target = pickTarget(minecraft);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_no_target"), true);
            return;
        }

        BuildPreset preset = ToolkitSettingsCache.get().buildPreset();
        if (firstPoint == null) {
            firstPoint = target.immutable();
            // 第一个点落在上半/下半位置：决定半砖放成上半砖还是下半砖
            slabTop = isTopHalf(minecraft.hitResult);
            statusTimer = 0;
            updatePreview(minecraft, player);
            player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_first_point"), true);
            return;
        }

        BlockPos box = QuickBuild.snapBox(firstPoint, target, preset, player.getViewVector(1.0F));
        List<BlockPos> positions = QuickBuild.positions(firstPoint, box, preset);
        if (positions.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.build_too_big", QuickBuild.MAX_VOLUME), true);
            resetPlan();
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!isPlaceableSolid(held, minecraft.level, target)) {
            player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_not_block"), true);
            resetPlan();
            return;
        }
        int available = countItem(player, held.getItem());
        if (available < positions.size()) {
            player.displayClientMessage(Component.translatable("message.aliyastoolkit.build_not_enough",
                    positions.size(), available), true);
            resetPlan();
            return;
        }

        if (minecraft.getConnection() != null
                && NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY,
                        QuickBuildPayload.TYPE.id())) {
            PacketDistributor.sendToServer(new QuickBuildPayload(firstPoint, box, preset, slabTop));
        }
        resetPlan();
    }

    /** 每 tick 刷新规划范围（蓝色轮廓）和辅助线。 */
    private static void updatePreview(Minecraft minecraft, LocalPlayer player) {
        if (firstPoint == null) {
            rawTarget = null;
            plannedPoint = null;
            outline = List.of();
            plannedCount = 0;
            return;
        }
        BlockPos target = pickTarget(minecraft);
        rawTarget = target;
        if (target == null) {
            plannedPoint = null;
            outline = List.of();
            plannedCount = 0;
            return;
        }

        BuildPreset preset = ToolkitSettingsCache.get().buildPreset();
        Vec3 look = player.getViewVector(1.0F);
        plannedPoint = QuickBuild.snap(firstPoint, target, preset, look);
        plannedBox = QuickBuild.snapBox(firstPoint, target, preset, look);
        List<BlockPos> positions = QuickBuild.positions(firstPoint, plannedBox, preset);
        plannedCount = positions.size();
        // 范围太大时只显示数量，不画轮廓（避免每 tick 计算太多边）
        outline = positions.isEmpty() || positions.size() > 2048
                ? List.of()
                : ChainMining.collectOutlineEdges(positions);
    }

    /**
     * 选点的位置：优先取准星指向方块“靠近玩家的那一面”的空气格；
     * 没有指向方块时，取玩家身前约 2 格的位置。
     * <p>
     * 注意：射线什么都没打到时，原版给的不是 null，而是 {@link HitResult.Type#MISS}
     * 的 {@link BlockHitResult}，它的位置在<b>最大触及距离的射线末端</b>
     * （见 {@code GameRenderer#filterHitResult}）。必须把它当成“没有指向方块”处理，
     * 否则会取到几格外（约 4.5 格以上）的位置。
     */
    private static BlockPos pickTarget(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockPos adjacent = hitPos.relative(blockHit.getDirection());
            if (isReplaceable(minecraft.level, adjacent)) {
                return adjacent.immutable();
            }
            if (isReplaceable(minecraft.level, hitPos)) {
                return hitPos.immutable();
            }
            return adjacent.immutable();
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        // 没有指向方块时：取指示器前方约 2 格的位置
        BlockPos pos = BlockPos.containing(eye.add(look.scale(2.0D)));
        return isReplaceable(minecraft.level, pos) ? pos.immutable() : null;
    }

    private static boolean isReplaceable(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /** 手上拿的是不是“可放置的实体方块”（半砖也允许）。 */
    private static boolean isPlaceableSolid(ItemStack stack, BlockGetter level, BlockPos pos) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || stack.isEmpty()) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.getBlock() instanceof SlabBlock || state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * 第一个点落在“上半位置”还是“下半位置”：上半砖还是下半砖由它决定。
     * <p>
     * 顶面（放在方块上面）→ 下半砖；底面（放在天花板下面）→ 上半砖；
     * 侧面则看点击高度在方块里的位置（上半 → 上半砖，下半 → 下半砖）。
     */
    private static boolean isTopHalf(HitResult hit) {
        // MISS 的位置在射线末端，不能拿来判断上半/下半
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            Direction face = blockHit.getDirection();
            if (face == Direction.UP) {
                return false;
            }
            if (face == Direction.DOWN) {
                return true;
            }
            double fraction = blockHit.getLocation().y - Math.floor(blockHit.getLocation().y);
            return fraction >= 0.5D;
        }
        return false;
    }

    /** 统计背包 + 快捷栏里某种物品的数量。 */
    private static int countItem(Player player, Item item) {
        if (item == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }


    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        if (outline.isEmpty() && firstPoint == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // 先画被方块挡住的暗蓝描边（关深度测试，隔着方块也能看到）
        VertexConsumer behind = buffers.getBuffer(OUTLINE_BEHIND);
        for (ChainMining.OutlineEdge edge : outline) {
            renderEdge(poseStack, behind, edge, BEHIND_RED, BEHIND_GREEN, BEHIND_BLUE);
        }
        buffers.endBatch(OUTLINE_BEHIND);

        // 再画直接可见的亮蓝描边（正常深度测试，被挡住的像素不会覆盖）
        VertexConsumer front = buffers.getBuffer(OUTLINE_FRONT);
        for (ChainMining.OutlineEdge edge : outline) {
            renderEdge(poseStack, front, edge, FRONT_RED, FRONT_GREEN, FRONT_BLUE);
        }
        buffers.endBatch(OUTLINE_FRONT);

        // 第一个点：更粗的一点蓝色框
        if (firstPoint != null) {
            VertexConsumer box = buffers.getBuffer(FIRST_POINT_BOX);
            LevelRenderer.renderLineBox(poseStack, box, new AABB(firstPoint).inflate(BOX_INFLATE),
                    FRONT_RED, FRONT_GREEN, FRONT_BLUE, 1.0F);
            buffers.endBatch(FIRST_POINT_BOX);
        }

        // 指示器位置到实际规划位置之间的辅助线
        if (rawTarget != null && plannedPoint != null && !rawTarget.equals(plannedPoint)) {
            VertexConsumer helper = buffers.getBuffer(HELPER_LINE);
            renderLine(poseStack, helper, Vec3.atCenterOf(rawTarget), Vec3.atCenterOf(plannedPoint),
                    HELPER_RED, HELPER_GREEN, HELPER_BLUE);
            buffers.endBatch(HELPER_LINE);
        }

        poseStack.popPose();
    }

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

    private static void renderLine(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to,
            float red, float green, float blue) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        direction = direction.normalize();
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, (float) direction.x, (float) direction.y, (float) direction.z);
        consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, (float) direction.x, (float) direction.y, (float) direction.z);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        enabled = false;
        keyHeld = false;
        scrolledWhileHeld = false;
        resetPlan();
    }
}

