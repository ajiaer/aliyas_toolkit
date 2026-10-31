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
 * 客户端 -> 服务端：把白名单的某一格设置为指定物品（数量固定存 1）。
 */
public record SetWhitelistSlotPayload(int index, ItemStack stack) implements CustomPacketPayload {
    public static final Type<SetWhitelistSlotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PHotbar.MODID, "set_whitelist_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetWhitelistSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetWhitelistSlotPayload::index,
            ItemStack.OPTIONAL_STREAM_CODEC, SetWhitelistSlotPayload::stack,
            SetWhitelistSlotPayload::new);

    @Override
    public Type<SetWhitelistSlotPayload> type() {
        return TYPE;
    }

    public static void handle(SetWhitelistSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.stack().isEmpty()) {
                return;
            }
            if (payload.index() < 0 || payload.index() >= WhitelistMenu.WHITELIST_SIZE) {
                return;
            }
            // 只有真的打开着白名单界面时才允许修改
            if (!(player.containerMenu instanceof WhitelistMenu menu)) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }

            ItemStack stack = payload.stack().copyWithCount(1);
            menu.getWhitelist().setItem(payload.index(), stack);
            WhitelistData.get(server).setSlot(payload.index(), stack);
            menu.broadcastChanges();
        });
    }
}
