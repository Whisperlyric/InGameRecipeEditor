package dev.whisperlyric.ingamerecipeeditor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 抽象基类：网格选择界面
 * 封装了槽位网格渲染、分页、搜索、背景渲染等公共逻辑
 * @param <T> 选择项的类型
 */
public abstract class AbstractGridSelectorScreen<T> extends Screen {

    // 公共常量
    protected static final int SLOT_SIZE = 18;
    protected static final int DEFAULT_HEADER_HEIGHT = 96;
    protected static final int FOOTER_HEIGHT = 28;

    // 颜色常量（子类可覆盖）
    protected static final int C_BG_OUTER    = 0xFF0F0F0F;
    protected static final int C_BG_MAIN     = 0xFF252525;
    protected static final int C_PANEL       = 0xFF1A1A1A;
    protected static final int C_SLOT_EMPTY  = 0xFF141414;
    protected static final int C_SLOT_HOVER  = 0xFF1D3555;
    protected static final int C_DIVIDER     = 0xFF333333;
    protected static final int C_FOOTER      = 0xFF1E1E1E;
    protected static final int C_TEXT        = 0xFFE0E0E0;
    protected static final int C_TEXT_DIM    = 0xFF888888;

    // GUI 尺寸
    protected int guiWidth, guiHeight;
    protected int itemsPerRow, itemsPerPage;
    protected int leftPos, topPos;

    // 分页状态
    protected int currentPage;
    protected int maxPage;

    // 父界面和回调
    protected final Screen parentScreen;
    protected final Consumer<T> onItemSelected;

    // UI 组件
    protected EditBox searchBox;
    protected Button prevPageButton, nextPageButton, cancelButton;

    // 数据
    protected final List<T> filteredItems;

    protected AbstractGridSelectorScreen(Screen parentScreen, Consumer<T> onItemSelected, Component title) {
        super(title);
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;
        this.filteredItems = new java.util.ArrayList<>();
        this.currentPage = 0;
        this.maxPage = 0;
        this.itemsPerRow = 9;
        this.itemsPerPage = 54;
    }

    // ========== 抽象方法（子类必须实现） ==========

    /** 获取搜索框的提示文本 */
    protected abstract String getSearchHint();

    /** 收集所有可选项目 */
    protected abstract void collectAllItems();

    /** 根据搜索文本更新过滤后的项目列表 */
    protected abstract void updateFilteredItems(String searchText);

    /** 渲染单个项目 */
    protected abstract void renderItem(GuiGraphics g, T item, int x, int y);

    /** 获取项目的 tooltip 内容 */
    protected abstract List<Component> getItemTooltip(T item);

    /** 项目被选中后的处理 */
    protected abstract void onItemSelected(T item);

    // ========== 可覆盖的方法 ==========

    /** 获取头区域高度 */
    protected int getHeaderHeight() {
        return DEFAULT_HEADER_HEIGHT;
    }

    /** 获取槽位悬停颜色 */
    protected int getSlotHoverColor() {
        return C_SLOT_HOVER;
    }

    /** 获取槽位悬停覆盖层颜色 */
    protected int getSlotHoverOverlayColor() {
        return 0x30AACCFF;
    }

    /** 获取最小 GUI 宽度 */
    protected int getMinGuiWidth() {
        return 200;
    }

    /** 获取最小 GUI 高度 */
    protected int getMinGuiHeight() {
        return 200;
    }

    /** 获取最大 GUI 宽度 */
    protected int getMaxGuiWidth() {
        return 400;
    }

    /** 获取最大 GUI 高度 */
    protected int getMaxGuiHeight() {
        return 400;
    }

    /** 渲染头区域的额外内容（子类可覆盖添加） */
    protected void renderHeaderExtra(GuiGraphics g, int mouseX, int mouseY) {
        // 默认空实现，子类可覆盖
    }

