package com.prism.aliyastoolkit.client;

import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.ChainPreset;
import com.prism.aliyastoolkit.ToolkitSettings;
import com.prism.aliyastoolkit.ToolkitSettingsCache;
import com.prism.aliyastoolkit.menu.ToolkitMenu;
import com.prism.aliyastoolkit.network.ClearWhitelistSlotPayload;
import com.prism.aliyastoolkit.network.SetToolkitSettingsPayload;
import com.prism.aliyastoolkit.network.SetWhitelistSlotPayload;

import net.minecraft.client.Minecraft;
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
 * “Aliya's Toolkit 配置”界面。
 * <p>
 * 布局：标题栏（右上角是关闭按钮）；
 * <ul>
 * <li>左侧（自动补充）：补充物品是否需要完全一致 + 3x3 物品白名单</li>
 * <li>右侧（连锁破坏）：触发方式、连锁数量，以及只对石头/泥土这类大量方块生效的
 * 预设（横截面 + 长度 + 锁定方向）</li>
 * <li>下方：玩家的背包与快捷栏</li>
 * </ul>
 * 白名单格子：左键设置为当前拿着的物品（不消耗），右键清空。
 * 带加号/减号的按钮：左键 +1，右键 -1。
 */
public class ToolkitScreen extends AbstractContainerScreen<ToolkitMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int BORDER_COLOR = 0xFF373737;
    private static final int SLOT_COLOR = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0x404040;
    private static final int HINT_COLOR = 0xFF555555;

    /** 右侧设置区。 */
    private static final int SETTINGS_X = 88;
    private static final int SETTINGS_RIGHT = 252;
    private static final int SETTINGS_WIDTH = SETTINGS_RIGHT - SETTINGS_X;

    /** 当前界面里的设置（和客户端缓存保持同步）。 */
    private ToolkitSettings settings = ToolkitSettingsCache.get();

    private Button exactMatchButton;
    private Button triggerModeButton;
    private Button chainLimitMinusButton;
    private Button chainLimitPlusButton;
    private Button presetPrevButton;
    private Button presetNameButton;
    private Button presetNextButton;
    private Button presetAddButton;
    private Button presetDeleteButton;
    private Button shapeUpButton;
    private Button shapeDownButton;
    private Button shapeLeftButton;
    private Button shapeRightButton;
    private Button lengthButton;
    private Button lockDirectionButton;
    private Button allowOtherBlocksButton;

    public ToolkitScreen(ToolkitMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 260;
        this.imageHeight = 242;
        this.inventoryLabelX = 49;
        this.inventoryLabelY = 152;
    }

    @Override
    protected void init() {
        super.init();
        this.settings = ToolkitSettingsCache.get();

        int x = this.leftPos;
        int y = this.topPos;

        // 右上角关闭按钮
        this.addRenderableWidget(Button.builder(Component.literal("X"), button -> this.onClose())
                .bounds(x + this.imageWidth - 24, y + 4, 18, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.close")))
                .build());

        // 自动补充：补充物品是否完全一致（放在白名单上方）
        this.exactMatchButton = this.addRenderableWidget(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withRequireExactItemMatch(!this.settings.requireExactItemMatch())))
                .bounds(x + 14, y + 18, 64, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.exact_match.tooltip")))
                .build());

        // 连锁破坏：触发方式
        this.triggerModeButton = this.addRenderableWidget(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withTriggerMode(this.settings.triggerMode().next())))
                .bounds(x + SETTINGS_X, y + 28, 100, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.trigger_mode.tooltip",
                        AliyasToolkitClient.CHAIN_MINE_KEY.getTranslatedKeyMessage())))
                .build());

        // 连锁破坏：连锁数量（左键/右键按钮）
        this.chainLimitMinusButton = this.addRenderableWidget(new StepperButton(
                x + SETTINGS_X, y + 46, 14, 14, Component.literal("-"),
                Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.chain_limit.minus")),
                () -> this.changeChainLimit(-1), () -> this.changeChainLimit(1)));
        this.chainLimitPlusButton = this.addRenderableWidget(new StepperButton(
                x + SETTINGS_RIGHT - 14, y + 46, 14, 14, Component.literal("+"),
                Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.chain_limit.plus")),
                () -> this.changeChainLimit(1), () -> this.changeChainLimit(-1)));

        // 大量方块：预设选择 / 新建 / 删除
        this.presetPrevButton = this.addRenderableWidget(Button
                .builder(Component.literal("<"), button -> this.selectRelativePreset(-1))
                .bounds(x + SETTINGS_X, y + 78, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.previous")))
                .build());
        this.presetNameButton = this.addRenderableWidget(Button
                .builder(Component.empty(), button -> this.selectRelativePreset(1))
                .bounds(x + 104, y + 78, 68, 14)
                .build());
        this.presetNextButton = this.addRenderableWidget(Button
                .builder(Component.literal(">"), button -> this.selectRelativePreset(1))
                .bounds(x + 174, y + 78, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.next")))
                .build());
        this.presetAddButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.aliyastoolkit.settings.preset.add"),
                        button -> this.applySettings(this.settings.withAddedPreset()))
                .bounds(x + 190, y + 78, 28, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.add.tooltip")))
                .build());
        this.presetDeleteButton = this.addRenderableWidget(Button
                .builder(Component.translatable("gui.aliyastoolkit.settings.preset.delete"),
                        button -> this.applySettings(this.settings.withRemovedPreset()))
                .bounds(x + 220, y + 78, 28, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.delete.tooltip")))
                .build());

        // 大量方块：横截面（上/下/左/右，0-4）
        this.shapeUpButton = this.addRenderableWidget(this.createShapeButton(
                x + SETTINGS_X, y + 96, "gui.aliyastoolkit.settings.shape.up", 0));
        this.shapeDownButton = this.addRenderableWidget(this.createShapeButton(
                x + SETTINGS_X + 42, y + 96, "gui.aliyastoolkit.settings.shape.down", 1));
        this.shapeLeftButton = this.addRenderableWidget(this.createShapeButton(
                x + SETTINGS_X + 84, y + 96, "gui.aliyastoolkit.settings.shape.left", 2));
        this.shapeRightButton = this.addRenderableWidget(this.createShapeButton(
                x + SETTINGS_X + 126, y + 96, "gui.aliyastoolkit.settings.shape.right", 3));

        // 大量方块：延伸长度（1-8）、锁定方向
        this.lengthButton = this.addRenderableWidget(new StepperButton(
                x + SETTINGS_X, y + 114, 64, 14, Component.empty(),
                Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.length.tooltip")),
                () -> this.changePresetLength(1), () -> this.changePresetLength(-1)));
        this.lockDirectionButton = this.addRenderableWidget(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withPreset(this.settings.preset()
                                .withLockDirection(!this.settings.preset().lockDirection()))))
                .bounds(x + 156, y + 114, 96, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.lock_direction.tooltip")))
                .build());

        // 大量方块：是否连范围里“其它”方块也一起破坏
        this.allowOtherBlocksButton = this.addRenderableWidget(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withAllowBreakingOtherBlocks(!this.settings.allowBreakingOtherBlocks())))
                .bounds(x + SETTINGS_X, y + 132, SETTINGS_WIDTH, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.allow_other_blocks.tooltip")))
                .build());

        this.updateButtonLabels();
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

        // 左右两栏之间的分隔线
        guiGraphics.fill(left + 78, top + 16, left + 79, top + 151, BORDER_COLOR);

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
        // 标题
        guiGraphics.drawString(this.font, this.title, 8, 6, TEXT_COLOR, false);

        // 左栏：自动补充（完全一致 + 白名单）
        guiGraphics.drawString(this.font, Component.translatable("gui.aliyastoolkit.group.whitelist"), 14, 38,
                TEXT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.aliyastoolkit.whitelist.hint.set"), 14, 107,
                HINT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.aliyastoolkit.whitelist.hint.clear"), 14, 117,
                HINT_COLOR, false);

        // 右栏：连锁破坏
        guiGraphics.drawString(this.font, Component.translatable("gui.aliyastoolkit.group.chain"), SETTINGS_X, 18,
                TEXT_COLOR, false);

        // 连锁数量的数值（两个按钮中间）
        Component chainLimit = Component.translatable("gui.aliyastoolkit.settings.chain_limit",
                this.settings.chainLimit());
        int chainLimitCenter = (SETTINGS_X + 14 + SETTINGS_RIGHT - 14) / 2;
        guiGraphics.drawString(this.font, chainLimit, chainLimitCenter - this.font.width(chainLimit) / 2, 49,
                TEXT_COLOR, false);

        // 大量方块（只对石头、泥土、下界岩等生效）
        guiGraphics.drawString(this.font, Component.translatable("gui.aliyastoolkit.group.massive_blocks"),
                SETTINGS_X, 66, TEXT_COLOR, false);

        // 下方：玩家背包
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                TEXT_COLOR, false);
    }


    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        // 白名单格子（菜单里前 9 个槽位）由自定义逻辑处理，不交给原版槽位逻辑
        if (slot != null && slot.index < ToolkitMenu.WHITELIST_SIZE) {
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

    /**
     * 保存设置：先更新本地（界面/连锁预览立刻生效），再把整份设置发给服务端校验并入库。
     */
    private void applySettings(ToolkitSettings updated) {
        this.settings = updated;
        ToolkitSettingsCache.set(updated);
        this.updateButtonLabels();
        PacketDistributor.sendToServer(new SetToolkitSettingsPayload(updated));
    }

    private void changeChainLimit(int direction) {
        int step = hasShiftDown() ? 16 : 1;
        this.applySettings(this.settings.withChainLimit(this.settings.chainLimit() + direction * step));
    }

    /** side：0=上 1=下 2=左 3=右。 */
    private void changePresetSide(int side, int delta) {
        ChainPreset preset = this.settings.preset();
        ChainPreset updated = switch (side) {
            case 0 -> preset.withUp(preset.up() + delta);
            case 1 -> preset.withDown(preset.down() + delta);
            case 2 -> preset.withLeft(preset.left() + delta);
            default -> preset.withRight(preset.right() + delta);
        };
        this.applySettings(this.settings.withPreset(updated));
    }

    private void changePresetLength(int delta) {
        ChainPreset preset = this.settings.preset();
        this.applySettings(this.settings.withPreset(preset.withLength(preset.length() + delta)));
    }

    private void selectRelativePreset(int direction) {
        int count = this.settings.presets().size();
        if (count <= 1) {
            return;
        }
        int index = Math.floorMod(this.settings.selectedPreset() + direction, count);
        this.applySettings(this.settings.withSelectedPreset(index));
    }

    private StepperButton createShapeButton(int x, int y, String labelKey, int side) {
        return new StepperButton(x, y, 38, 14, Component.empty(),
                Tooltip.create(Component.translatable(labelKey + ".tooltip")),
                () -> this.changePresetSide(side, 1),
                () -> this.changePresetSide(side, -1));
    }

    private void updateButtonLabels() {
        ChainPreset preset = this.settings.preset();

        this.exactMatchButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.exact_match",
                onOff(this.settings.requireExactItemMatch())));
        this.triggerModeButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.trigger_mode",
                Component.translatable(this.settings.triggerMode().translationKey())));

        this.presetNameButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.preset.name",
                this.settings.selectedPreset() + 1, this.settings.presets().size()));
        this.presetNameButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.aliyastoolkit.settings.preset.summary",
                preset.up(), preset.down(), preset.left(), preset.right(), preset.length(),
                onOff(preset.lockDirection()))));

        this.shapeUpButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.shape.up", preset.up()));
        this.shapeDownButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.shape.down", preset.down()));
        this.shapeLeftButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.shape.left", preset.left()));
        this.shapeRightButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.shape.right",
                preset.right()));
        this.lengthButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.length", preset.length()));
        this.lockDirectionButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.lock_direction",
                onOff(preset.lockDirection())));
        this.allowOtherBlocksButton.setMessage(Component.translatable(
                "gui.aliyastoolkit.settings.allow_other_blocks",
                onOff(this.settings.allowBreakingOtherBlocks())));

        // 预设数量到上限/下限时把按钮变灰
        this.presetAddButton.active = this.settings.presets().size() < ToolkitSettings.MAX_PRESETS;
        this.presetDeleteButton.active = this.settings.presets().size() > 1;
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "gui.aliyastoolkit.on" : "gui.aliyastoolkit.off");
    }

    /**
     * 左键执行 {@code onLeftClick}、右键执行 {@code onRightClick} 的按钮（普通 Button 只认左键）。
     */
    private static class StepperButton extends Button {
        private final Runnable onLeftClick;
        private final Runnable onRightClick;

        StepperButton(int x, int y, int width, int height, Component message, Tooltip tooltip,
                Runnable onLeftClick, Runnable onRightClick) {
            super(x, y, width, height, message, button -> {
            }, DEFAULT_NARRATION);
            this.onLeftClick = onLeftClick;
            this.onRightClick = onRightClick;
            this.setTooltip(tooltip);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.active || !this.visible || !this.clicked(mouseX, mouseY)) {
                return false;
            }
            if (button == 0) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onLeftClick.run();
                return true;
            }
            if (button == 1) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onRightClick.run();
                return true;
            }
            return false;
        }
    }

}
