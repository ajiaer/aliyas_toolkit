package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：蓝图箱界面滚动到指定行。
 * <p>
 * 菜单只包含可见槽位；服务端更新槽位对应的容器索引后广播一次变化，
 * 保证之后点击的槽位在两边映射一致。
 */
public record BlueprintScrollPayload(BlockPos pos, int row) implements CustomPacketPayload {
    public static final Type<BlueprintScrollPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "blueprint_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintScrollPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BlueprintScrollPayload::pos,
                    ByteBufCodecs.VAR_INT, BlueprintScrollPayload::row,
                    BlueprintScrollPayload::new);

    @Override
    public Type<BlueprintScrollPayload> type() {
        return TYPE;
    }

    public static void handle(BlueprintScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof BlueprintBoxMenu menu)) {
                return;
            }
            if (!menu.getBox().getBlockPos().equals(payload.pos())) {
                return;
            }
            menu.setScrollRow(payload.row());
            menu.broadcastChanges();
        });
    }
}
