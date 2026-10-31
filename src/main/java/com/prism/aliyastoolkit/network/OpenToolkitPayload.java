package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.menu.ToolkitMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：打开“Aliya's Toolkit 配置”界面。
 * <p>
 * 菜单必须在服务端创建（容器界面需要服务端发放 containerId），所以不能直接在客户端 setScreen。
 * 界面左侧是这名玩家自己的物品白名单，右侧是连锁破坏/自动补充等设置。
 */
public record OpenToolkitPayload() implements CustomPacketPayload {
    public static final OpenToolkitPayload INSTANCE = new OpenToolkitPayload();

    public static final Type<OpenToolkitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "open_toolkit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenToolkitPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenToolkitPayload> type() {
        return TYPE;
    }

    public static void handle(OpenToolkitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> ToolkitMenu.fromData(containerId, inventory, p.getData(AliyasToolkit.WHITELIST)),
                    Component.translatable("gui.aliyastoolkit.toolkit.title")));
        });
    }
}
