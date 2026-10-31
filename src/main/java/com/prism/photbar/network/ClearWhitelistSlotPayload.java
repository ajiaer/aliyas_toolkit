package com.prism.photbar.network;

import com.prism.photbar.PHotbar;
import com.prism.photbar.WhitelistData;
import com.prism.photbar.menu.WhitelistMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：清空白名单的某一格（界面里右键格子）。
 */
public record ClearWhitelistSlotPayload(int index) implements CustomPacketPayload {
    public static final Type<ClearWhitelistSlotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PHotbar.MODID, "clear_whitelist_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearWhitelistSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClearWhitelistSlotPayload::index,
            ClearWhitelistSlotPayload::new);

    @Override
    public Type<ClearWhitelistSlotPayload> type() {
        return TYPE;
    }

    public static void handle(ClearWhitelistSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.index() < 0 || payload.index() >= WhitelistMenu.WHITELIST_SIZE) {
                return;
            }
            if (!(player.containerMenu instanceof WhitelistMenu menu)) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }

            menu.getWhitelist().setItem(payload.index(), ItemStack.EMPTY);
            WhitelistData.get(server).setSlot(payload.index(), ItemStack.EMPTY);
            menu.broadcastChanges();
        });
    }
}
