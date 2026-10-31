package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.menu.WhitelistMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：清空白名单的某一格（界面里右键格子）。
 */
public record ClearWhitelistSlotPayload(int index) implements CustomPacketPayload {
    public static final Type<ClearWhitelistSlotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "clear_whitelist_slot"));

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

            // 清空这名玩家自己白名单的这一格，并同步界面
            player.getData(AliyasToolkit.WHITELIST).setSlot(payload.index(), ItemStack.EMPTY);
            menu.getWhitelist().setItem(payload.index(), ItemStack.EMPTY);
            menu.broadcastChanges();
        });
    }
}
