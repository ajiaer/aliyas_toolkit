package com.prism.aliyastoolkit.blueprint;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

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

    /** 流体储罐容量（mB）；0 表示该等级没有储罐。 */
    int getTankCapacity();

    /** 当前储罐（可直接修改，修改后调用 {@link #setChanged()} / {@link #syncToClient()}）。 */
    FluidTank getTank();

    /** 用指定储罐替换并持久化。 */
    void setTank(FluidTank tank);

    /** 第二栏（容器内容物）的格数，默认与箱子容量一致。 */
    default int getContentCapacity() {
        return this.getCapacity();
    }

    /** 容器内容物一栏的占位（只用于显示，不参与建造）。 */
    default List<BlueprintData.SlotDef> getContentSlotDefs() {
        BlueprintData blueprint = this.getBlueprint();
        return blueprint == null ? List.of() : blueprint.createContentSlots(this.getContentCapacity());
    }

    default BlueprintData.SlotDef getContentSlotDef(int slot) {
        List<BlueprintData.SlotDef> defs = this.getContentSlotDefs();
        return slot >= 0 && slot < defs.size() ? defs.get(slot) : null;
    }

    /** 容器内容物一栏的每格上限（0 = 禁用）。 */
    default int getContentLimit(int slot) {
        BlueprintData.SlotDef def = this.getContentSlotDef(slot);
        return def == null ? 0 : def.limit();
    }

    /** 内容物格子读取（第二栏）。 */
    ItemStack getContentItem(int slot);

    void setContentItem(int slot, ItemStack stack);

    ItemStack removeContentItem(int slot, int amount);

    /** 内容物格子能否放入该物品（占位匹配 + 未超过上限）。 */
    default boolean canPlaceContentItem(int slot, ItemStack stack) {
        int limit = this.getContentLimit(slot);
        BlueprintData.SlotDef def = this.getContentSlotDef(slot);
        return limit > 0 && def != null && !stack.isEmpty()
                && !(stack.getItem() instanceof BlueprintBoxItem)
                && def.matches(stack) && this.getContentItem(slot).getCount() < limit;
    }

    /** 网络同步用的定位器（方块坐标或玩家物品栏槽位）。 */
    BlueprintBoxReference reference();

    /** 方块实体把数据推给附近客户端；物品版通过物品栏同步完成，不需要额外处理。 */
    default void syncToClient() {
    }
}
