package com.prism.aliyastoolkit;

import com.mojang.blaze3d.platform.InputConstants;
import com.prism.aliyastoolkit.client.ClientAutoRefill;
import com.prism.aliyastoolkit.client.WhitelistScreen;
import com.prism.aliyastoolkit.network.OpenWhitelistPayload;
import com.prism.aliyastoolkit.network.SwapHotbarPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = AliyasToolkit.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public class AliyasToolkitClient {
    /** 按键绑定分类（显示在“选项 -> 控制 -> 按键绑定”里的分类名）。 */
    public static final String KEY_CATEGORY = "key.categories." + AliyasToolkit.MODID;

    /**
     * 交换快捷栏的快捷键。
     * <p>
     * 默认 X，玩家可以在“选项 -> 控制 -> 按键绑定”里改成任意键，
     * 绑定结果会保存到 {@code options.txt}（形如 {@code key_key.aliyastoolkit.swap_hotbar:key.keyboard.x}）。
     */
    public static final KeyMapping SWAP_HOTBAR_KEY = new KeyMapping(
            "key." + AliyasToolkit.MODID + ".swap_hotbar",
            KeyConflictContext.IN_GAME, // 只在游戏内生效，不会和 GUI 里的按键冲突
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY);

    /**
     * 打开白名单配置界面的快捷键。
     * <p>
     * 默认 Alt + X（同样可以在控制界面里修改）。带修饰键的绑定不会和上面的单键 X 互相触发：
     * NeoForge 的按键查找会按当前按下的修饰键过滤。
     */
    public static final KeyMapping OPEN_WHITELIST_KEY = new KeyMapping(
            "key." + AliyasToolkit.MODID + ".open_whitelist",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY);

    public AliyasToolkitClient(IEventBus modEventBus, ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 按键绑定必须在 mod 事件总线的 RegisterKeyMappingsEvent 中注册，否则不会出现在控制界面里
        modEventBus.addListener(AliyasToolkitClient::onRegisterKeyMappings);
        // 容器菜单对应的界面也要注册，否则打开白名单界面时客户端不知道怎么画
        modEventBus.addListener(AliyasToolkitClient::onRegisterMenuScreens);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SWAP_HOTBAR_KEY);
        event.register(OPEN_WHITELIST_KEY);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(AliyasToolkit.WHITELIST_MENU.get(), WhitelistScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AliyasToolkit.LOGGER.info("Aliya's Toolkit client setup complete (swap: X, whitelist: Alt+X)");
    }

    /**
     * 每个客户端 tick 检查一次按键队列。
     * <p>
     * 用 {@link KeyMapping#consumeClick()} 而不是 {@code isDown()}：前者只在“按下”的那一次返回 true，
     * 长按不会连续触发。
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (OPEN_WHITELIST_KEY.consumeClick()) {
            tryOpenWhitelist(minecraft);
        }
        while (SWAP_HOTBAR_KEY.consumeClick()) {
            trySwap(minecraft);
        }
    }

    private static void trySwap(Minecraft minecraft) {
        if (!canSend(minecraft)) {
            return;
        }
        if (!Config.ENABLE_HOTBAR_SWAP.getAsBoolean()) {
            return;
        }
        if (!hasChannel(minecraft, SwapHotbarPayload.TYPE.id())) {
            return;
        }

        // 真正的交换在服务端完成（物品栏是服务端权威数据），这里只发一个请求包
        PacketDistributor.sendToServer(SwapHotbarPayload.INSTANCE);

        // 交换会让一些格子变空，这不属于“物品消失”，所以要抑制自动补充
        ClientAutoRefill.suppressAfterSwap();
    }

    private static void tryOpenWhitelist(Minecraft minecraft) {
        if (!canSend(minecraft) || minecraft.player.isSpectator()) {
            return;
        }
        if (!hasChannel(minecraft, OpenWhitelistPayload.TYPE.id())) {
            return;
        }

        // 界面由服务端创建（容器菜单），客户端会自动打开对应的 Screen
        PacketDistributor.sendToServer(OpenWhitelistPayload.INSTANCE);
    }

    /**
     * 基础可用性检查：进入了世界、没有打开任何 GUI、还活着。
     */
    private static boolean canSend(Minecraft minecraft) {
        return minecraft.player != null
                && minecraft.getConnection() != null
                && minecraft.screen == null
                && minecraft.player.isAlive()
                && !minecraft.player.isDeadOrDying();
    }

    /**
     * 对面是否支持这个通道。
     * <p>
     * 单人游戏/局域网主机、以及装了本模组的服务器都会返回 true；原版服务器或没装本模组的服务器返回 false，
     * 此时不发包，避免 NeoForge 以“未知通道”为由断开连接。
     */
    private static boolean hasChannel(Minecraft minecraft, ResourceLocation payloadId) {
        return NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY, payloadId);
    }
}

