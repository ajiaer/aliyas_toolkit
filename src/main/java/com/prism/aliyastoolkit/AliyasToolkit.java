package com.prism.aliyastoolkit;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.prism.aliyastoolkit.menu.ToolkitMenu;
import com.prism.aliyastoolkit.network.ToolkitNetwork;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Aliya's Toolkit 主类（双端通用）。
 * <p>
 * 模组内容：
 * <ul>
 * <li>快捷栏与背包最后一行交换（快捷键，默认 X）</li>
 * <li>快捷栏物品消失后，从背包自动补充白名单物品</li>
 * <li>连锁破坏（快捷键，默认 Z）：按住/切换后用准星指向方块，挖一个带走附近相同的方块</li>
 * <li>“Aliya's Toolkit 配置”界面（快捷键 Alt + X）：左侧物品白名单，右侧连锁破坏/自动补充设置</li>
 * </ul>
 * 功能开关在模组配置里（{@link Config}），各项功能的具体参数按玩家保存在 {@link ToolkitSettings}。
 */
@Mod(AliyasToolkit.MODID)
public class AliyasToolkit {
    /** 模组 id，必须与 META-INF/neoforge.mods.toml 中的 modId 一致。 */
    public static final String MODID = "aliyastoolkit";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 容器菜单类型（“Aliya's Toolkit 配置”界面）。 */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);

    /** 玩家数据附件（per-player data）。 */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    /** “Aliya's Toolkit 配置”界面的菜单类型。 */
    public static final DeferredHolder<MenuType<?>, MenuType<ToolkitMenu>> TOOLKIT_MENU = MENU_TYPES.register("toolkit",
            () -> IMenuTypeExtension.create((containerId, inventory, data) -> new ToolkitMenu(containerId, inventory)));

    /**
     * 每个玩家自己的物品白名单。
     * <p>
     * 绑定在玩家数据上：每名玩家独立一份，随玩家存档保存，死亡重生后不会丢失。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WhitelistData>> WHITELIST = ATTACHMENT_TYPES.register("whitelist",
            () -> AttachmentType.builder(WhitelistData::new)
                    .serialize(WhitelistData.CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * 每个玩家自己的功能设置（连锁触发方式、连锁数量、补充物品是否完全一致）。
     * <p>
     * 同样绑定在玩家数据上，随玩家存档保存；登录时由服务端同步给客户端。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ToolkitSettings>> SETTINGS = ATTACHMENT_TYPES.register("settings",
            () -> AttachmentType.builder(() -> ToolkitSettings.DEFAULT)
                    .serialize(ToolkitSettings.CODEC)
                    .copyOnDeath()
                    .build());

    public AliyasToolkit(IEventBus modEventBus, ModContainer modContainer) {
        // 通用初始化
        modEventBus.addListener(this::commonSetup);

        // 注册菜单类型与玩家数据附件
        MENU_TYPES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);

        // 注册网络包（快捷栏交换、配置界面及设置同步、连锁状态、自动补充）
        modEventBus.addListener(ToolkitNetwork::register);

        // 注册配置（模组配置界面里可以改的开关）
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Aliya's Toolkit common setup complete");
    }
}
