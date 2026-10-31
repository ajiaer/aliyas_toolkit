package com.prism.photbar.network;

import com.prism.photbar.PHotbar;
import com.prism.photbar.WhitelistData;
import com.prism.photbar.menu.WhitelistMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：打开白名单配置界面。
 * <p>
 * 菜单必须在服务端创建（容器界面需要服务端发放 containerId），所以不能直接在客户端 setScreen。
 */
public record OpenWhitelistPayload() implements CustomPacketPayload {
    public static final OpenWhitelistPayload INSTANCE = new OpenWhitelistPayload();

    public static final Type<OpenWhitelistPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PHotbar.MODID, "open_whitelist"));

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
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            WhitelistData data = WhitelistData.get(server);
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> WhitelistMenu.fromData(containerId, inventory, data),
                    Component.translatable("gui.photbar.whitelist.title")));
        });
    }
}
