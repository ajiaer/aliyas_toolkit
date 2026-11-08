package com.prism.aliyastoolkit.blueprint;

import java.util.List;

import net.minecraft.world.Container;

/**
 * 蓝图箱的统一视图：世界里的方块实体和玩家物品栏里的蓝图箱物品都实现它，
 * 因此菜单、界面按钮和滚动逻辑可以共用同一套代码。
 */
public interface BlueprintBoxView extends Container {
    BlueprintData getBlueprint();

    boolean hasBlueprint();

    int[] getLimits();

    int getLimit(int slot);

    List<BlueprintData.SlotDef> getSlotDefs();

    BlueprintData.SlotDef getSlotDef(int slot);

    void applyBlueprint(BlueprintData blueprint);

    void clearBlueprint();

    boolean hasContentsOrBlueprint();

    int getCapacity();

    /** 网络同步用的定位器（方块坐标或玩家物品栏槽位）。 */
    BlueprintBoxReference reference();

    /** 方块实体把数据推给附近客户端；物品版通过物品栏同步完成，不需要额外处理。 */
    default void syncToClient() {
    }
}
