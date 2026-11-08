package com.prism.aliyastoolkit.blueprint;

import com.prism.aliyastoolkit.AliyasToolkit;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 蓝图箱掉落物（物品实体）的等级保护：
 * <ul>
 * <li>所有蓝图箱：在水中向上漂浮</li>
 * <li>黑曜石及以上：掉落物不会被爆炸销毁</li>
 * <li>下界合金：物品本身防火/防岩浆（{@code fireResistant}），在岩浆中漂浮；
 * 掉入虚空会传送到最近的可站立位置</li>
 * </ul>
 * 铜制及以上保护的是放在世界里的方块本体（方块不会燃烧/被岩浆销毁），
 * 但铜制、铁制、金制、黑曜石、钻石箱的掉落物仍然可能被火/岩浆销毁。
 */
@EventBusSubscriber(modid = AliyasToolkit.MODID)
public final class BlueprintItemBehavior {
    private BlueprintItemBehavior() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof BlueprintBoxItem boxItem)) {
            return;
        }
        BlueprintTier tier = boxItem.tier();
        Level level = itemEntity.level();

        // 所有等级：水中上浮
        if (itemEntity.isInWater()) {
            Vec3 motion = itemEntity.getDeltaMovement();
            if (motion.y < 0.03D) {
                itemEntity.setDeltaMovement(motion.x, 0.03D, motion.z);
            }
        }

        // 下界合金：物品本身防火，顺便清掉残留火焰
        if (tier.voidSafe() && itemEntity.isOnFire()) {
            itemEntity.clearFire();
        }

        // 下界合金：只使用原版防火/岩浆物理，不再额外给向上速度，
        // 这样漂浮动画与下界合金工具一致。

        // 下界合金：虚空救援（只在服务端执行，避免客户端位置不同步）
        if (tier.voidSafe() && !level.isClientSide && itemEntity.getY() < level.getMinBuildHeight() - 32) {
            teleportToNearestSafe(itemEntity, level);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedEntities().removeIf(entity -> entity instanceof ItemEntity itemEntity
                && itemEntity.getItem().getItem() instanceof BlueprintBoxItem boxItem
                && boxItem.tier().explosionProof());
    }

    private static void teleportToNearestSafe(ItemEntity itemEntity, Level level) {
        int originX = Mth.floor(itemEntity.getX());
        int originZ = Mth.floor(itemEntity.getZ());
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = originX + dx;
                    int z = originZ + dz;
                    if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (y > level.getMinBuildHeight()) {
                        itemEntity.teleportTo(x + 0.5D, y + 0.5D, z + 0.5D);
                        itemEntity.setDeltaMovement(Vec3.ZERO);
                        return;
                    }
                }
            }
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        itemEntity.teleportTo(spawn.getX() + 0.5D,
                Math.max(y, level.getMinBuildHeight() + 64) + 0.5D, spawn.getZ() + 0.5D);
        itemEntity.setDeltaMovement(Vec3.ZERO);
    }
}
