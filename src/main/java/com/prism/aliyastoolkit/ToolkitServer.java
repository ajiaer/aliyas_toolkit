package com.prism.aliyastoolkit;

import com.prism.aliyastoolkit.network.SyncToolkitSettingsPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端通用的玩家事件处理。
 * <p>
 * 玩家登录时把“Aliya's Toolkit 配置”界面里能改的设置同步给客户端：
 * 客户端要用它来决定连锁预览、连锁数量、以及快捷键是“按住”还是“切换”。
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID)
public final class ToolkitServer {
    private ToolkitServer() {
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 新的一局从“未开启连锁”开始，避免上一次会话的状态残留
        ChainMining.forget(player);

        if (player.connection.hasChannel(SyncToolkitSettingsPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player,
                    new SyncToolkitSettingsPayload(player.getData(AliyasToolkit.SETTINGS)));
        }
    }

    @SubscribeEvent
    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ChainMining.forget(event.getEntity());
    }
}
