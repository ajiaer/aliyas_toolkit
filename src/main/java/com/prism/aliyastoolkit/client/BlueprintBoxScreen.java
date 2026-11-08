package com.prism.aliyastoolkit.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxBlockEntity;
import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;
import com.prism.aliyastoolkit.menu.BlueprintSlot;
import com.prism.aliyastoolkit.network.BlueprintBoxActionPayload;
import com.prism.aliyastoolkit.network.BlueprintScrollPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.opengl.GL11;

/**
 * 蓝图箱界面：最多显示 5 行 × 9 列，列与玩家背包列对齐；超过 5 行时右侧显示滚动条。
 * <p>
 * 滚动条参考创造模式物品栏：滚轮/拖动改变当前行；菜单只同步可见槽位，
 * 箱子里的隐藏槽位仍可被漏斗/按钮访问。
 */
public class BlueprintBoxScreen extends AbstractContainerScreen<BlueprintBoxMenu> {
    /** 原版容器风格的灰底、黑边、白高光、深灰阴影。 */
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int BORDER_BLACK = 0xFF000000;
    private static final int BORDER_LIGHT = 0xFFFFFFFF;
    private static final int BORDER_SHADOW = 0xFF555555;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0x404040;
    /** 原版 6 行箱子界面纹理；直接裁切头部/槽位区和底部玩家背包区。 */
    private static final ResourceLocation GENERIC_54 =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    /**
     * 原版 generic_54 的槽位区从 y=17 开始，6 行槽位到 y=124 结束；
     * 底部玩家背包区从 y=125 开始。自定义行数时从 17 开始裁切，
     * 这样不会把下一行的顶部边框多画到箱子格子下面。
     */
    private static final int GENERIC_54_BOTTOM_SOURCE_Y = 125;
    private static final int GENERIC_54_BOTTOM_HEIGHT = 97;
    /** 合成器禁用槽位的 18×18 覆盖精灵。 */
    private static final ResourceLocation CRAFTER_DISABLED_SLOT =
            ResourceLocation.withDefaultNamespace("container/crafter/disabled_slot");
    /** 占位物品透明度。 */
    private static final float GHOST_ALPHA = 0.42F;

    private static final int SLOT_X = 8;
    private static final int SLOT_Y = 18;
    /** 主面板宽度固定为原版 176；右侧扩展区只放滚动条和按钮。 */
    private static final int MAIN_WIDTH = 176;
    /** 从主面板最后 4 像素开始覆盖它的右边框，避免中间留下一条黑色竖缝。 */
    private static final int EXTENSION_X = 172;
    /** 与滚动条右侧留 4 像素间距。 */
    private static final int BUTTON_X = 192;
    private static final int BUTTON_WIDTH = 66;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCROLL_X = 175;
    private static final int SCROLL_WIDTH = 12;
    private static final int SCROLL_HEIGHT = 15;
    /** 滑槽比滑块宽 2 像素，左右各露出 1 像素。 */
    private static final int SCROLL_TRACK_X = 174;
    private static final int SCROLL_TRACK_WIDTH = 14;
    /** 滑槽的深色内边框。 */
    private static final int SCROLL_TRACK_DARK = 0xFF373737;
    /** 创造模式物品栏右侧使用的滚动条精灵。 */
    private static final ResourceLocation CREATIVE_SCROLLER =
            ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");

    private final BlueprintBoxBlockEntity box;
    private final int visibleRows;
    private boolean draggingScroll;

