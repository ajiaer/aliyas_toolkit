package com.prism.aliyastoolkit.client;

import com.prism.aliyastoolkit.menu.WhitelistMenu;
import com.prism.aliyastoolkit.network.ClearWhitelistSlotPayload;
import com.prism.aliyastoolkit.network.SetWhitelistSlotPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 白名单配置界面。
 * <p>
 * 布局：标题栏（右上角是关闭按钮）、上方 3x3 白名单格子、下方只有玩家的背包和快捷栏。
 * <ul>
 * <li>左键点白名单格子：把当前鼠标拿着的物品设置为该格内容（手上的物品不会消耗，可以放回去）</li>
 * <li>右键点白名单格子：清空该格</li>
 * </ul>
 * 白名单格子不能直接放入/取出物品，所有修改都通过自定义网络包发给服务端保存。
 */
public class WhitelistScreen extends AbstractContainerScreen<WhitelistMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int BORDER_COLOR = 0xFF373737;
    private static final int SLOT_COLOR = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0x404040;

    public WhitelistScreen(WhitelistMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 78;
    }

    @Override
    protected void init() {
        super.init();
        // 右上角关闭按钮
        this.addRenderableWidget(Button.builder(Component.literal("X"), button -> this.onClose())
                .bounds(this.leftPos + this.imageWidth - 24, this.topPos + 4, 18, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.close")))
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        // 面板
        guiGraphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, PANEL_COLOR);
        // 边框
        guiGraphics.fill(left, top, left + this.imageWidth, top + 1, BORDER_COLOR);
        guiGraphics.fill(left, top + this.imageHeight - 1, left + this.imageWidth, top + this.imageHeight, BORDER_COLOR);
        guiGraphics.fill(left, top, left + 1, top + this.imageHeight, BORDER_COLOR);
        guiGraphics.fill(left + this.imageWidth - 1, top, left + this.imageWidth, top + this.imageHeight, BORDER_COLOR);

        // 每个格子画一个凹槽
        for (Slot slot : this.menu.slots) {
            int slotX = left + slot.x;
            int slotY = top + slot.y;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, BORDER_COLOR);
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, SLOT_COLOR);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, TEXT_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT_COLOR, false);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        // 白名单格子（菜单里前 9 个槽位）由自定义逻辑处理，不交给原版槽位逻辑
        if (slot != null && slot.index < WhitelistMenu.WHITELIST_SIZE) {
            if (type == ClickType.PICKUP) {
                if (mouseButton == 0) {
                    // 左键：把手上拿着的物品设置为这一格（只是选择，不消耗物品）
                    ItemStack carried = this.menu.getCarried();
                    if (!carried.isEmpty()) {
                        PacketDistributor.sendToServer(new SetWhitelistSlotPayload(slot.index, carried.copyWithCount(1)));
                    }
                } else if (mouseButton == 1) {
                    // 右键：清空
                    PacketDistributor.sendToServer(new ClearWhitelistSlotPayload(slot.index));
                }
            }
            return;
        }

        super.slotClicked(slot, slotId, mouseButton, type);
    }
}
