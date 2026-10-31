package com.prism.aliyastoolkit.client;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.network.RefillHotbarPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * 客户端侧的“快捷栏物品消失”检测。
 * <p>
 * 之所以在客户端判断：只有客户端知道玩家有没有打开 GUI（比如正在背包里整理物品、或者打开了 Aliya's Toolkit 配置界面），
 * 服务端并不知道玩家是否按 E 打开了背包。检测到“从非空变空”后才发请求，服务端负责真正的补充。
 * <p>
 * 无论是否处于可触发状态，每 tick 都会更新快照，所以：
 * <ul>
 * <li>在 GUI 里移动/丢弃物品不会触发补充（也不会在关闭 GUI 后补触发）</li>
 * <li>按快捷键交换后会用 {@link #suppressAfterSwap()} 抑制一小段时间，不会因为“交换后格子变空”而补充</li>
 * </ul>
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID, value = Dist.CLIENT)
public final class ClientAutoRefill {
    private static final int HOTBAR_SIZE = 9;
    /** 交换快捷栏后抑制自动补充的 tick 数（0.5 秒）。 */
    private static final int SUPPRESS_TICKS_AFTER_SWAP = 10;

    private static final ItemStack[] PREVIOUS = new ItemStack[HOTBAR_SIZE];

    static {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            PREVIOUS[i] = ItemStack.EMPTY;
        }
    }

    private static LocalPlayer trackedPlayer;
    private static int suppressTicks;

    private ClientAutoRefill() {
    }

    /**
     * 刚执行过“快捷栏 <-> 背包最后一行”交换，短时间内不要触发自动补充。
     */
    public static void suppressAfterSwap() {
        suppressTicks = Math.max(suppressTicks, SUPPRESS_TICKS_AFTER_SWAP);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            trackedPlayer = null;
            suppressTicks = 0;
            clearSnapshot();
            return;
        }

        // 换世界/重生后玩家对象会变，重新建立快照
        if (player != trackedPlayer) {
            trackedPlayer = player;
            suppressTicks = 0;
            takeSnapshot(player);
            return;
        }

        if (suppressTicks > 0) {
            suppressTicks--;
        }

        boolean canTrigger = suppressTicks == 0
                && minecraft.screen == null
                && minecraft.getConnection() != null
                && player.isAlive()
                && !player.isDeadOrDying()
                && !player.isSpectator();

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack current = player.getInventory().getItem(slot);

            // 只在“上一 tick 有物品、这一 tick 变空”时补充，并把消失的物品带上
            if (canTrigger && !PREVIOUS[slot].isEmpty() && current.isEmpty() && hasChannel(minecraft)) {
                PacketDistributor.sendToServer(new RefillHotbarPayload(slot, PREVIOUS[slot].copyWithCount(1)));
            }

            if (!ItemStack.matches(PREVIOUS[slot], current)) {
                PREVIOUS[slot] = current.copy();
            }
        }
    }

    private static boolean hasChannel(Minecraft minecraft) {
        return NetworkRegistry.hasChannel(minecraft.getConnection().getConnection(), ConnectionProtocol.PLAY, RefillHotbarPayload.TYPE.id());
    }

    private static void takeSnapshot(LocalPlayer player) {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            PREVIOUS[slot] = player.getInventory().getItem(slot).copy();
        }
    }

    private static void clearSnapshot() {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            PREVIOUS[slot] = ItemStack.EMPTY;
        }
    }
}
