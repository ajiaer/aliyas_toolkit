package com.prism.aliyastoolkit.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.prism.aliyastoolkit.AliyasToolkitClient;
import com.prism.aliyastoolkit.BuildPreset;
import com.prism.aliyastoolkit.ChainPreset;
import com.prism.aliyastoolkit.ToolkitSettings;
import com.prism.aliyastoolkit.ToolkitSettingsCache;
import com.prism.aliyastoolkit.menu.ToolkitMenu;
import com.prism.aliyastoolkit.network.ClearWhitelistSlotPayload;
import com.prism.aliyastoolkit.network.SetToolkitSettingsPayload;
import com.prism.aliyastoolkit.network.SetWhitelistSlotPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * “Aliya's Toolkit 配置”界面。
 * <p>
 * 布局：标题栏（右上角是关闭按钮）；
 * <ul>
 * <li>左侧：5 个功能按钮（物品切换 / 自动补充 / 连锁破坏 / 快速建造 / 蓝图建造），点哪个右边就显示哪个功能的内容</li>
 * <li>右侧：当前功能的内容区，默认显示第一个功能（物品切换）</li>
 * <li>玩家背包与快捷栏只在“自动补充”页显示（用来设置白名单）</li>
 * </ul>
 * 各功能页的内容：
 * <ul>
 * <li>物品切换：功能说明文字 + “交换行”（与快捷栏交换的背包行，1-3）</li>
 * <li>自动补充：补充物品是否需要完全一致 + 3x3 物品白名单</li>
 * <li>连锁破坏：连锁数量（左键 +1、右键 -1），以及只对石头/泥土这类大量方块生效的
 * 预设（预设名称 + 横截面 + 层数 + 锁定方向）</li>
 * <li>快速建造：预设名称 + 操作 / 维度 / 形状 / 填充</li>
 * <li>蓝图建造：功能说明 + 选区 / 收进蓝图箱 / 虚影预览 / 旋转 / 固定 / 建造的操作提示</li>
 * </ul>
 * 白名单格子和玩家背包/快捷栏只属于“自动补充”页：切到别的功能页时它们不绘制、也不响应鼠标。
 * 白名单格子：左键设置为当前拿着的物品（不消耗），右键清空。
 * 带加号/减号的按钮：左键 +1，右键 -1。
 * 预设名称那一行是可编辑的文字框（不画标题），默认“预设1”“预设2”……。
 */