    public BlueprintBoxScreen(BlueprintBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.box = menu.getBox();
        this.visibleRows = menu.getVisibleRows();
        this.imageWidth = 260;
        this.imageHeight = SLOT_Y - 1 + this.visibleRows * 18 + GENERIC_54_BOTTOM_HEIGHT;
        this.inventoryLabelX = SLOT_X;
        this.inventoryLabelY = SLOT_Y + this.visibleRows * 18 + 14 - 11;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        int firstButtonY = y + SLOT_Y + 8;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.aliyastoolkit.blueprint.clear"),
                button -> this.sendAction(BlueprintBoxActionPayload.CLEAR_BLUEPRINT))
                .bounds(x + BUTTON_X, firstButtonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.blueprint.clear.tooltip")))
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.aliyastoolkit.blueprint.take_all"),
                button -> this.sendAction(BlueprintBoxActionPayload.TAKE_ALL))
                .bounds(x + BUTTON_X, firstButtonY + BUTTON_HEIGHT + 6, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.blueprint.take_all.tooltip")))
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.aliyastoolkit.blueprint.store_all"),
                button -> this.sendAction(BlueprintBoxActionPayload.STORE_ALL))
                .bounds(x + BUTTON_X, firstButtonY + (BUTTON_HEIGHT + 6) * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("gui.aliyastoolkit.blueprint.store_all.tooltip")))
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // AbstractContainerScreen 本身不会自动调用 renderTooltip，子类需要显式调用。
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void sendAction(int action) {
        PacketDistributor.sendToServer(new BlueprintBoxActionPayload(this.box.getBlockPos(), action));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        // 17 = generic_54 的槽位区起始 y；这样最后一行下面不会多出下一行的顶边框。
        int topHeight = SLOT_Y - 1 + this.visibleRows * 18;

        // 主面板完全来自原版 generic_54。
        guiGraphics.blit(GENERIC_54, left, top, 0, 0, MAIN_WIDTH, topHeight, 256, 256);
        guiGraphics.blit(GENERIC_54, left, top + topHeight,
                0, GENERIC_54_BOTTOM_SOURCE_Y, MAIN_WIDTH, GENERIC_54_BOTTOM_HEIGHT, 256, 256);
        // 右侧扩展面板最后绘制，覆盖主面板的右边框，左右之间不再出现黑缝。
        drawExtension(guiGraphics, left + EXTENSION_X, top,
                this.imageWidth - EXTENSION_X, this.imageHeight);

        int visibleSlots = this.menu.getVisibleSlots();
        for (int i = 0; i < visibleSlots; i++) {
            Slot slot = this.menu.slots.get(i);
            int slotX = left + slot.x;
            int slotY = top + slot.y;
            int limit = this.box.getLimit(slot instanceof BlueprintSlot blueprintSlot
                    ? blueprintSlot.getContainerSlot() : i);
            if (limit <= 0) {
                // 禁用槽位由 renderSlot 使用合成器的禁用槽位覆盖层绘制。
                continue;
            }
            if (slot.getItem().isEmpty()) {
                ItemStack ghost = ItemStack.EMPTY;
                if (slot instanceof BlueprintSlot blueprintSlot) {
                    ghost = blueprintSlot.getDisplayStack();
                }
                if (!ghost.isEmpty()) {
                    this.renderGhostItem(guiGraphics, ghost.copyWithCount(1), slotX, slotY);
                }
            }
        }

        this.renderScrollbar(guiGraphics);
    }

    /**
     * 渲染半透明占位物品。
     * <p>
     * 普通 {@code renderFakeItem} 对大多数方块物品使用 cutout 渲染类型，
     * 顶点 alpha 会被丢弃；这里把 cutout/solid 映射成 translucent，
     * 保证占位物品真的半透明，能区分“空槽”和“已放入 1 个物品”。
     */
    private void renderGhostItem(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
        MultiBufferSource.BufferSource buffers = guiGraphics.bufferSource();
        MultiBufferSource translucent = renderType -> {
            RenderType mapped = renderType;
            if (renderType == Sheets.cutoutBlockSheet() || renderType == Sheets.solidBlockSheet()) {
                mapped = Sheets.translucentCullBlockSheet();
            } else {
                mapped = GhostRenderTypes.translucent(renderType);
            }
            return buffers.getBuffer(mapped);
        };

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x + 8.0F, y + 8.0F, 150.0F);
        pose.scale(16.0F, -16.0F, 16.0F);
        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        }
        // 先把之前的面板/槽位批次画掉，避免半透明 alpha 作用到整个界面。
        guiGraphics.flush();

        // 第一遍只写深度，不写颜色：先建立这个物品的外表面深度。
        // 第二遍半透明绘制时，箱子内部/背面等被前表面挡住的面会自然失败。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        try {
            minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            buffers.endBatch();
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, GHOST_ALPHA);
        try {
            minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, translucent,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            buffers.endBatch();
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.disableDepthTest();
            if (flat) {
                Lighting.setupFor3DItems();
            }
            pose.popPose();
        }
    }

    /**
     * 右侧扩展面板：左边缘故意不画边框，并从主面板最后 4 像素开始铺灰色，
     * 把原版 {@code generic_54} 的右黑边遮住，左右两块面板连成一体。
     */
    private static void drawExtension(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        guiGraphics.fill(x, y + 1, right, bottom - 1, PANEL_COLOR);
        guiGraphics.fill(x, y, right, y + 1, BORDER_BLACK);
        guiGraphics.fill(x, y + 1, right - 1, y + 3, BORDER_LIGHT);
        guiGraphics.fill(x, bottom - 2, right - 1, bottom - 1, BORDER_SHADOW);
        guiGraphics.fill(right - 2, y + 1, right - 1, bottom - 1, BORDER_SHADOW);
        guiGraphics.fill(right - 1, y, right, bottom, BORDER_BLACK);
        guiGraphics.fill(x, bottom - 1, right, bottom, BORDER_BLACK);
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        int maxScroll = this.menu.getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackX = this.leftPos + SCROLL_TRACK_X;
        int thumbX = this.leftPos + SCROLL_X;
        int y = this.topPos + SLOT_Y;
        int trackHeight = this.visibleRows * 18;

        // 原版容器槽位风格的滑槽：灰底、深色左上、浅色右下，滑块左右各留 1 像素。
        guiGraphics.fill(trackX, y, trackX + SCROLL_TRACK_WIDTH, y + trackHeight, SLOT_FILL);
        guiGraphics.fill(trackX, y, trackX + SCROLL_TRACK_WIDTH, y + 1, SCROLL_TRACK_DARK);
        guiGraphics.fill(trackX, y, trackX + 1, y + trackHeight, SCROLL_TRACK_DARK);
        guiGraphics.fill(trackX, y + trackHeight - 1, trackX + SCROLL_TRACK_WIDTH, y + trackHeight, BORDER_LIGHT);
        guiGraphics.fill(trackX + SCROLL_TRACK_WIDTH - 1, y, trackX + SCROLL_TRACK_WIDTH, y + trackHeight, BORDER_LIGHT);

        int thumbY = y + (trackHeight - SCROLL_HEIGHT) * this.menu.getScrollRow() / maxScroll;
        guiGraphics.blitSprite(CREATIVE_SCROLLER, thumbX, thumbY, SCROLL_WIDTH, SCROLL_HEIGHT);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot instanceof BlueprintSlot blueprintSlot && blueprintSlot.isDisabled()) {
            // 与合成器一致：用禁用槽位精灵把整个 18×18 格子“盖住”。
            guiGraphics.blitSprite(CRAFTER_DISABLED_SLOT, slot.x - 1, slot.y - 1, 18, 18);
            return;
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected void renderSlotContents(GuiGraphics guiGraphics, ItemStack itemStack, Slot slot, String countString) {
        super.renderSlotContents(guiGraphics, itemStack, slot, countString);
        if (slot instanceof BlueprintSlot blueprintSlot && blueprintSlot.getLimit() > 0) {
            this.drawLimit(guiGraphics, slot.x, slot.y, blueprintSlot.getLimit());
        }
    }

    private void drawLimit(GuiGraphics guiGraphics, int x, int y, int limit) {
        String text = Integer.toString(limit);
        // 缩小到 0.7 倍并贴住槽位右上角，三位数也不会越过边框。
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + 16.0F, y + 1.0F, 200.0F);
        guiGraphics.pose().scale(0.7F, 0.7F, 1.0F);
        guiGraphics.drawString(this.font, text, -this.font.width(text), 0, 0xFFFFFF, true);
        guiGraphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, TEXT_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                TEXT_COLOR, false);
    }


    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Slot hovered = this.getSlotUnderMouse();
        if (this.menu.getCarried().isEmpty() && hovered instanceof BlueprintSlot blueprintSlot) {
            // 空占位显示对应物品的说明；已放入的物品也走这里，避免某些槽位不显示原版 tooltip。
            ItemStack display = blueprintSlot.getItem();
            if (display.isEmpty() && blueprintSlot.getLimit() > 0) {
                display = blueprintSlot.getDisplayStack();
            }
            if (!display.isEmpty()) {
                List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(display));
                if (blueprintSlot.getLimit() > 0 && blueprintSlot.getItem().isEmpty()) {
                    lines.add(Component.translatable("gui.aliyastoolkit.blueprint.required",
                            blueprintSlot.getLimit()));
                }
                guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.menu.getMaxScroll() > 0 && scrollY != 0.0D) {
            int row = this.menu.getScrollRow() - (int) Math.signum(scrollY);
            this.sendScroll(row);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.menu.getMaxScroll() > 0 && this.isOnScrollbar(mouseX, mouseY)) {
            this.draggingScroll = true;
            this.scrollToMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScroll) {
            this.scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingScroll) {
            this.draggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOnScrollbar(double mouseX, double mouseY) {
        int x = this.leftPos + SCROLL_TRACK_X;
        int y = this.topPos + SLOT_Y;
        int height = this.visibleRows * 18;
        return mouseX >= x && mouseX < x + SCROLL_TRACK_WIDTH && mouseY >= y && mouseY < y + height;
    }

    private void scrollToMouse(double mouseY) {
        int maxScroll = this.menu.getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = this.visibleRows * 18;
        double ratio = (mouseY - this.topPos - SLOT_Y - SCROLL_HEIGHT / 2.0D)
                / Math.max(1.0D, trackHeight - SCROLL_HEIGHT);
        int row = (int) Math.round(ratio * maxScroll);
        this.sendScroll(row);
    }

    private void sendScroll(int row) {
        int clamped = Math.max(0, Math.min(row, this.menu.getMaxScroll()));
        if (clamped == this.menu.getScrollRow()) {
            return;
        }
        this.menu.setScrollRow(clamped);
        PacketDistributor.sendToServer(new BlueprintScrollPayload(this.box.getBlockPos(), clamped));
    }
}

