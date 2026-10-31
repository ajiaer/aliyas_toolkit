package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.menu.WhitelistMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：打开白名单配置界面。
 * <p>
 * 菜单必须在服务端创建（容器界面需要服务端发放 containerId），所以不能直接在客户端 setScreen。
 * 菜单里的内容来自这名玩家自己的白名单数据（Data Attachment）。
 */
public record OpenWhitelistPayload() implements CustomPacketPayload {
    public static final OpenWhitelistPayload INSTANCE = new OpenWhitelistPayload();

    public static final Type<OpenWhitelistPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "open_whitelist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWhitelistPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenWhitelistPayload> type() {
        return TYPE;
    }

    public static void handle(OpenWhitelistPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> WhitelistMenu.fromData(containerId, inventory, p.getData(AliyasToolkit.WHITELIST)),
                    Component.translatable("gui.aliyastoolkit.whitelist.title")));
        });
    }
}
