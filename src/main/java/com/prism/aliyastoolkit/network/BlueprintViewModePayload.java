package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxReference;
import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：切换蓝图箱界面显示的是哪一栏。
 * <p>
 * 0 = 蓝图箱材料，1 = 蓝图内容器里的物品。服务端切换后再广播一次，
 * 保证两边槽位读写的是同一份数据。
 */
public record BlueprintViewModePayload(BlueprintBoxReference reference, int mode)
        implements CustomPacketPayload {
    public static final Type<BlueprintViewModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "blueprint_view_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintViewModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlueprintBoxReference.STREAM_CODEC, BlueprintViewModePayload::reference,
                    ByteBufCodecs.VAR_INT, BlueprintViewModePayload::mode,
                    BlueprintViewModePayload::new);

    @Override
    public Type<BlueprintViewModePayload> type() {
        return TYPE;
    }

    public static void handle(BlueprintViewModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof BlueprintBoxMenu menu)) {
                return;
            }
            if (!menu.getReference().equals(payload.reference())) {
                return;
            }
            menu.setViewMode(payload.mode());
            menu.broadcastChanges();
        });
    }
}
