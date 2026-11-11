package com.prism.aliyastoolkit;

import com.mojang.blaze3d.platform.InputConstants;
import com.prism.aliyastoolkit.client.ChainMiningClient;
import com.prism.aliyastoolkit.client.BlueprintBoxItemRenderer;
import com.prism.aliyastoolkit.client.BlueprintBoxRenderer;
import com.prism.aliyastoolkit.client.BlueprintBoxScreen;
import com.prism.aliyastoolkit.client.ClientAutoRefill;
import com.prism.aliyastoolkit.client.CopyPasteClient;
import com.prism.aliyastoolkit.client.ExclusiveModes;
import com.prism.aliyastoolkit.client.OrderedKeyMapping;
import com.prism.aliyastoolkit.client.QuickBuildClient;
import com.prism.aliyastoolkit.client.ToolkitScreen;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxes;
import com.prism.aliyastoolkit.network.OpenToolkitPayload;
import com.prism.aliyastoolkit.network.SwapHotbarPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
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
    /**
     * 物品栏/掉落物也使用蓝图箱自己的 ChestRenderer，保证和世界中方块一致。
     * 自定义渲染器延迟创建，避免在客户端资源还没初始化时访问 Minecraft。
     */
    private static final IClientItemExtensions BLUEPRINT_BOX_ITEM_EXTENSIONS = new IClientItemExtensions() {
        private BlueprintBoxItemRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (this.renderer == null) {
                this.renderer = new BlueprintBoxItemRenderer();
            }
            return this.renderer;
        }
    };

    /** 按键绑定分类（显示在“选项 -> 控制 -> 按键绑定”里的分类名）。 */
    public static final String KEY_CATEGORY = "key.categories." + AliyasToolkit.MODID;

    /**
     * 交换快捷栏的快捷键。
     * <p>
     * 默认 X，玩家可以在“选项 -> 控制 -> 按键绑定”里改成任意键，
     * 绑定结果会保存到 {@code options.txt}（形如 {@code key_key.aliyastoolkit.swap_hotbar:key.keyboard.x}）。
     */
    public static final KeyMapping SWAP_HOTBAR_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".swap_hotbar",
            KeyConflictContext.IN_GAME, // 只在游戏内生效，不会和 GUI 里的按键冲突
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY, 1);

    /**
     * 打开“Aliya's Toolkit 配置”界面的快捷键。
     * <p>
     * 默认 Alt + X（同样可以在控制界面里修改）。带修饰键的绑定不会和上面的单键 X 互相触发：
     * NeoForge 的按键查找会按当前按下的修饰键过滤。
     */
    public static final KeyMapping OPEN_TOOLKIT_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".open_toolkit",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY, 0);

    /**
     * 连锁破坏的快捷键。
     * <p>
     * 默认 Z。固定为“切换”：按一下开启，再按一下关闭；按住它滚动滚轮可以切换“大量方块”预设。
     * 键位可以在“选项 -> 控制 -> 按键绑定”里修改。
     */
    public static final KeyMapping CHAIN_MINE_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".chain_mine",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY, 2);

    /**
     * 快速建造的快捷键。


     * <p>
     * 默认 C。固定为“切换”：按一下开启，再按一下关闭；按住它滚动滚轮可以切换快速建造预设。
     * 开启快速建造会自动关闭连锁破坏（两者最多同时开启一个）。
     */
    public static final KeyMapping QUICK_BUILD_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".quick_build",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            KEY_CATEGORY, 3);

    /**
     * 主手拿着蓝图箱时，Alt + 右键打开蓝图箱界面（等同于右键世界里的蓝图箱方块）。
     * <p>
     * 所有蓝图相关快捷键都可以在“选项 -> 控制 -> 按键绑定”里修改。
     */
    public static final KeyMapping BLUEPRINT_OPEN_BOX_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".blueprint_open_box",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            KEY_CATEGORY, 4);

    /**
     * 蓝图建造主按键：手持蓝图箱时 Ctrl + 右键进入蓝图建造；
     * 绘制模式下只记录蓝图并退出（复制），不拿取源方块和材料；
     * 建造模式下（固定虚影后）检查材料并建造。
     */
    public static final KeyMapping BLUEPRINT_BUILD_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".blueprint_build",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            KEY_CATEGORY, 5);

    /**
     * 蓝图绘制：记录结构并把材料收进蓝图箱，同时清除源方块（剪切）。
     * 默认 Ctrl + 左键。
     */
    public static final KeyMapping BLUEPRINT_CUT_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".blueprint_cut",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            KEY_CATEGORY, 6);

    /** 蓝图建造：在没有物品的蓝图箱上切换绘制 / 建造模式。 */
    public static final KeyMapping BLUEPRINT_MODE_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".blueprint_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            KEY_CATEGORY, 7);

    /** 蓝图建造：旋转建造虚影。 */
    public static final KeyMapping BLUEPRINT_ROTATE_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".blueprint_rotate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY, 9);

    /** 蓝图绘制：按住时把选点吸附到指向方块的外侧（快速建造选点位置）。 */
    public static final KeyMapping BLUEPRINT_SNAP_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".blueprint_snap",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            KEY_CATEGORY, 8);

    /**
     * 退出当前工具模式：连锁破坏、快速建造、蓝图建造都可以用这个键退出。
     * 默认 `（反引号），可改键。
     */
    public static final KeyMapping TOOLKIT_EXIT_KEY = new OrderedKeyMapping(
            "key." + AliyasToolkit.MODID + ".blueprint_exit",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            KEY_CATEGORY, 10);

    public AliyasToolkitClient(IEventBus modEventBus, ModContainer container) {
        // 互斥模式：连锁破坏、快速建造、剪切复制同一时间最多开启一个
        ExclusiveModes.register(ExclusiveModes.CHAIN_MINING, ChainMiningClient::forceDisable);
        ExclusiveModes.register(ExclusiveModes.QUICK_BUILD, QuickBuildClient::forceDisable);
        ExclusiveModes.register(ExclusiveModes.COPY_PASTE, CopyPasteClient::forceDisable);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 按键绑定必须在 mod 事件总线的 RegisterKeyMappingsEvent 中注册，否则不会出现在控制界面里
        modEventBus.addListener(AliyasToolkitClient::onRegisterKeyMappings);
        // 容器菜单对应的界面也要注册，否则打开配置界面时客户端不知道怎么画
        modEventBus.addListener(AliyasToolkitClient::onRegisterMenuScreens);
        // 蓝图箱使用原版 ChestRenderer 的箱子模型和翻盖动画
        modEventBus.addListener(AliyasToolkitClient::onRegisterRenderers);
        // 物品栏/掉落物使用同一个箱子渲染器
        modEventBus.addListener(AliyasToolkitClient::onRegisterClientExtensions);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TOOLKIT_KEY);
        event.register(SWAP_HOTBAR_KEY);
        event.register(CHAIN_MINE_KEY);
        event.register(QUICK_BUILD_KEY);
        event.register(BLUEPRINT_OPEN_BOX_KEY);
        event.register(BLUEPRINT_BUILD_KEY);
        event.register(BLUEPRINT_CUT_KEY);
        event.register(BLUEPRINT_MODE_KEY);
        event.register(BLUEPRINT_SNAP_KEY);
        event.register(BLUEPRINT_ROTATE_KEY);
        event.register(TOOLKIT_EXIT_KEY);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(AliyasToolkit.TOOLKIT_MENU.get(), ToolkitScreen::new);
        event.register(AliyasToolkit.BLUEPRINT_BOX_MENU.get(), BlueprintBoxScreen::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlueprintBoxes.BLUEPRINT_BOX_BE.get(), BlueprintBoxRenderer::new);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (var item : BlueprintBoxes.items()) {
            event.registerItem(BLUEPRINT_BOX_ITEM_EXTENSIONS, item.get());
        }
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AliyasToolkit.LOGGER.info(
                "Aliya's Toolkit client setup complete (swap: X, chain mining: Z, quick build: C, blueprint: Ctrl+LMB cut / Ctrl+RMB copy or build / Alt+RMB, exit modes: backtick, toolkit screen: Alt+X)");
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

        while (TOOLKIT_EXIT_KEY.consumeClick()) {
            if (minecraft.screen != null || minecraft.player == null) {
                continue;
            }
            boolean chainActive = ChainMiningClient.isActive();
            boolean buildEnabled = QuickBuildClient.isEnabled();
            boolean blueprintActive = CopyPasteClient.isActive();
            if (!chainActive && !buildEnabled && !blueprintActive) {
                continue;
            }
            if (chainActive) {
                ChainMiningClient.forceDisable();
            }
            if (buildEnabled) {
                QuickBuildClient.forceDisable();
            }
            if (blueprintActive) {
                CopyPasteClient.forceDisable();
            }
            minecraft.player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.mode_exit"), true);
        }

        while (OPEN_TOOLKIT_KEY.consumeClick()) {
            tryOpenToolkit(minecraft);
        }
        while (SWAP_HOTBAR_KEY.consumeClick()) {
            trySwap(minecraft);
        }
        // 连锁快捷键由 ChainMiningClient 处理（固定切换模式，并且要同步给服务端）
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

    private static void tryOpenToolkit(Minecraft minecraft) {
        if (!canSend(minecraft) || minecraft.player.isSpectator()) {
            return;
        }
        if (!hasChannel(minecraft, OpenToolkitPayload.TYPE.id())) {
            return;
        }

        // 界面由服务端创建（容器菜单），客户端会自动打开对应的 Screen
        PacketDistributor.sendToServer(OpenToolkitPayload.INSTANCE);
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