public class ToolkitScreen extends AbstractContainerScreen<ToolkitMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int BORDER_COLOR = 0xFF373737;
    private static final int SLOT_COLOR = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0x404040;
    private static final int HINT_COLOR = 0xFF555555;
    /** 当前功能按钮的高亮色。 */
    private static final int ACCENT_COLOR = 0xFF3A7D32;

    /** 左侧功能按钮栏。 */
    private static final int TAB_X = 8;
    private static final int TAB_TOP = 26;
    private static final int TAB_WIDTH = 68;
    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 4;
    /** 左侧按钮栏与右侧内容区之间的分隔线。 */
    private static final int DIVIDER_X = 80;
    /** 分隔线底部留的空白，避免直接顶到面板边框。 */
    private static final int DIVIDER_BOTTOM_GAP = 8;

    /** 右侧设置区。 */
    private static final int SETTINGS_X = 88;
    private static final int SETTINGS_RIGHT = 252;
    private static final int SETTINGS_WIDTH = SETTINGS_RIGHT - SETTINGS_X;
    /** 右侧内容区的水平中心（白名单、提示文字居中用）。 */
    private static final int SETTINGS_CENTER_X = (SETTINGS_X + SETTINGS_RIGHT) / 2;

    /** 自动补充页：白名单标题、提示的纵坐标。 */
    private static final int WHITELIST_HEADING_Y = 50;
    private static final int WHITELIST_HINT_Y = 120;

    /** 物品切换页：说明文字的纵坐标，以及“交换行”按钮。 */
    private static final int HOTBAR_DESC_Y = 34;
    private static final int HOTBAR_SWAP_ROW_Y = 84;

    /** 连锁破坏页：各行的纵坐标。 */
    private static final int CHAIN_LIMIT_Y = 30;
    private static final int CHAIN_MASSIVE_LABEL_Y = 58;
    private static final int CHAIN_PRESET_Y = 72;
    /** 预设名称（可以直接编辑的文字框，不写标题）。 */
    private static final int CHAIN_PRESET_NAME_Y = 88;
    /** 大量方块：上 / 左·层数·右 / 下，十字排列。 */
    private static final int CHAIN_SHAPE_Y = 110;
    private static final int CHAIN_SHAPE_ROW_HEIGHT = 20;
    private static final int CHAIN_SHAPE_BUTTON_WIDTH = 38;
    /** 十字排列的三列（相对 GUI 的 x，整体在内容区居中）。 */
    private static final int CHAIN_SHAPE_LEFT_X = 107;
    private static final int CHAIN_SHAPE_CENTER_X = 151;
    private static final int CHAIN_SHAPE_RIGHT_X = 195;
    /** 锁定方向、允许其它方块各占一整行。 */
    private static final int CHAIN_LOCK_Y = 170;
    private static final int CHAIN_ALLOW_OTHER_Y = 190;
    private static final int CHAIN_HINT_Y = 210;

    /** 快速建造页：各行的纵坐标。 */
    private static final int BUILD_PRESET_Y = 32;
    /** 预设名称（可以直接编辑的文字框，不写标题）。 */
    private static final int BUILD_PRESET_NAME_Y = 48;
    private static final int BUILD_OPERATION_Y = 68;
    private static final int BUILD_DIMENSION_Y = 88;
    private static final int BUILD_SHAPE_Y = 108;
    private static final int BUILD_FILL_Y = 128;
    private static final int BUILD_HINT_Y = 152;

    /** 蓝图建造页：说明文字、自动收集流体开关和操作提示的纵坐标。 */
    private static final int COPY_PASTE_DESC_Y = 34;
    private static final int COPY_PASTE_FLUID_Y = 60;
    private static final int COPY_PASTE_HINT_Y = 82;

    /** 当前界面里的设置（和客户端缓存保持同步）。 */
    private ToolkitSettings settings = ToolkitSettingsCache.get();

    /** 当前选中的功能页，默认第一个（物品切换）。 */
    private Feature activeFeature = Feature.HOTBAR_SWAP;

    /** 各功能页的按钮，切换功能页时整组显示/隐藏。 */
    private final List<AbstractWidget> hotbarSwapWidgets = new ArrayList<>();
    private final List<AbstractWidget> autoRefillWidgets = new ArrayList<>();
    private final List<AbstractWidget> chainMiningWidgets = new ArrayList<>();
    private final List<AbstractWidget> quickBuildWidgets = new ArrayList<>();
    private final List<AbstractWidget> copyPasteWidgets = new ArrayList<>();

    private Button swapRowButton;
    /** 预设名称文字框（连锁破坏 / 快速建造各一个；不画标题，直接编辑）。 */
    private EditBox presetNameField;
    private EditBox buildPresetNameField;
    /** 程序设置文字框内容时，忽略它触发的改名回调。 */
    private boolean updatingNameFields;
    private Button exactMatchButton;
    private Button chainLimitButton;
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
    private Button buildPresetPrevButton;
    private Button buildPresetNameButton;
    private Button buildPresetNextButton;
    private Button buildPresetAddButton;
    private Button buildPresetDeleteButton;
    private Button buildOperationButton;
    private Button buildDimensionButton;
    private Button buildShapeButton;
    private Button buildFillButton;
    private Button collectFluidsButton;

    public ToolkitScreen(ToolkitMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 260;
        this.imageHeight = 242;
        this.inventoryLabelX = ToolkitMenu.INVENTORY_X;
        this.inventoryLabelY = 152;
    }

    @Override
    protected void init() {
        super.init();
        this.settings = ToolkitSettingsCache.get();

        // init 在窗口大小变化、从别的界面返回时会再次调用，先清空上一次的分组
        this.hotbarSwapWidgets.clear();
        this.autoRefillWidgets.clear();
        this.chainMiningWidgets.clear();
        this.quickBuildWidgets.clear();
        this.copyPasteWidgets.clear();

        int x = this.leftPos;
        int y = this.topPos;

        // 右上角关闭按钮
        this.addRenderableWidget(Button.builder(Component.literal("X"), button -> this.onClose())
                .bounds(x + this.imageWidth - 24, y + 4, 18, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.close")))
                .build());

        // 左侧：功能按钮，点击后右边显示对应功能的内容
        Feature[] features = Feature.values();
        for (int i = 0; i < features.length; i++) {
            Feature feature = features[i];
            this.addRenderableWidget(new FeatureButton(
                    x + TAB_X, y + TAB_TOP + i * (TAB_HEIGHT + TAB_GAP), TAB_WIDTH, TAB_HEIGHT,
                    feature, pressed -> this.selectFeature(feature)));
        }

        // 物品切换：交换行（与快捷栏交换的背包行，1 = 最上面一行，3 = 最后一行）
        this.swapRowButton = this.addToGroup(new StepperButton(
                x + SETTINGS_X, y + HOTBAR_SWAP_ROW_Y, SETTINGS_WIDTH, 14, Component.empty(),
                Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.swap_row.tooltip")),
                () -> this.changeSwapRow(1), () -> this.changeSwapRow(-1)), this.hotbarSwapWidgets);

        // 自动补充：补充物品是否完全一致（放在白名单上方）
        this.exactMatchButton = this.addToGroup(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withRequireExactItemMatch(!this.settings.requireExactItemMatch())))
                .bounds(x + SETTINGS_X, y + 30, SETTINGS_WIDTH, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.exact_match.tooltip")))
                .build(), this.autoRefillWidgets);

        // 连锁破坏：连锁数量（左键 +1、右键 -1，按住 Shift 步进 16）
        this.chainLimitButton = this.addToGroup(new StepperButton(
                x + SETTINGS_X, y + CHAIN_LIMIT_Y, SETTINGS_WIDTH, 14, Component.empty(),
                Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.chain_limit.tooltip")),
                () -> this.changeChainLimit(1), () -> this.changeChainLimit(-1)), this.chainMiningWidgets);

        // 大量方块：预设选择 / 新建 / 删除
        this.presetPrevButton = this.addToGroup(Button
                .builder(Component.literal("<"), button -> this.selectRelativePreset(-1))
                .bounds(x + SETTINGS_X, y + CHAIN_PRESET_Y, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.previous")))
                .build(), this.chainMiningWidgets);
        this.presetNameButton = this.addToGroup(Button
                .builder(Component.empty(), button -> this.selectRelativePreset(1))
                .bounds(x + 104, y + CHAIN_PRESET_Y, 68, 14)
                .build(), this.chainMiningWidgets);
        this.presetNextButton = this.addToGroup(Button
                .builder(Component.literal(">"), button -> this.selectRelativePreset(1))
                .bounds(x + 174, y + CHAIN_PRESET_Y, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.next")))
                .build(), this.chainMiningWidgets);
        this.presetAddButton = this.addToGroup(Button
                .builder(Component.translatable("gui.aliyastoolkit.settings.preset.add"),
                        button -> this.applySettings(this.settings.withAddedPreset()))
                .bounds(x + 190, y + CHAIN_PRESET_Y, 28, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.add.tooltip")))
                .build(), this.chainMiningWidgets);
        this.presetDeleteButton = this.addToGroup(Button
                .builder(Component.translatable("gui.aliyastoolkit.settings.preset.delete"),
                        button -> this.applySettings(this.settings.withRemovedPreset()))
                .bounds(x + 220, y + CHAIN_PRESET_Y, 28, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.delete.tooltip")))
                .build(), this.chainMiningWidgets);

        // 预设名称：切换行下面单独一行，直接编辑（不写“预设名称”这几个字）
        this.presetNameField = this.addToGroup(new EditBox(this.font,
                x + SETTINGS_X, y + CHAIN_PRESET_NAME_Y, SETTINGS_WIDTH, 14, Component.empty()),
                this.chainMiningWidgets);
        this.presetNameField.setMaxLength(ToolkitSettings.MAX_PRESET_NAME_LENGTH);
        this.presetNameField.setValue(this.settings.preset().name());
        this.presetNameField.setResponder(value -> {
            if (!this.updatingNameFields) {
                this.renameChainPreset(value);
            }
        });

        // 大量方块：十字排列（上 / 左·层数·右 / 下）
        this.shapeUpButton = this.addToGroup(this.createShapeButton(
                x + CHAIN_SHAPE_CENTER_X, y + CHAIN_SHAPE_Y,
                "gui.aliyastoolkit.settings.shape.up", 0), this.chainMiningWidgets);
        this.shapeLeftButton = this.addToGroup(this.createShapeButton(
                x + CHAIN_SHAPE_LEFT_X, y + CHAIN_SHAPE_Y + CHAIN_SHAPE_ROW_HEIGHT,
                "gui.aliyastoolkit.settings.shape.left", 2), this.chainMiningWidgets);
        this.lengthButton = this.addToGroup(new StepperButton(
                x + CHAIN_SHAPE_CENTER_X, y + CHAIN_SHAPE_Y + CHAIN_SHAPE_ROW_HEIGHT,
                CHAIN_SHAPE_BUTTON_WIDTH, 14, Component.empty(),
                Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.length.tooltip")),
                () -> this.changePresetLength(1), () -> this.changePresetLength(-1)), this.chainMiningWidgets);
        this.shapeRightButton = this.addToGroup(this.createShapeButton(
                x + CHAIN_SHAPE_RIGHT_X, y + CHAIN_SHAPE_Y + CHAIN_SHAPE_ROW_HEIGHT,
                "gui.aliyastoolkit.settings.shape.right", 3), this.chainMiningWidgets);
        this.shapeDownButton = this.addToGroup(this.createShapeButton(
                x + CHAIN_SHAPE_CENTER_X, y + CHAIN_SHAPE_Y + CHAIN_SHAPE_ROW_HEIGHT * 2,
                "gui.aliyastoolkit.settings.shape.down", 1), this.chainMiningWidgets);

        // 锁定方向、允许其它方块：各占一整行
        this.lockDirectionButton = this.addToGroup(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withPreset(this.settings.preset()
                                .withLockDirection(!this.settings.preset().lockDirection()))))
                .bounds(x + SETTINGS_X, y + CHAIN_LOCK_Y, SETTINGS_WIDTH, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.lock_direction.tooltip")))
                .build(), this.chainMiningWidgets);
        this.allowOtherBlocksButton = this.addToGroup(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withAllowBreakingOtherBlocks(!this.settings.allowBreakingOtherBlocks())))
                .bounds(x + SETTINGS_X, y + CHAIN_ALLOW_OTHER_Y, SETTINGS_WIDTH, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.allow_other_blocks.tooltip")))
                .build(), this.chainMiningWidgets);

        // 快速建造：预设行（与连锁破坏一致）
        this.buildPresetPrevButton = this.addToGroup(Button
                .builder(Component.literal("<"), button -> this.selectRelativeBuildPreset(-1))
                .bounds(x + SETTINGS_X, y + BUILD_PRESET_Y, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.previous")))
                .build(), this.quickBuildWidgets);
        this.buildPresetNameButton = this.addToGroup(Button
                .builder(Component.empty(), button -> this.selectRelativeBuildPreset(1))
                .bounds(x + 104, y + BUILD_PRESET_Y, 68, 14)
                .build(), this.quickBuildWidgets);
        this.buildPresetNextButton = this.addToGroup(Button
                .builder(Component.literal(">"), button -> this.selectRelativeBuildPreset(1))
                .bounds(x + 174, y + BUILD_PRESET_Y, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.next")))
                .build(), this.quickBuildWidgets);
        this.buildPresetAddButton = this.addToGroup(Button
                .builder(Component.translatable("gui.aliyastoolkit.settings.preset.add"),
                        button -> this.applySettings(this.settings.withAddedBuildPreset()))
                .bounds(x + 190, y + BUILD_PRESET_Y, 28, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.add.tooltip")))
                .build(), this.quickBuildWidgets);
        this.buildPresetDeleteButton = this.addToGroup(Button
                .builder(Component.translatable("gui.aliyastoolkit.settings.preset.delete"),
                        button -> this.applySettings(this.settings.withRemovedBuildPreset()))
                .bounds(x + 220, y + BUILD_PRESET_Y, 28, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.preset.delete.tooltip")))
                .build(), this.quickBuildWidgets);

        // 预设名称：切换行下面单独一行，直接编辑（不写“预设名称”这几个字）
        this.buildPresetNameField = this.addToGroup(new EditBox(this.font,
                x + SETTINGS_X, y + BUILD_PRESET_NAME_Y, SETTINGS_WIDTH, 14, Component.empty()),
                this.quickBuildWidgets);
        this.buildPresetNameField.setMaxLength(ToolkitSettings.MAX_PRESET_NAME_LENGTH);
        this.buildPresetNameField.setValue(this.settings.buildPreset().name());
        this.buildPresetNameField.setResponder(value -> {
            if (!this.updatingNameFields) {
                this.renameBuildPreset(value);
            }
        });

        // 快速建造：操作 / 维度 / 形状 / 填充（左键下一个、右键上一个，循环）
        this.buildOperationButton = this.addToGroup(new StepperButton(
                x + SETTINGS_X, y + BUILD_OPERATION_Y, SETTINGS_WIDTH, 14, Component.empty(),
                Tooltip.create(Component.empty()),
                () -> this.changeBuildOperation(1), () -> this.changeBuildOperation(-1)), this.quickBuildWidgets);
        this.buildDimensionButton = this.addToGroup(new StepperButton(
                x + SETTINGS_X, y + BUILD_DIMENSION_Y, SETTINGS_WIDTH, 14, Component.empty(),
                Tooltip.create(Component.empty()),
                () -> this.changeBuildDimension(1), () -> this.changeBuildDimension(-1)), this.quickBuildWidgets);
        this.buildShapeButton = this.addToGroup(new StepperButton(
                x + SETTINGS_X, y + BUILD_SHAPE_Y, SETTINGS_WIDTH, 14, Component.empty(),
                Tooltip.create(Component.empty()),
                () -> this.changeBuildShape(1), () -> this.changeBuildShape(-1)), this.quickBuildWidgets);
        this.buildFillButton = this.addToGroup(new StepperButton(
                x + SETTINGS_X, y + BUILD_FILL_Y, SETTINGS_WIDTH, 14, Component.empty(),
                Tooltip.create(Component.empty()),
                () -> this.changeBuildFill(1), () -> this.changeBuildFill(-1)), this.quickBuildWidgets);

        // 蓝图建造：自动收集流体（默认开）
        this.collectFluidsButton = this.addToGroup(Button
                .builder(Component.empty(), button -> this.applySettings(
                        this.settings.withCollectFluids(!this.settings.collectFluids())))
                .bounds(x + SETTINGS_X, y + COPY_PASTE_FLUID_Y, SETTINGS_WIDTH, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.settings.collect_fluids.tooltip")))
                .build(), this.copyPasteWidgets);

        this.updateButtonLabels();
        this.updateFeatureVisibility();
    }

    /** 注册按钮，并按功能页分组（切页时整组显示/隐藏）。 */
    private <T extends AbstractWidget> T addToGroup(T widget, List<AbstractWidget> group) {
        this.addRenderableWidget(widget);
        group.add(widget);
        return widget;
    }

    /** 点击左侧功能按钮：右边换成这个功能的内容。 */
    private void selectFeature(Feature feature) {
        if (this.activeFeature == feature) {
            return;
        }
        // 切页时让名称文字框失焦，避免看不见的文字框继续接收键盘输入
        if (this.presetNameField != null) {
            this.presetNameField.setFocused(false);
        }
        if (this.buildPresetNameField != null) {
            this.buildPresetNameField.setFocused(false);
        }
        this.activeFeature = feature;
        this.updateFeatureVisibility();
    }

    /** 只显示当前功能页的按钮；白名单格子和玩家背包/快捷栏也只属于“自动补充”页。 */
    private void updateFeatureVisibility() {
        setGroupVisible(this.hotbarSwapWidgets, this.activeFeature == Feature.HOTBAR_SWAP);
        setGroupVisible(this.autoRefillWidgets, this.activeFeature == Feature.AUTO_REFILL);
        setGroupVisible(this.chainMiningWidgets, this.activeFeature == Feature.CHAIN_MINING);
        setGroupVisible(this.quickBuildWidgets, this.activeFeature == Feature.QUICK_BUILD);
        setGroupVisible(this.copyPasteWidgets, this.activeFeature == Feature.COPY_PASTE);

        boolean autoRefill = this.activeFeature == Feature.AUTO_REFILL;
        this.menu.setWhitelistVisible(autoRefill);
        this.menu.setInventoryVisible(autoRefill);
    }

    private static void setGroupVisible(List<AbstractWidget> group, boolean visible) {
        for (AbstractWidget widget : group) {
            widget.visible = visible;
        }
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

        // 左侧功能按钮与右侧内容区之间的分隔线：一直画到接近底部（底部留一点空白，视觉上更舒服）
        guiGraphics.fill(left + DIVIDER_X, top + 16, left + DIVIDER_X + 1,
                top + this.imageHeight - DIVIDER_BOTTOM_GAP, BORDER_COLOR);

        // 当前功能：按钮左边画一条竖着的强调条
        int accentY = top + TAB_TOP + this.activeFeature.ordinal() * (TAB_HEIGHT + TAB_GAP);
        guiGraphics.fill(left + TAB_X - 4, accentY, left + TAB_X - 2, accentY + TAB_HEIGHT, ACCENT_COLOR);

        // 每个（当前可见的）格子画一个凹槽；白名单格子不在“自动补充”页时不画
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
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

        // 右侧：当前功能页的内容
        switch (this.activeFeature) {
            case HOTBAR_SWAP -> this.renderHotbarSwapLabels(guiGraphics);
            case AUTO_REFILL -> this.renderAutoRefillLabels(guiGraphics);
            case CHAIN_MINING -> this.renderChainMiningLabels(guiGraphics);
            case QUICK_BUILD -> this.renderQuickBuildLabels(guiGraphics);
            case COPY_PASTE -> this.renderCopyPasteLabels(guiGraphics);
        }

        // 下方：玩家背包（只在“自动补充”页显示，用来往白名单里放物品）
        if (this.menu.isInventoryVisible()) {
            guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                    TEXT_COLOR, false);
        }
    }

    /** 物品切换页的文字：只保留功能说明。 */
    private void renderHotbarSwapLabels(GuiGraphics guiGraphics) {
        this.drawContentTitle(guiGraphics);
        this.drawWrapped(guiGraphics, Component.translatable("gui.aliyastoolkit.hotbar_swap.description"),
                SETTINGS_X, HOTBAR_DESC_Y, TEXT_COLOR);
    }

    /** 自动补充页的文字（白名单标题 + 操作提示）。 */
    private void renderAutoRefillLabels(GuiGraphics guiGraphics) {
        this.drawContentTitle(guiGraphics);

        Component heading = Component.translatable("gui.aliyastoolkit.group.whitelist");
        guiGraphics.drawString(this.font, heading, SETTINGS_CENTER_X - this.font.width(heading) / 2,
                WHITELIST_HEADING_Y, TEXT_COLOR, false);

        this.drawCentered(guiGraphics, Component.translatable("gui.aliyastoolkit.whitelist.hint.set"),
                WHITELIST_HINT_Y, HINT_COLOR);
        this.drawCentered(guiGraphics, Component.translatable("gui.aliyastoolkit.whitelist.hint.clear"),
                WHITELIST_HINT_Y + 10, HINT_COLOR);
    }

    /** 连锁破坏页的文字。 */
    private void renderChainMiningLabels(GuiGraphics guiGraphics) {
        this.drawContentTitle(guiGraphics);

        // 大量方块（只对石头、泥土、下界岩等生效）
        guiGraphics.drawString(this.font, Component.translatable("gui.aliyastoolkit.group.massive_blocks"),
                SETTINGS_X, CHAIN_MASSIVE_LABEL_Y, TEXT_COLOR, false);

        // 提示：按住连锁键滚动滚轮切换预设（不循环 / 不切换开关）
        this.drawWrapped(guiGraphics, Component.translatable("gui.aliyastoolkit.settings.chain.preset_hint",
                AliyasToolkitClient.CHAIN_MINE_KEY.getTranslatedKeyMessage()), SETTINGS_X, CHAIN_HINT_Y, HINT_COLOR);
    }

    /** 快速建造页的文字。 */
    private void renderQuickBuildLabels(GuiGraphics guiGraphics) {
        this.drawContentTitle(guiGraphics);
        this.drawWrapped(guiGraphics, Component.translatable("gui.aliyastoolkit.build.hint",
                AliyasToolkitClient.QUICK_BUILD_KEY.getTranslatedKeyMessage()), SETTINGS_X, BUILD_HINT_Y, HINT_COLOR);
    }

    /** 蓝图建造页的文字：功能说明 + 选区 / 装箱 / 预览建造的用法。 */
    private void renderCopyPasteLabels(GuiGraphics guiGraphics) {
        this.drawContentTitle(guiGraphics);
        int y = this.drawWrapped(guiGraphics, Component.translatable("gui.aliyastoolkit.copy_paste.description"),
                SETTINGS_X, COPY_PASTE_DESC_Y, TEXT_COLOR);
        y = Math.max(y + 4, COPY_PASTE_HINT_Y);
        y = this.drawWrapped(guiGraphics, Component.translatable("gui.aliyastoolkit.copy_paste.hint.select",
                AliyasToolkitClient.BLUEPRINT_BUILD_KEY.getTranslatedKeyMessage(),
                AliyasToolkitClient.BLUEPRINT_SNAP_KEY.getTranslatedKeyMessage()), SETTINGS_X, y, HINT_COLOR);
        y = this.drawWrapped(guiGraphics, Component.translatable("gui.aliyastoolkit.copy_paste.hint.place",
                AliyasToolkitClient.BLUEPRINT_BUILD_KEY.getTranslatedKeyMessage(),
                AliyasToolkitClient.BLUEPRINT_ROTATE_KEY.getTranslatedKeyMessage()), SETTINGS_X, y, HINT_COLOR);
        this.drawWrapped(guiGraphics, Component.translatable("gui.aliyastoolkit.copy_paste.hint.finish",
                AliyasToolkitClient.BLUEPRINT_BUILD_KEY.getTranslatedKeyMessage(),
                AliyasToolkitClient.BLUEPRINT_MODE_KEY.getTranslatedKeyMessage(),
                AliyasToolkitClient.BLUEPRINT_EXIT_KEY.getTranslatedKeyMessage(),
                AliyasToolkitClient.BLUEPRINT_OPEN_BOX_KEY.getTranslatedKeyMessage()), SETTINGS_X, y, HINT_COLOR);
    }

    private void drawContentTitle(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, this.activeFeature.label(), SETTINGS_X, 18, TEXT_COLOR, false);
    }

    private void drawCentered(GuiGraphics guiGraphics, Component text, int y, int color) {
        guiGraphics.drawString(this.font, text, SETTINGS_CENTER_X - this.font.width(text) / 2, y, color, false);
    }

    /** 按内容区宽度自动换行绘制，返回下一行的纵坐标。 */
    private int drawWrapped(GuiGraphics guiGraphics, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : this.font.split(text, SETTINGS_WIDTH)) {
            guiGraphics.drawString(this.font, line, x, y, color, false);
            y += this.font.lineHeight + 2;
        }
        return y;
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

    /** 连锁数量：左键 +1、右键 -1；按住 Shift 时一次 16。 */
    private void changeChainLimit(int direction) {
        int step = hasShiftDown() ? 16 : 1;
        this.applySettings(this.settings.withChainLimit(this.settings.chainLimit() + direction * step));
    }

    /** 交换行：左键 +1、右键 -1（1-3）。 */
    private void changeSwapRow(int direction) {
        this.applySettings(this.settings.withSwapRow(this.settings.swapRow() + direction));
    }

    /**
     * 改名：文字框每敲一个字都会触发，直接按普通设置的流程立即同步给服务端。
     * <p>
     * 不能等关闭界面时再发：关闭流程里“设置包”和“关闭容器包”的先后不好保证，
     * 服务端要求设置界面还开着（{@code containerMenu} 是 {@code ToolkitMenu}）才接受修改，
     * 晚发的包会被丢弃，表现为重开界面/切换预设后名称又变回去。
     */
    private void renameChainPreset(String name) {
        this.applySettings(this.settings.withPresetName(name));
    }

    private void renameBuildPreset(String name) {
        this.applySettings(this.settings.withBuildPresetName(name));
    }

    /** 把当前预设名称同步到文字框（程序设置内容时不触发改名回调，正在输入的框不动）。 */
    private void updateNameFields() {
        if (this.presetNameField == null || this.buildPresetNameField == null) {
            return;
        }
        this.updatingNameFields = true;
        try {
            String chainName = this.settings.preset().name();
            // 聚焦中的框不覆盖：玩家可能正在清空重输（空名称会被补成“预设N”）
            if (!this.presetNameField.isFocused() && !this.presetNameField.getValue().equals(chainName)) {
                this.presetNameField.setValue(chainName);
            }
            String buildName = this.settings.buildPreset().name();
            if (!this.buildPresetNameField.isFocused() && !this.buildPresetNameField.getValue().equals(buildName)) {
                this.buildPresetNameField.setValue(buildName);
            }
        } finally {
            this.updatingNameFields = false;
        }
    }

    /** 预设切换按钮只有 68 宽，名字太长时截断显示（完整名字在下面的文字框里）。 */
    private String shortName(String name) {
        if (this.font.width(name) <= 62) {
            return name;
        }
        return this.font.plainSubstrByWidth(name, 54) + "...";
    }

    /** 是否有预设名称文字框正处于编辑状态。 */
    private boolean isNameFieldFocused() {
        return (this.presetNameField != null && this.presetNameField.isFocused())
                || (this.buildPresetNameField != null && this.buildPresetNameField.isFocused());
    }

    /**
     * 文字框聚焦时禁用界面/快捷键：普通字母键原版输入框不“消费”，
     * 会继续传给 {@link AbstractContainerScreen}，例如在名称里打 e 会直接关掉界面。
     * <p>
     * 这里先让输入框自己处理（退格、方向键、Ctrl+A/C/V、回车等），
     * 剩下的按键全部吞掉；Esc 关闭界面、Tab 保留原版的焦点切换。
     * 点击别处让输入框失焦后，快捷键恢复正常。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isNameFieldFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            // 回车：结束编辑（名称已经实时保存），恢复快捷键
            this.setFocused(null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    /**
     * 点击输入框以外的地方（空白处、按钮、格子）时让输入框失焦，之后的快捷键恢复正常。
     * <p>
     * 不能看 {@code super.mouseClicked} 的返回值：原版容器界面点空白处也会返回 true。
     * 这里直接按点击位置判断；随后 super 会把焦点交给真正被点到的控件（按钮 / 另一个输入框）。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isNameFieldFocused()
                && !this.presetNameField.isMouseOver(mouseX, mouseY)
                && !this.buildPresetNameField.isMouseOver(mouseX, mouseY)) {
            this.setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    /** 在当前预设的基础上前后移动；到两端就停住，不会循环。 */
    private void selectRelativePreset(int direction) {
        int count = this.settings.presets().size();
        if (count <= 1) {
            return;
        }
        int index = Mth.clamp(this.settings.selectedPreset() + (direction >= 0 ? 1 : -1), 0, count - 1);
        if (index != this.settings.selectedPreset()) {
            this.applySettings(this.settings.withSelectedPreset(index));
        }
    }

    /** 切换快速建造预设；到两端停住，不循环。 */
    private void selectRelativeBuildPreset(int direction) {
        int count = this.settings.buildPresets().size();
        if (count <= 1) {
            return;
        }
        int index = Mth.clamp(this.settings.selectedBuildPreset() + (direction >= 0 ? 1 : -1), 0, count - 1);
        if (index != this.settings.selectedBuildPreset()) {
            this.applySettings(this.settings.withSelectedBuildPreset(index));
        }
    }

    private void changeBuildOperation(int direction) {
        BuildPreset preset = this.settings.buildPreset();
        this.applySettings(this.settings.withBuildPreset(preset.withOperation(
                BuildPreset.step(BuildPreset.operations(), preset.operation(), direction))));
    }

    private void changeBuildDimension(int direction) {
        BuildPreset preset = this.settings.buildPreset();
        this.applySettings(this.settings.withBuildPreset(preset.withDimension(
                BuildPreset.step(BuildPreset.dimensions(), preset.dimension(), direction))));
    }

    private void changeBuildShape(int direction) {
        BuildPreset preset = this.settings.buildPreset();
        this.applySettings(this.settings.withBuildPreset(preset.withShape(
                BuildPreset.step(BuildPreset.shapesFor(preset.dimension()), preset.shape(), direction))));
    }

    private void changeBuildFill(int direction) {
        BuildPreset preset = this.settings.buildPreset();
        this.applySettings(this.settings.withBuildPreset(preset.withFill(
                BuildPreset.step(BuildPreset.fillsFor(preset.dimension(), preset.shape()), preset.fill(), direction))));
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
        this.chainLimitButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.chain_limit",
                this.settings.chainLimit()));
        this.swapRowButton.setMessage(Component.translatable("gui.aliyastoolkit.settings.swap_row",
                this.settings.swapRow()));

        this.presetNameButton.setMessage(Component.literal(shortName(preset.name())));
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

        BuildPreset build = this.settings.buildPreset();
        this.buildPresetNameButton.setMessage(Component.literal(shortName(build.name())));
        this.buildPresetNameButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.aliyastoolkit.build.preset.summary",
                Component.translatable(build.operation().translationKey()),
                Component.translatable(build.dimension().translationKey()),
                Component.translatable(build.shape().translationKey()),
                Component.translatable(build.fill().translationKey()))));
        this.buildOperationButton.setMessage(Component.translatable("gui.aliyastoolkit.build.operation",
                Component.translatable(build.operation().translationKey())));
        this.buildDimensionButton.setMessage(Component.translatable("gui.aliyastoolkit.build.dimension",
                Component.translatable(build.dimension().translationKey())));
        this.buildShapeButton.setMessage(Component.translatable("gui.aliyastoolkit.build.shape",
                Component.translatable(build.shape().translationKey())));
        this.buildFillButton.setMessage(Component.translatable("gui.aliyastoolkit.build.fill",
                Component.translatable(build.fill().translationKey())));
        this.collectFluidsButton.setMessage(Component.translatable(
                "gui.aliyastoolkit.settings.collect_fluids", onOff(this.settings.collectFluids())));

        // 鼠标悬停时显示这个设置当前可选的全部内容（当前项用 [] 标出）
        this.buildOperationButton.setTooltip(Tooltip.create(buildOptionsTooltip(
                BuildPreset.operations(), build.operation(),
                value -> Component.translatable(value.translationKey()),
                "gui.aliyastoolkit.build.operation.tooltip")));
        this.buildDimensionButton.setTooltip(Tooltip.create(buildOptionsTooltip(
                BuildPreset.dimensions(), build.dimension(),
                value -> Component.translatable(value.translationKey()),
                "gui.aliyastoolkit.build.dimension.tooltip")));
        this.buildShapeButton.setTooltip(Tooltip.create(buildOptionsTooltip(
                BuildPreset.shapesFor(build.dimension()), build.shape(),
                value -> Component.translatable(value.translationKey()),
                "gui.aliyastoolkit.build.shape.tooltip")));
        this.buildFillButton.setTooltip(Tooltip.create(buildOptionsTooltip(
                BuildPreset.fillsFor(build.dimension(), build.shape()), build.fill(),
                value -> Component.translatable(value.translationKey()),
                "gui.aliyastoolkit.build.fill.tooltip")));

        // 预设数量到上限/下限时把按钮变灰；到两端时把上一页/下一页按钮变灰（不循环）
        this.presetAddButton.active = this.settings.presets().size() < ToolkitSettings.MAX_PRESETS;
        this.presetDeleteButton.active = this.settings.presets().size() > 1;
        this.presetPrevButton.active = this.settings.selectedPreset() > 0;
        this.presetNextButton.active = this.settings.selectedPreset() < this.settings.presets().size() - 1;
        this.buildPresetAddButton.active = this.settings.buildPresets().size() < ToolkitSettings.MAX_PRESETS;
        this.buildPresetDeleteButton.active = this.settings.buildPresets().size() > 1;
        this.buildPresetPrevButton.active = this.settings.selectedBuildPreset() > 0;
        this.buildPresetNextButton.active = this.settings.selectedBuildPreset()
                < this.settings.buildPresets().size() - 1;
        // 只有一个可选项时把形状/填充按钮变灰
        this.buildShapeButton.active = BuildPreset.shapesFor(build.dimension()).size() > 1;
        this.buildFillButton.active = BuildPreset.fillsFor(build.dimension(), build.shape()).size() > 1;

        // 名称文字框跟当前预设保持一致（切换/新建/删除预设后）
        this.updateNameFields();
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "gui.aliyastoolkit.on" : "gui.aliyastoolkit.off");
    }

    /** 生成“可选：…”的 tooltip（当前项用 [] 标出）。 */
    private static <T> Component buildOptionsTooltip(List<T> values, T current,
            Function<T, Component> nameOf, String tooltipKey) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                list.append(Component.literal(" · "));
            }
            Component name = nameOf.apply(values.get(i));
            if (values.get(i).equals(current)) {
                list.append(Component.literal("[").append(name).append(Component.literal("]")));
            } else {
                list.append(name);
            }
        }
        return Component.translatable(tooltipKey, list);
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

    /** 左侧的功能按钮：当前选中时画一圈绿色内边框。 */
    private final class FeatureButton extends Button {
        private final Feature feature;

        private FeatureButton(int x, int y, int width, int height, Feature feature, Button.OnPress onPress) {
            super(x, y, width, height, feature.label(), onPress, DEFAULT_NARRATION);
            this.feature = feature;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            if (ToolkitScreen.this.activeFeature != this.feature) {
                return;
            }
            int left = this.getX();
            int top = this.getY();
            int right = left + this.getWidth();
            int bottom = top + this.getHeight();
            guiGraphics.fill(left, top, right, top + 1, ACCENT_COLOR);
            guiGraphics.fill(left, bottom - 1, right, bottom, ACCENT_COLOR);
            guiGraphics.fill(left, top, left + 1, bottom, ACCENT_COLOR);
            guiGraphics.fill(right - 1, top, right, bottom, ACCENT_COLOR);
        }
    }

    /** 左侧功能按钮对应的功能页（顺序就是按钮从上到下的顺序）。 */
    private enum Feature {
        HOTBAR_SWAP("gui.aliyastoolkit.tab.hotbar_swap"),
        AUTO_REFILL("gui.aliyastoolkit.tab.auto_refill"),
        CHAIN_MINING("gui.aliyastoolkit.tab.chain_mining"),
        QUICK_BUILD("gui.aliyastoolkit.tab.quick_build"),
        COPY_PASTE("gui.aliyastoolkit.tab.copy_paste");

        private final String translationKey;

        Feature(String translationKey) {
            this.translationKey = translationKey;
        }

        private Component label() {
            return Component.translatable(this.translationKey);
        }
    }
}

