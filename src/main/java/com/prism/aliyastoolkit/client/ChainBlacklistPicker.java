package com.prism.aliyastoolkit.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.network.AddChainBlacklistPayload;
import com.prism.aliyastoolkit.network.OpenToolkitPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * 连锁破坏黑名单的“添加”模式：
 * <ul>
 * <li>在设置界面点“添加”后暂时关闭界面并进入这个模式</li>
 * <li>准星指向的方块画一圈纯白粗框（和连锁破坏指向方块的框一致，并替换掉原版黑框）</li>
 * <li>左键点击方块 → 把它加入黑名单，然后重新打开设置界面</li>
 * <li>按返回键（默认 `，与退出工具模式同一个键）→ 不添加，直接重新打开设置界面</li>
 * </ul>
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public final class ChainBlacklistPicker {
    /** 指向方块的框宽度（与 {@link ChainMiningClient} 的目标框一致）。 */
    private static final float TARGET_LINE_WIDTH = 0.020F;

    private static boolean picking;
    private static BlockPos targetPos;
    /** 重新打开设置界面时是否直接显示“黑名单”页面（消费一次）。 */
    private static boolean reopenBlacklistView;

    private ChainBlacklistPicker() {
    }

    /** 进入“添加”模式（由设置界面的“添加”按钮调用，调用后界面会被关闭）。 */
    public static void start() {
        Minecraft minecraft = Minecraft.getInstance();
        picking = true;
        targetPos = null;
        reopenBlacklistView = true;
        LocalPlayer player = minecraft.player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.aliyastoolkit.chain_blacklist.pick",
                    AliyasToolkitClient.TOOLKIT_EXIT_KEY.getTranslatedKeyMessage()), true);
        }
    }

    public static boolean isPicking() {
        return picking;
    }

    /** 直接结束“添加”模式（不重新打开界面），例如设置界面被重新打开时。 */
    public static void cancel() {
        stop(false);
    }

    /** 设置界面重新打开时读一次：是否直接显示黑名单页面。 */
    public static boolean consumeReopenBlacklistView() {
        boolean value = reopenBlacklistView;
        reopenBlacklistView = false;
        return value;
    }

    /** 结束“添加”模式；{@code reopen} 为 true 时重新打开设置界面。 */
    private static void stop(boolean reopen) {
        picking = false;
        targetPos = null;
        if (!reopen) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            // 设置界面必须由服务端创建（要发放 containerId），所以走这个包重新打开
            PacketDistributor.sendToServer(OpenToolkitPayload.INSTANCE);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        if (!picking) {
            return;
        }
        // 返回键：取消选择并回到设置界面
        while (AliyasToolkitClient.TOOLKIT_EXIT_KEY.consumeClick()) {
            stop(true);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            targetPos = null;
            return;
        }
        if (minecraft.hitResult instanceof BlockHitResult hitResult) {
            BlockState state = minecraft.level.getBlockState(hitResult.getBlockPos());
            // 空气和没有方块物品的方块（水、岩浆等）不能加入黑名单，也不画框
            targetPos = state.getBlock().asItem() == Items.AIR ? null : hitResult.getBlockPos().immutable();
        } else {
            targetPos = null;
        }
    }

    /** 左键方块：加入黑名单并回到设置界面（同时阻止这次左键去挖方块）。 */
    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!picking || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.level == null) {
            return;
        }
        event.setCanceled(true);
        if (targetPos == null) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(targetPos);
        Item item = state.getBlock().asItem();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (item == Items.AIR || id == null) {
            return;
        }
        PacketDistributor.sendToServer(new AddChainBlacklistPayload(id));
        LocalPlayer player = minecraft.player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.aliyastoolkit.chain_blacklist.added",
                    new ItemStack(item).getHoverName()), true);
        }
        stop(true);
    }

    /** 用自己画的白色粗框代替原版黑框，避免黑线把白线压暗。 */
    @SubscribeEvent
    static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (!picking || targetPos == null) {
            return;
        }
        if (!event.getTarget().getBlockPos().equals(targetPos)) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || !picking || targetPos == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        try (ByteBufferBuilder lineBuffer = new ByteBufferBuilder(1536)) {
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(lineBuffer);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer target = buffers.getBuffer(ThickOutlineRenderer.type());
            ThickOutlineRenderer.renderBox(poseStack, target,
                    new AABB(targetPos).inflate(ThickOutlineRenderer.BOX_INFLATE),
                    TARGET_LINE_WIDTH, 1.0F, 1.0F, 1.0F, 1.0F);
            buffers.endBatch(ThickOutlineRenderer.type());
            poseStack.popPose();
        }
    }
}
