package com.prism.photbar;

import com.mojang.blaze3d.platform.InputConstants;
import com.prism.photbar.network.SwapHotbarPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = PHotbar.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = PHotbar.MODID, value = Dist.CLIENT)
public class PHotbarClient {
    /** 按键绑定分类（显示在“选项 -> 控制 -> 按键绑定”里的分类名）。 */
    public static final String KEY_CATEGORY = "key.categories." + PHotbar.MODID;
    /** 按键绑定的翻译键。 */
    public static final String KEY_SWAP_HOTBAR = "key." + PHotbar.MODID + ".swap_hotbar";

    /**
     * 交换快捷栏的快捷键。
     * <p>
     * 默认 X，玩家可以在“选项 -> 控制 -> 按键绑定”里改成任意键，
     * 绑定结果会保存到 {@code options.txt}（形如 {@code key_key.photbar.swap_hotbar:key.keyboard.x}）。
     */
    public static final KeyMapping SWAP_HOTBAR_KEY = new KeyMapping(
            KEY_SWAP_HOTBAR,
            KeyConflictContext.IN_GAME, // 只在游戏内生效，不会和 GUI 里的按键冲突
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY);

    public PHotbarClient(IEventBus modEventBus, ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 按键绑定必须在 mod 事件总线的 RegisterKeyMappingsEvent 中注册，否则不会出现在控制界面里
        modEventBus.addListener(PHotbarClient::onRegisterKeyMappings);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SWAP_HOTBAR_KEY);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        PHotbar.LOGGER.info("P-Hotbar client setup complete, swap key defaults to X");
    }

    /**
     * 每个客户端 tick 检查一次按键队列。
     * <p>
     * 用 {@link KeyMapping#consumeClick()} 而不是 {@code isDown()}：前者只在“按下”的那一次返回 true，
     * 长按不会连续触发交换。
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (SWAP_HOTBAR_KEY.consumeClick()) {
            trySwap(minecraft);
        }
    }

    private static void trySwap(Minecraft minecraft) {
        // 没有进入世界 / 打开了任意 GUI（背包、箱子、聊天）时都不处理
        if (minecraft.player == null || minecraft.getConnection() == null || minecraft.screen != null) {
            return;
        }
        if (minecraft.player.isSpectator()) {
            return;
        }

        // 交换是在服务端执行的，如果对面没装本模组（原版服务器、或没装这个模组的 NeoForge 服务器），
        // 发包会让 NeoForge 以“未知通道”为由断开连接，所以必须先确认通道可用。
        // 单人游戏/局域网主机、以及装了这个模组的服务器都会返回 true。
        if (!NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY, SwapHotbarPayload.TYPE.id())) {
            return;
        }

        // 真正的交换在服务端完成（物品栏是服务端权威数据），这里只发一个请求包
        PacketDistributor.sendToServer(SwapHotbarPayload.INSTANCE);
    }
}

