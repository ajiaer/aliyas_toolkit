package com.prism.aliyastoolkit.client;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.network.OpenBlueprintBoxPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import org.lwjgl.glfw.GLFW;

/**
 * 主手蓝图箱 + Alt + 右键：打开蓝图箱界面。
 * <p>
 * 界面由服务端创建，和右键世界里的蓝图箱方块使用同一个 {@code BlueprintBoxMenu} /
 * {@code BlueprintBoxScreen}，只是后端换成物品栏里的蓝图箱物品。
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public final class BlueprintBoxClient {
    private BlueprintBoxClient() {
    }

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        if (!matchesMouse(AliyasToolkitClient.BLUEPRINT_OPEN_BOX_KEY, event.getButton())) {
            return;
        }
        if (!(minecraft.player.getMainHandItem().getItem() instanceof BlueprintBoxItem)) {
            return;
        }
        if (!hasChannel(minecraft, OpenBlueprintBoxPayload.TYPE.id())) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToServer(new OpenBlueprintBoxPayload(minecraft.player.getInventory().selected));
        minecraft.player.swing(InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        if (!matchesKey(AliyasToolkitClient.BLUEPRINT_OPEN_BOX_KEY, event)) {
            return;
        }
        if (!(minecraft.player.getMainHandItem().getItem() instanceof BlueprintBoxItem)) {
            return;
        }
        if (!hasChannel(minecraft, OpenBlueprintBoxPayload.TYPE.id())) {
            return;
        }
        PacketDistributor.sendToServer(new OpenBlueprintBoxPayload(minecraft.player.getInventory().selected));
        minecraft.player.swing(InteractionHand.MAIN_HAND);
    }

    /** 兜底：用键盘“使用”键触发时也不要放置方块。 */
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!(minecraft.player.getMainHandItem().getItem() instanceof BlueprintBoxItem)) {
            return;
        }
        if (AliyasToolkitClient.BLUEPRINT_OPEN_BOX_KEY.isDown()) {
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        while (AliyasToolkitClient.BLUEPRINT_OPEN_BOX_KEY.consumeClick()) {
        }
    }

    private static boolean matchesMouse(KeyMapping mapping, int button) {
        return mapping.matchesMouse(button)
                && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }

    private static boolean matchesKey(KeyMapping mapping, InputEvent.Key event) {
        return mapping.matches(event.getKey(), event.getScanCode())
                && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }

    private static boolean hasChannel(Minecraft minecraft, net.minecraft.resources.ResourceLocation id) {
        return NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(),
                ConnectionProtocol.PLAY, id);
    }
}
