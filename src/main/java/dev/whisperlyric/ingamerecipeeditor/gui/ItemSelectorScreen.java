package dev.whisperlyric.ingamerecipeeditor.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 物品选择界面
 * 继承 AbstractGridSelectorScreen，添加分类标签页和背包模式功能
 */
public class ItemSelectorScreen extends AbstractGridSelectorScreen<ItemStack> {

    // 物品特定常量
    private static final int ITEM_HEADER_HEIGHT = 108; // 更高以容纳标签页
    private static final int C_ITEM_SLOT_HOVER = 0xFF1D3555;
    private static final int C_ITEM_HOVER_OVERLAY = 0x30AACCFF;
    private static final int C_TITLE_BAR = 0xFF1A3A6A;
    private static final int C_TITLE_LINE = 0xFF4A7ACF;

    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> inventoryItems = new ArrayList<>();
    private final PinyinSearchHelper<ItemStack> searchHelper;
    private final Consumer<ItemStack> onItemSelectedCallback;

    // 额外的 UI 状态
    private CycleButton<SelectionMode> modeButton;
    private SelectionMode currentMode = SelectionMode.ALL_ITEMS;
    private ItemCategory currentCategory = ItemCategory.ALL;

    public enum SelectionMode {
        ALL_ITEMS("所有物品"), INVENTORY("背包物品");
        private final String displayName;
        SelectionMode(String d) { this.displayName = d; }
        public String getDisplayName() { return displayName; }
    }

    public enum ItemCategory {
        ALL      ("全部",   0xFFE0E0E0),
        BLOCKS   ("方块",   0xFF88DDAA),
        TOOLS    ("工具",   0xFFDDAA55),
        COMBAT   ("战斗",   0xFFDD5555),
        FOOD     ("食物",   0xFFDDCC44),
        MISC     ("其他",   0xFFAAAACC);

        final String label;
        final int color;
        ItemCategory(String l, int c) { label = l; color = c; }
    }