    /** 渲染空槽位的边框（默认实现） */
    protected void renderEmptySlotBorder(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF0A0A0A);
        g.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF0A0A0A);
        g.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF3A3A3A);
        g.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF3A3A3A);
    }

    // ========== 公共实现的方法 ==========

    @SuppressWarnings("unchecked")
    @Override
    protected void init() {
        int maxW = Math.min(this.width - 30, getMaxGuiWidth());
        int maxH = Math.min(this.height - 50, getMaxGuiHeight());
        maxW = Math.max(maxW, getMinGuiWidth());
        maxH = Math.max(maxH, getMinGuiHeight());

        this.itemsPerRow = Math.max(4, (maxW - 20) / SLOT_SIZE);
        int gridRows = Math.max(3, (maxH - getHeaderHeight() - FOOTER_HEIGHT) / SLOT_SIZE);
        this.itemsPerPage = this.itemsPerRow * gridRows;
        this.guiWidth = 20 + this.itemsPerRow * SLOT_SIZE;
        this.guiHeight = getHeaderHeight() + gridRows * SLOT_SIZE + FOOTER_HEIGHT;

        this.leftPos = (this.width - guiWidth) / 2;
        this.topPos = (this.height - guiHeight) / 2;

        // 更新过滤列表（保留搜索框内容）
        updateFilteredItems(searchBox != null ? searchBox.getValue() : "");

        // 创建搜索框
        searchBox = new EditBox(this.font, leftPos + 10, topPos + 6, guiWidth - 20, 18, Component.literal("搜索"));
        searchBox.setHint(Component.literal(getSearchHint()));
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

        // 创建分页按钮
        prevPageButton = addRenderableWidget(Button.builder(Component.literal("◀"), b -> previousPage())
                .bounds(leftPos + 4, topPos + guiHeight - 22, 24, 18).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal("▶"), b -> nextPage())
                .bounds(leftPos + guiWidth - 28, topPos + guiHeight - 22, 24, 18).build());
        cancelButton = addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(leftPos + (guiWidth - 50) / 2, topPos + guiHeight - 22, 50, 18).build());

        updateButtons();
    }

    protected void updateButtons() {
        if (prevPageButton != null) prevPageButton.active = currentPage > 0;
        if (nextPageButton != null) nextPageButton.active = currentPage < maxPage;
    }

    protected void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateButtons();
        }
    }

    protected void nextPage() {
        if (currentPage < maxPage) {
            currentPage++;
            updateButtons();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // 外边框
        g.fill(leftPos - 1, topPos - 1, leftPos + guiWidth + 1, topPos + guiHeight + 1, C_BG_OUTER);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, C_BG_MAIN);

        // 头区域
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + getHeaderHeight() - 2, 0xFF1E1E1E);
        g.fill(leftPos + 5, topPos + getHeaderHeight() - 3, leftPos + guiWidth - 5, topPos + getHeaderHeight() - 2, C_DIVIDER);

        // 网格区域
        int gridTop = topPos + getHeaderHeight();
        int gridBot = topPos + guiHeight - FOOTER_HEIGHT;
        g.fill(leftPos, gridTop, leftPos + guiWidth, gridBot, C_PANEL);

        // 底部区域
        g.fill(leftPos, gridBot, leftPos + guiWidth, topPos + guiHeight, C_FOOTER);
        g.fill(leftPos + 5, gridBot, leftPos + guiWidth - 5, gridBot + 1, C_DIVIDER);

        // 渲染网格
        renderItemGrid(g, mouseX, mouseY);

        // 渲染搜索框
        searchBox.render(g, mouseX, mouseY, partialTick);

        // 子类额外头内容
        renderHeaderExtra(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        // 页码信息
        String pageInfo = String.format("§7第 %d / %d 页  (%d 个)", currentPage + 1, maxPage + 1, filteredItems.size());
        g.drawCenteredString(this.font, pageInfo, leftPos + guiWidth / 2, topPos + getHeaderHeight() - 14, C_TEXT_DIM);

        // Tooltip
        renderGridTooltip(g, mouseX, mouseY);
    }

    protected void renderItemGrid(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredItems.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + getHeaderHeight() + 1;
        int totalSlots = itemsPerPage;

        // 渲染空槽位
        for (int i = 0; i < totalSlots; i++) {
            int sx = gridStartX + (i % itemsPerRow) * SLOT_SIZE;
            int sy = gridStartY + (i / itemsPerRow) * SLOT_SIZE;
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_SLOT_EMPTY);
            renderEmptySlotBorder(g, sx, sy);
        }

        // 渲染项目
        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx = gridStartX + (rel % itemsPerRow) * SLOT_SIZE;
            int sy = gridStartY + (rel / itemsPerRow) * SLOT_SIZE;
            boolean hover = isMouseOverSlot(mouseX, mouseY, sx, sy);

            if (hover) {
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, getSlotHoverColor());
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, getSlotHoverOverlayColor());
            }

            T item = filteredItems.get(i);
            renderItem(g, item, sx + 1, sy + 1);
        }
    }

    protected void renderGridTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx = currentPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, filteredItems.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + getHeaderHeight() + 1;

        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx = gridStartX + (rel % itemsPerRow) * SLOT_SIZE;
            int sy = gridStartY + (rel / itemsPerRow) * SLOT_SIZE;

            if (isMouseOverSlot(mouseX, mouseY, sx, sy)) {
                T item = filteredItems.get(i);
                List<Component> tooltip = getItemTooltip(item);
                g.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startIdx = currentPage * itemsPerPage;
            int endIdx = Math.min(startIdx + itemsPerPage, filteredItems.size());
            int gridStartX = leftPos + 10;
            int gridStartY = topPos + getHeaderHeight() + 1;

            for (int i = startIdx; i < endIdx; i++) {
                int rel = i - startIdx;
                int sx = gridStartX + (rel % itemsPerRow) * SLOT_SIZE;
                int sy = gridStartY + (rel / itemsPerRow) * SLOT_SIZE;

                if (isMouseOverSlot(mouseX, mouseY, sx, sy)) {
                    onItemSelected.accept(filteredItems.get(i));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) previousPage();
        else nextPage();
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 辅助方法 ==========

    protected boolean isMouseOverSlot(double mouseX, double mouseY, int sx, int sy) {
        return mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;
    }

    protected boolean isMouseOverSlot(int mouseX, int mouseY, int sx, int sy) {
        return mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;
    }

    /** 计算最大页数 */
    protected void calculateMaxPage() {
        maxPage = itemsPerPage > 0 ? Math.max(0, (filteredItems.size() - 1) / itemsPerPage) : 0;
        currentPage = Math.min(currentPage, maxPage);
    }
}