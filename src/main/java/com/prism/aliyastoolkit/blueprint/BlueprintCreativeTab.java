package com.prism.aliyastoolkit.blueprint;

import com.prism.aliyastoolkit.AliyasToolkit;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组专用创造模式分类：只放蓝图箱等本模组物品，避免和原版功能方块混在一起。
 */
public final class BlueprintCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AliyasToolkit.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLUEPRINTS = TABS.register("blueprints",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aliyastoolkit.blueprints"))
                    .icon(() -> new ItemStack(BlueprintBoxes.item(BlueprintTier.LAPIS).get()))
                    .displayItems((parameters, output) -> {
                        for (BlueprintTier tier : BlueprintTier.values()) {
                            output.accept(BlueprintBoxes.item(tier).get());
                        }
                    })
                    .build());

    private BlueprintCreativeTab() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
