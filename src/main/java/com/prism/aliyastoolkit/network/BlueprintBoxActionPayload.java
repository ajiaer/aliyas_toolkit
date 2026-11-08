package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.InventorySync;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxItem;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxReference;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxView;
import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：蓝图箱界面右边的三个按钮。
 * <ul>
 * <li>{@link #CLEAR_BLUEPRINT}：把所有物品放回背包（放不下就掉落），然后清空蓝图占位；</li>
 * <li>{@link #TAKE_ALL}：把能放进背包的物品取出，放不下的留在箱内，保留蓝图；</li>
 * <li>{@link #STORE_ALL}：把背包里符合占位要求的物品放入箱子，最多到每格上限。</li>
 * </ul>
 */
public record BlueprintBoxActionPayload(BlueprintBoxReference reference, int action) implements CustomPacketPayload {
    public static final int CLEAR_BLUEPRINT = 0;
    public static final int TAKE_ALL = 1;
    public static final int STORE_ALL = 2;

    public static final Type<BlueprintBoxActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "blueprint_box_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintBoxActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlueprintBoxReference.STREAM_CODEC, BlueprintBoxActionPayload::reference,
                    ByteBufCodecs.VAR_INT, BlueprintBoxActionPayload::action,
                    BlueprintBoxActionPayload::new);

    @Override
    public Type<BlueprintBoxActionPayload> type() {
        return TYPE;
    }

    public static void handle(BlueprintBoxActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof BlueprintBoxMenu menu)) {
                return;
            }
            BlueprintBoxView box = menu.getBox();
            if (!menu.getReference().equals(payload.reference()) || !box.stillValid(player)) {
                return;
            }
            switch (payload.action()) {
                case CLEAR_BLUEPRINT -> {
                    takeAll(player, box);
                    box.clearBlueprint();
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.blueprint.cleared"), true);
                }
                case TAKE_ALL -> {
                    takeAllKeepRemainder(player, box);
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.blueprint.took_all"), true);
                }
                case STORE_ALL -> {
                    storeAll(player, box);
                    player.displayClientMessage(
                            Component.translatable("message.aliyastoolkit.blueprint.stored_all"), true);
                }
                default -> {
                    return;
                }
            }
            box.setChanged();
            box.syncToClient();
            menu.broadcastChanges();
            InventorySync.sync(player);
        });
    }

    /** 清空蓝图时使用：把箱子里所有物品放进背包；放不下的掉落。 */
    private static void takeAll(ServerPlayer player, BlueprintBoxView box) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            ItemStack stack = box.removeItemNoUpdate(slot);
            giveOrDrop(player, stack);
        }
        box.setChanged();
    }

    /**
     * 一键取出：能放进玩家背包的物品移走，背包/物品栏满时多余部分留在原槽位。
     */
    private static void takeAllKeepRemainder(ServerPlayer player, BlueprintBoxView box) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            ItemStack stack = box.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = stack.copy();
            player.getInventory().add(remainder);
            box.setItem(slot, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
        }
        box.setChanged();
    }

    /** 把背包里符合占位要求的物品放进箱子，最多到每格上限。 */
    private static void storeAll(ServerPlayer player, BlueprintBoxView box) {
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            int limit = box.getLimit(slot);
            var def = box.getSlotDef(slot);
            if (limit <= 0 || def == null) {
                continue;
            }
            int space = limit - box.getItem(slot).getCount();
            if (space <= 0) {
                continue;
            }
            // 只从背包（9-35）和快捷栏（0-8）里取，不动盔甲 / 副手
            for (int inventorySlot = 0; inventorySlot < 36 && space > 0; inventorySlot++) {
                ItemStack inventoryStack = player.getInventory().getItem(inventorySlot);
                if (inventoryStack.isEmpty() || inventoryStack.getItem() instanceof BlueprintBoxItem
                        || !def.matches(inventoryStack)) {
                    continue;
                }
                int amount = Math.min(space, inventoryStack.getCount());
                ItemStack taken = player.getInventory().removeItem(inventorySlot, amount);
                if (taken.isEmpty()) {
                    continue;
                }
                ItemStack current = box.getItem(slot);
                if (current.isEmpty()) {
                    box.setItem(slot, taken);
                } else {
                    ItemStack merged = current.copy();
                    merged.grow(taken.getCount());
                    box.setItem(slot, merged);
                }
                space -= taken.getCount();
            }
        }
        box.setChanged();
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        player.getInventory().add(copy);
        if (!copy.isEmpty()) {
            player.drop(copy, false);
        }
    }
}