    public ItemSelectorScreen(net.minecraft.client.gui.screens.Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(parentScreen, stack -> onItemSelected.accept(stack.copy()), Component.literal("选择物品"));
        this.onItemSelectedCallback = onItemSelected;
        this.searchHelper = new PinyinSearchHelper<>(
                item -> item.getItem().getDescription().getString(),
                item -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
                    return id != null ? id.toString() : "";
                }
        );
        collectAllItems();
        collectInventoryItems();
        searchHelper.buildCache(allItems);
        updateFilteredItems("");
    }

    // ========== 抽象方法实现 ==========

    @Override
    protected String getSearchHint() {
        return "输入物品名/注册名/拼音... 或 @mod名";
    }

    @Override
    protected int getHeaderHeight() {
        return ITEM_HEADER_HEIGHT;
    }

    @Override
    protected int getMinGuiWidth() {
        return 260;
    }

    @Override
    protected int getMinGuiHeight() {
        return 230;
    }

    @Override
    protected int getMaxGuiWidth() {
        return 620;
    }

    @Override
    protected int getMaxGuiHeight() {
        return 500;
    }

    @Override
    protected int getSlotHoverColor() {
        return C_ITEM_SLOT_HOVER;
    }

    @Override
    protected int getSlotHoverOverlayColor() {
        return C_ITEM_HOVER_OVERLAY;
    }

    @Override
    protected void collectAllItems() {
        allItems.clear();
        List<ItemStack> common = List.of(
            Items.DIAMOND.getDefaultInstance(), Items.EMERALD.getDefaultInstance(),
            Items.GOLD_INGOT.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(),
            Items.STICK.getDefaultInstance(), Items.STONE.getDefaultInstance(),
            Items.COBBLESTONE.getDefaultInstance(), Items.REDSTONE.getDefaultInstance(),
            Items.GLOWSTONE_DUST.getDefaultInstance(), Items.ENDER_PEARL.getDefaultInstance(),
            Items.BLAZE_ROD.getDefaultInstance(), Items.NETHER_STAR.getDefaultInstance(),
            Items.DRAGON_EGG.getDefaultInstance()
        );
        allItems.addAll(common);
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack s = item.getDefaultInstance();
            if (!allItems.contains(s) && item != Items.AIR) allItems.add(s);
        }
    }

    private void collectInventoryItems() {
        inventoryItems.clear();
        if (minecraft == null || minecraft.player == null) return;
        Player player = minecraft.player;
        Set<String> added = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) {
                String key = getItemIdentifier(s);
                if (!added.contains(key)) {
                    inventoryItems.add(s.copy());
                    added.add(key);
                }
            }
        }
        inventoryItems.sort((a, b) -> a.getHoverName().getString().compareTo(b.getHoverName().getString()));
    }

    private String getItemIdentifier(ItemStack s) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        String base = id != null ? id.toString() : "unknown";
        return s.hasTag() ? base + "#" + s.getTag().hashCode() : base;
    }

    private ItemCategory classifyItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BlockItem) return ItemCategory.BLOCKS;
        if (item instanceof SwordItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof ArmorItem
                || item instanceof TridentItem || item instanceof ShieldItem)
            return ItemCategory.COMBAT;
        if (item instanceof DiggerItem) return ItemCategory.TOOLS;
        if (item.isEdible()) return ItemCategory.FOOD;
        return ItemCategory.MISC;
    }

    @Override
    protected void updateFilteredItems(String searchText) {
        filteredItems.clear();
        List<ItemStack> src = currentMode == SelectionMode.INVENTORY ? inventoryItems : allItems;
        List<ItemStack> searched = searchText.isEmpty() ? new ArrayList<>(src) : new ArrayList<>(searchHelper.filter(src, searchText));

        if (currentCategory != ItemCategory.ALL) {
            searched.removeIf(s -> classifyItem(s) != currentCategory);
        }
        filteredItems.addAll(searched);
        calculateMaxPage();
        updateButtons();
    }

    @Override
    protected void renderItem(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        RenderSystem.enableDepthTest();
        g.renderItem(stack, x, y);
        if (currentMode == SelectionMode.INVENTORY && stack.hasTag()) {
            g.fill(x + 10, y, x + SLOT_SIZE - 1, y + 8, 0x90AA00FF);
            g.drawString(this.font, "§d✦", x + 10, y, 0xFFFFFF, true);
        }
        if (stack.getCount() > 1) g.renderItemDecorations(this.font, stack, x, y);
        RenderSystem.disableDepthTest();
    }

    @Override
    protected List<Component> getItemTooltip(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(stack.getHoverName());
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            tooltip.add(Component.literal("§7ID: " + id));
            tooltip.add(Component.literal("§9来自: " + id.getNamespace()));
        }
        if (currentMode == SelectionMode.INVENTORY && stack.hasTag()) {
            tooltip.add(Component.literal("§b✦ 包含NBT数据"));
        }
        if (stack.getCount() > 1) tooltip.add(Component.literal("§7数量: " + stack.getCount()));
        return tooltip;
    }

    @Override
    protected void onItemSelected(ItemStack stack) {
        onItemSelectedCallback.accept(stack.copy());
    }

    // ========== 扩展的初始化和渲染 ==========

    @SuppressWarnings("unchecked")
    @Override
    protected void init() {
        super.init();

        // 添加模式切换按钮
        modeButton = addRenderableWidget(CycleButton.<SelectionMode>builder(
                        m -> Component.literal(m.getDisplayName()))
                .withValues(SelectionMode.values())
                .withInitialValue(currentMode)
                .displayOnlyValue()
                .create(leftPos + 10, topPos + 6, 90, 20,
                        Component.literal("选择模式"), this::onModeChanged));
    }

    private void onModeChanged(CycleButton<SelectionMode> btn, SelectionMode mode) {
        this.currentMode = mode;
        if (mode == SelectionMode.INVENTORY) collectInventoryItems();
        updateFilteredItems(searchBox.getValue());
    }

    @Override
    protected void renderHeaderExtra(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        // 渲染标题栏
        int tb = topPos + 28;
        g.fill(leftPos, topPos, leftPos + guiWidth, tb, C_TITLE_BAR);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + 1, C_TITLE_LINE);
        g.fill(leftPos, tb - 1, leftPos + guiWidth, tb, 0xFF335599);
        String titleText = currentMode == SelectionMode.INVENTORY ? "选择物品  §b[ 背包 ]" : "选择物品  §7[ 全部 ]";
        g.drawCenteredString(this.font, titleText, leftPos + guiWidth / 2, topPos + 9, C_TEXT);

        // 渲染分类标签页
        renderCategoryTabs(g, mouseX, mouseY);

        // 搜索框位置调整（向下）
        if (searchBox != null) {
            searchBox.setY(topPos + 32);
        }
    }

    private void renderCategoryTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabY = topPos + 54;
        int tabH = 18;
        int tabSpacing = 3;
        ItemCategory[] cats = ItemCategory.values();
        int totalSpacing = tabSpacing * (cats.length - 1);
        int tabW = Math.max(28, (guiWidth - 20 - totalSpacing) / cats.length);
        int tabX = leftPos + 10;

        for (ItemCategory cat : cats) {
            boolean sel = cat == currentCategory;
            boolean hover = !sel && mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + tabH;

            int bg = sel ? 0xFF1D3A6A : (hover ? 0xFF252530 : 0xFF161616);
            g.fill(tabX, tabY, tabX + tabW, tabY + tabH, bg);
            int lineColor = sel ? cat.color : (hover ? 0xFF444444 : 0xFF2A2A2A);
            g.fill(tabX, tabY, tabX + tabW, tabY + 2, lineColor);
            int txtColor = sel ? cat.color : (hover ? 0xFFCCCCCC : 0xFF777777);
            g.drawCenteredString(this.font, cat.label, tabX + tabW / 2, tabY + 5, txtColor);

            tabX += tabW + tabSpacing;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 处理标签页点击
            ItemCategory[] cats = ItemCategory.values();
            int totalSpacing = 3 * (cats.length - 1);
            int tabW = Math.max(28, (guiWidth - 20 - totalSpacing) / cats.length);
            int tabX = leftPos + 10;
            int tabY = topPos + 54, tabH = 18;
            for (ItemCategory cat : cats) {
                if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + tabH) {
                    currentCategory = cat;
                    currentPage = 0;
                    updateFilteredItems(searchBox != null ? searchBox.getValue() : "");
                    return true;
                }
                tabX += tabW + 3;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}