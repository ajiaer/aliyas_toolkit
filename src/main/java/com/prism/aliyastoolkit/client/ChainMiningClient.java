package com.prism.aliyastoolkit.client;

import java.util.List;
import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.ChainMining;
import com.prism.aliyastoolkit.ChainPreset;
import com.prism.aliyastoolkit.Config;
import com.prism.aliyastoolkit.ToolkitSettings;
import com.prism.aliyastoolkit.ToolkitSettingsCache;
import com.prism.aliyastoolkit.network.CyclePresetPayload;
import com.prism.aliyastoolkit.network.SetChainStatePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端的连锁破坏表现：
 * <ul>
 * <li>处理连锁快捷键（固定为切换：按一下开、再按一下关），并把“当前是否激活”同步给服务端</li>
 * <li>激活时计算准星指向方块的连锁列表，并沿着连锁区域的外表面轮廓画描边
 * （只画 90°/270° 转角线，平直的 180° 棱不画）：
 * 直接看得见的部分是白色，被方块挡住的部分是暗一点的白色，方便一眼分辨前后</li>
 * <li>指向的方块会替换掉原版黑框，改用纯白、更粗的一圈框，避免黑线压在白线上显得发暗</li>
 * </ul>
 * 真正破坏方块在服务端完成（见 {@link ChainMining}），这里只负责按键和显示。
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public final class ChainMiningClient {
    /** 被遮挡部分的描边颜色（偏暗的白）。 */
    private static final float BEHIND_RED = 0.6F;
    private static final float BEHIND_GREEN = 0.6F;
    private static final float BEHIND_BLUE = 0.6F;

    /**
     * 被遮挡部分的描边：关闭深度测试，所以隔着方块也能看到（先画这一层）。
     */
    private static final RenderType CHAIN_OUTLINE_BEHIND = createOutlineType(
            "aliyastoolkit_chain_outline_behind", RenderStateShard.NO_DEPTH_TEST, 2.0D);

    /**
     * 可以直接看到的部分的描边：正常深度测试，被方块挡住的部分不会画到（后画，盖住上面那一层）。
     */
    private static final RenderType CHAIN_OUTLINE_FRONT = createOutlineType(
            "aliyastoolkit_chain_outline_front", RenderStateShard.LEQUAL_DEPTH_TEST, 2.0D);

    /**
     * 准星指向的那个方块专用的框：纯白、更粗，会替换原版的黑框。
     */
    private static final RenderType CHAIN_OUTLINE_TARGET = createOutlineType(
            "aliyastoolkit_chain_outline_target", RenderStateShard.LEQUAL_DEPTH_TEST, 5.0D);

    /** 指向方块框向外扩一点，避免和方块表面完全重合。 */
    private static final double TARGET_BOX_INFLATE = 0.002D;

    private static RenderType createOutlineType(String name, RenderStateShard.DepthTestStateShard depthTest,
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

    /** 当前要显示的白边线段（由连锁方块外表面的转角棱组成）。 */
    private static List<ChainMining.OutlineEdge> preview = List.of();

    /** 准星当前指向的方块（连锁激活时用于替换原版黑框）。 */
    private static BlockPos targetPos;

    /** “切换”模式下是否已经打开连锁。 */
    private static boolean toggled;

    /** 连锁键当前是否处于“按下还没松开”的状态（由原始按键事件维护）。 */
    private static boolean chainKeyHeld;

    /** 这一次按住期间是否滚动过滚轮（滚动 = 在切预设，松开时就不再切换开关）。 */
    private static boolean scrolledWhileHeld;

    /** 当前是否处于连锁激活状态。 */
    private static boolean active;

    /** 上一次发给服务端的状态，避免每 tick 都发包。 */
    private static boolean lastSentActive;

    private ChainMiningClient() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.getConnection() == null) {
            // 加载/换维度时先不画白边；这里不要清空按键和同步状态，
            // 否则会出现“客户端以为关了、服务端还开着”的失步
            preview = List.of();
            targetPos = null;
            return;
        }

        // 连锁键的“按下/松开”在 onKeyInput / onMouseButton 里按原始事件处理（真正的 keydown / keyup）。
        // 原版 KeyMapping.consumeClick() 在按住时会随按键重复（GLFW_REPEAT）反复返回 true，
        // 用它无法区分“短按”和“按住 + 滚轮”，所以这里只把点击队列清空，避免残留。
        while (AliyasToolkitClient.CHAIN_MINE_KEY.consumeClick()) {
        }

        ToolkitSettings settings = ToolkitSettingsCache.get();
        boolean usable = Config.ENABLE_CHAIN_MINING.getAsBoolean()
                && minecraft.screen == null
                && player.isAlive()
                && !player.isDeadOrDying()
                && !player.isSpectator();

        if (!usable) {
            active = false;
            chainKeyHeld = false;
            scrolledWhileHeld = false;
        } else {
            active = toggled;
        }

        syncActive(minecraft);

        // 计算白边预览：以准星指向的方块为中心（原版的拾取已经按挖掘/交互距离算好了）
        if (!active || !(minecraft.hitResult instanceof BlockHitResult hitResult)) {
            preview = List.of();
            targetPos = null;
            return;
        }
        BlockPos origin = hitResult.getBlockPos();
        BlockState originState = minecraft.level.getBlockState(origin);
        // 空气和液体方块（水、岩浆等）无法破坏：不画目标框，也不画连锁预览
        if (originState.isAir() || ChainMining.isFluidBlock(originState)) {
            preview = List.of();
            targetPos = null;
            return;
        }

        // 记住指向的方块：渲染时会用它替换原版的黑框
        targetPos = origin.immutable();
        preview = ChainMining.collectOutlineEdges(
                ChainMining.collectChain(minecraft.level, origin, originState, settings, player));
    }

    /**
     * 连锁激活且有方块指向时，取消原版的黑框，改由我们自己画纯白的框
     * （原版黑框在描边之后绘制，会盖住白线，让指向的方块看起来发暗）。
     */
    @SubscribeEvent
    static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (!active || !Config.ENABLE_CHAIN_MINING.getAsBoolean() || targetPos == null) {
            return;
        }
        if (!event.getTarget().getBlockPos().equals(targetPos)) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * 连锁键的 keydown / keyup（键盘）。
     * <p>
     * 原版的 {@link KeyMapping#consumeClick()} 在按住时会随按键重复（GLFW_REPEAT）不断返回 true，
     * 没法区分“短按”和“按住”；这里直接监听原始按键事件：
     * <ul>
     * <li>keydown：只记录开始按住，并清空“这一按期间滚过滚轮”的标记；</li>
     * <li>keyup：这一按期间没滚过滚轮才切换连锁开关；滚过滚轮（= 在切预设）就不切换。</li>
     * </ul>
     */
    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        KeyMapping chainKey = AliyasToolkitClient.CHAIN_MINE_KEY;
        if (!chainKey.matches(event.getKey(), event.getScanCode())) {
            return;
        }

        if (event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft minecraft = Minecraft.getInstance();
            // isDown() 会同时检查改键后的修饰键与按键冲突上下文
            onChainKeyDown(minecraft.screen == null && chainKey.isDown());
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            onChainKeyUp(Minecraft.getInstance());
        }
        // GLFW_REPEAT 忽略：按住不放不应该算作新的按下
    }

    /** 连锁键被改到鼠标按键时（玩家自己改键），同样按 keydown / keyup 处理。 */
    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!AliyasToolkitClient.CHAIN_MINE_KEY.matchesMouse(event.getButton())) {
            return;
        }

        if (event.getAction() == GLFW.GLFW_PRESS) {
            onChainKeyDown(Minecraft.getInstance().screen == null);
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            onChainKeyUp(Minecraft.getInstance());
        }
    }

    /** keydown：开始一次“按住”，并清空滚轮标记。 */
    private static void onChainKeyDown(boolean active) {
        chainKeyHeld = active;
        scrolledWhileHeld = false;
    }

    /** keyup：这次按住期间没滚过滚轮才切换连锁开关。 */
    private static void onChainKeyUp(Minecraft minecraft) {
        boolean toggle = chainKeyHeld && !scrolledWhileHeld;
        chainKeyHeld = false;
        scrolledWhileHeld = false;
        if (toggle) {
            toggleChain(minecraft);
        }
    }

    /** 切换连锁开关（只在游戏内、功能可用时生效）。 */
    private static void toggleChain(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }
        if (!Config.ENABLE_CHAIN_MINING.getAsBoolean()
                || !player.isAlive()
                || player.isDeadOrDying()
                || player.isSpectator()) {
            return;
        }

        toggled = !toggled;
        if (toggled) {
            // 与其它互斥模式（快速建造等）最多同时开启一个
            ExclusiveModes.activate(ExclusiveModes.CHAIN_MINING);
        } else {
            ExclusiveModes.deactivate(ExclusiveModes.CHAIN_MINING);
        }
        player.displayClientMessage(Component.translatable(
                toggled ? "message.aliyastoolkit.chain_on" : "message.aliyastoolkit.chain_off"), true);
    }

    /** 由互斥模式管理器调用：强制关闭连锁，不会再反过来关闭别人。 */
    public static void forceDisable() {
        toggled = false;
        chainKeyHeld = false;
        scrolledWhileHeld = false;
        active = false;
        ExclusiveModes.deactivate(ExclusiveModes.CHAIN_MINING);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            syncActive(minecraft);
        }
    }

    /**
     * 按住连锁快捷键（默认 Z）时，用滚轮切换大量方块的预设：
     * 向下滚 → 下一个预设，向上滚 → 上一个预设；到两端就停住，不会循环（并阻止滚轮顺带切换快捷栏）。
     * <p>
     * 这一次按住期间只要滚动过滚轮，就认为玩家是在“长按切预设”，松开时不再切换连锁开关。
     */
    @SubscribeEvent
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!Config.ENABLE_CHAIN_MINING.getAsBoolean()) {
            return;
        }
        if (!AliyasToolkitClient.CHAIN_MINE_KEY.isDown()) {
            return;
        }
        // 只有连锁破坏已经开启时才允许用滚轮切换预设
        if (!toggled) {
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

    /**
     * 切换预设：客户端先更新本地缓存（形状预览/提示立刻生效），再把请求发给服务端保存。
     */
    private static void switchPreset(Minecraft minecraft, int direction) {
        ToolkitSettings settings = ToolkitSettingsCache.get();
        int size = settings.presets().size();
        if (size > 1) {
            int next = CyclePresetPayload.nextIndex(settings.selectedPreset(), size, direction);
            if (next != settings.selectedPreset()) {
                settings = settings.withSelectedPreset(next);
                ToolkitSettingsCache.set(settings);
                if (NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY,
                        CyclePresetPayload.TYPE.id())) {
                    PacketDistributor.sendToServer(new CyclePresetPayload(direction));
                }
            }
        }
        // 用原版动作栏文字提示（一段时间后自动淡出）
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(presetMessage(settings), true);
        }
    }

    /**
     * 组装预设提示文字（名称 + 编号 + 形状/层数/锁定方向）。
     */
    private static Component presetMessage(ToolkitSettings settings) {
        ChainPreset preset = settings.preset();
        return Component.translatable("message.aliyastoolkit.preset",
                preset.name(), settings.selectedPreset() + 1, settings.presets().size(),
                preset.up(), preset.down(), preset.left(), preset.right(), preset.length(),
                Component.translatable(preset.lockDirection()
                        ? "gui.aliyastoolkit.on"
                        : "gui.aliyastoolkit.off"));
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || (preview.isEmpty() && targetPos == null)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // 先画“被方块挡住”的暗色描边：关掉深度测试，所以隔着方块也能看到
        VertexConsumer behind = buffers.getBuffer(CHAIN_OUTLINE_BEHIND);
        for (ChainMining.OutlineEdge edge : preview) {
            renderEdge(poseStack, behind, edge, BEHIND_RED, BEHIND_GREEN, BEHIND_BLUE);
        }
        buffers.endBatch(CHAIN_OUTLINE_BEHIND);

        // 再画“直接能看到”的白色描边：正常深度测试，被挡住的像素不会覆盖，
        // 于是可见部分保持白色，被遮挡部分保留上面那层暗色。
        VertexConsumer front = buffers.getBuffer(CHAIN_OUTLINE_FRONT);
        for (ChainMining.OutlineEdge edge : preview) {
            renderEdge(poseStack, front, edge, 1.0F, 1.0F, 1.0F);
        }
        buffers.endBatch(CHAIN_OUTLINE_FRONT);

        // 指向的方块：纯白、更粗的一圈框（原版黑框已在 RenderHighlightEvent 里取消）
        if (targetPos != null) {
            VertexConsumer target = buffers.getBuffer(CHAIN_OUTLINE_TARGET);
            LevelRenderer.renderLineBox(poseStack, target, new AABB(targetPos).inflate(TARGET_BOX_INFLATE),
                    1.0F, 1.0F, 1.0F, 1.0F);
            buffers.endBatch(CHAIN_OUTLINE_TARGET);
        }

        poseStack.popPose();
    }

    /**
     * 画一条 1 格长的描边线段。
     */
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

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ToolkitSettingsCache.reset();
    }

    private static void syncActive(Minecraft minecraft) {
        if (active == lastSentActive) {
            return;
        }
        lastSentActive = active;
        if (NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY,
                SetChainStatePayload.TYPE.id())) {
            PacketDistributor.sendToServer(new SetChainStatePayload(active));
        }
    }

    private static void reset() {
        preview = List.of();
        targetPos = null;
        active = false;
        lastSentActive = false;
        toggled = false;
        chainKeyHeld = false;
        scrolledWhileHeld = false;
    }

}
