package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxReference;
import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：打开主手蓝图箱物品的界面。
 * <p>
 * 界面和方块版蓝图箱完全一致（物品、蓝图、每格上限、三个按钮和滚动都由
 * {@link com.prism.aliyastoolkit.blueprint.BlueprintBoxItemContainer} 直接读写物品组件）。
 */
public record OpenBlueprintBoxPayload(int slot) implements CustomPacketPayload {
    public static final Type<OpenBlueprintBoxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "open_blueprint_box"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBlueprintBoxPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenBlueprintBoxPayload::slot,
                    OpenBlueprintBoxPayload::new);

    @Override
    public Type<OpenBlueprintBoxPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBlueprintBoxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
                return;
            }
            int slot = payload.slot();
            // 只允许打开当前主手所在的快捷栏槽位，避免客户端借界面操作其它格子。
            if (slot != player.getInventory().selected) {
                return;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof BlueprintBoxItem)) {
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new BlueprintBoxMenu(containerId, inventory, slot),
                    stack.getHoverName()),
                    buffer -> BlueprintBoxReference.STREAM_CODEC.encode(buffer,
                            BlueprintBoxReference.item(slot)));
        });
    }
}
