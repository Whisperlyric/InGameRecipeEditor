package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.util.PinyinSearchHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 标签选择界面
 * 显示游戏中所有可用的物品标签
 */
@OnlyIn(Dist.CLIENT)
public class TagSelectorScreen extends Screen {
    
    public enum TagType {
        ALL("全部", null),
        BLOCKS("方块", "blocks"),
        ITEMS("物品", "items"),
        FLUIDS("流体", "fluids");
        
        private final String displayName;
        private final String directory;
        
        TagType(String displayName, String directory) {
            this.displayName = displayName;
            this.directory = directory;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDirectory() {
            return directory;
        }
    }
    
    private static final int TAGS_PER_PAGE = 12;
    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 320;
    
    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onTagSelected;
    
    private EditBox searchBox;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button cancelButton;
    private final List<Button> typeButtons = new ArrayList<>();
    
    private final List<TagEntry> allTags = new ArrayList<>();
    private final List<TagEntry> filteredTags = new ArrayList<>();
    private PinyinSearchHelper<TagEntry> searchHelper;
    
    private TagType currentTagType = TagType.ALL;
    private int currentPage = 0;
    private int maxPage = 0;
    private int leftPos, topPos;
    
    /**
     * 标签条目，包含标签ID和代表性物品
     */
    private static class TagEntry {
        final ResourceLocation tagId;
        final ItemStack representativeItem;
        final Fluid representativeFluid;
        final String displayName;
        final int itemCount;
        final TagType tagType;
        
        TagEntry(ResourceLocation tagId, ItemStack representativeItem, int itemCount, TagType tagType) {
            this.tagId = tagId;
            this.representativeItem = representativeItem;
            this.representativeFluid = null;
            this.itemCount = itemCount;
            this.displayName = tagId.toString();
            this.tagType = tagType;
        }
        
        TagEntry(ResourceLocation tagId, Fluid fluid, int itemCount, TagType tagType) {
            this.tagId = tagId;
            this.representativeItem = ItemStack.EMPTY;
            this.representativeFluid = fluid;
            this.itemCount = itemCount;
            this.displayName = tagId.toString();
            this.tagType = tagType;
        }
    }
    
    public TagSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onTagSelected) {
        super(Component.literal("选择标签"));
        this.parentScreen = parentScreen;
        this.onTagSelected = onTagSelected;
        this.searchHelper = new PinyinSearchHelper<>(
                tag -> tag.representativeItem.getItem().getDescription().getString(),
                tag -> tag.tagId.toString()
        );
        collectAllTags();
        updateFilteredTags("");
    }
    
    /**
     * 收集所有可用的标签
     */
    private void collectAllTags() {
        allTags.clear();
        
        Set<ResourceLocation> processedTags = new HashSet<>();
        
        if (currentTagType == TagType.ALL || currentTagType == TagType.ITEMS) {
            collectItemTags(processedTags);
        }
        
        if (currentTagType == TagType.ALL || currentTagType == TagType.BLOCKS) {
            collectBlockTags(processedTags);
        }
        
        if (currentTagType == TagType.ALL || currentTagType == TagType.FLUIDS) {
            collectFluidTags(processedTags);
        }
        
        // 按命名空间和路径排序
        allTags.sort(Comparator.comparing(tag -> tag.tagId.toString()));
        searchHelper.buildCache(allTags);
    }
    
    /**
     * 收集物品标签
     */
    private void collectItemTags(Set<ResourceLocation> processedTags) {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            
            item.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                
                if (!processedTags.contains(tagId)) {
                    processedTags.add(tagId);
                    
                    int itemCount = (int) ForgeRegistries.ITEMS.getValues().stream()
                            .filter(i -> i.builtInRegistryHolder().is(tagKey))
                            .count();
                    
                    ItemStack representativeItem = new ItemStack(item, 1);
                    allTags.add(new TagEntry(tagId, representativeItem, itemCount, TagType.ITEMS));
                }
            });
        }
    }
    
    /**
     * 收集方块标签
     */
    private void collectBlockTags(Set<ResourceLocation> processedTags) {
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (block == net.minecraft.world.level.block.Blocks.AIR) continue;
            
            block.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                
                if (!processedTags.contains(tagId)) {
                    processedTags.add(tagId);
                    
                    int blockCount = (int) ForgeRegistries.BLOCKS.getValues().stream()
                            .filter(b -> b.builtInRegistryHolder().is(tagKey))
                            .count();
                    
                    ItemStack representativeItem = new ItemStack(block.asItem(), 1);
                    allTags.add(new TagEntry(tagId, representativeItem, blockCount, TagType.BLOCKS));
                }
            });
        }
    }
    
    /**
     * 收集流体标签
     */
    private void collectFluidTags(Set<ResourceLocation> processedTags) {
        for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
            if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) continue;
            
            fluid.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                
                if (!processedTags.contains(tagId)) {
                    processedTags.add(tagId);
                    
                    int fluidCount = (int) ForgeRegistries.FLUIDS.getValues().stream()
                            .filter(f -> f.builtInRegistryHolder().is(tagKey))
                            .count();
                    
                    allTags.add(new TagEntry(tagId, fluid, fluidCount, TagType.FLUIDS));
                }
            });
        }
    }
    
    /**
     * 更新过滤后的标签列表
     */
    private void updateFilteredTags(String searchText) {
        filteredTags.clear();
        
        String lowerSearch = searchText.toLowerCase().trim();
        
        for (TagEntry tag : allTags) {
            if (matchesSearch(tag, lowerSearch)) {
                filteredTags.add(tag);
            }
        }
        
        maxPage = Math.max(0, (filteredTags.size() - 1) / TAGS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }
    
    /**
     * 检查标签是否匹配搜索条件
     */
    private boolean matchesSearch(TagEntry tag, String searchText) {
        if (searchText.isEmpty()) return true;

        // 标签ID直接匹配
        String tagStr = tag.tagId.toString().toLowerCase();
        if (tagStr.contains(searchText)) {
            return true;
        }

        // 物品名称 + 拼音搜索（完整拼音、首字母、无空格拼音均支持）
        return searchHelper.matches(tag, searchText);
    }
    
    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
        
        // 搜索框
        searchBox = new EditBox(this.font, leftPos + 8, topPos + 6, GUI_WIDTH - 16, 20, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入标签ID或物品名称..."));
        searchBox.setResponder(this::updateFilteredTags);
        addWidget(searchBox);
        
        // 分类按钮
        typeButtons.clear();
        int buttonWidth = 60;
        int buttonSpacing = 5;
        int totalButtonsWidth = TagType.values().length * buttonWidth + (TagType.values().length - 1) * buttonSpacing;
        int startX = leftPos + (GUI_WIDTH - totalButtonsWidth) / 2;
        
        for (TagType type : TagType.values()) {
            Button typeButton = addRenderableWidget(Button.builder(
                    Component.literal(type.getDisplayName()),
                    button -> {
                        currentTagType = type;
                        currentPage = 0;
                        collectAllTags();
                        updateFilteredTags(searchBox.getValue());
                        updateTypeButtons();
                    })
                    .bounds(startX, topPos + 30, buttonWidth, 20)
                    .build());
            typeButtons.add(typeButton);
            startX += buttonWidth + buttonSpacing;
        }
        updateTypeButtons();
        
        // 翻页按钮
        prevPageButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> previousPage())
                .bounds(leftPos + (GUI_WIDTH - 48) / 2 - 28, topPos + GUI_HEIGHT + 20, 20, 20)
                .build());
        
        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> nextPage())
                .bounds(leftPos + (GUI_WIDTH - 48) / 2 - 4, topPos + GUI_HEIGHT + 20, 20, 20)
                .build());
        
        cancelButton = addRenderableWidget(Button.builder(
                Component.literal("取消"),
                button -> onClose())
                .bounds(leftPos + (GUI_WIDTH - 48) / 2 + 28, topPos + GUI_HEIGHT + 20, 48, 20)
                .build());
        
        updateButtons();
    }
    
    private void updateTypeButtons() {
        for (int i = 0; i < typeButtons.size(); i++) {
            Button button = typeButtons.get(i);
            TagType type = TagType.values()[i];
            button.active = type != currentTagType;
        }
    }
    
    private void updateButtons() {
        if (prevPageButton != null) {
            prevPageButton.active = currentPage > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = currentPage < maxPage;
        }
    }
    
    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateButtons();
        }
    }
    
    private void nextPage() {
        if (currentPage < maxPage) {
            currentPage++;
            updateButtons();
        }
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        // 背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);
        
        // 渲染标签列表
        renderTagList(guiGraphics, mouseX, mouseY);
        
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 页面信息
        String pageInfo = String.format("第 %d/%d 页 (共%d个标签)",
                currentPage + 1, maxPage + 1, filteredTags.size());
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT + 5, 0x404040);
        
        // 渲染工具提示
        renderTooltips(guiGraphics, mouseX, mouseY);
    }
    
    private void renderTagList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * TAGS_PER_PAGE;
        int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
        
        int startY = topPos + 55;
        int rowHeight = 22;
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * rowHeight;
            
            TagEntry tag = filteredTags.get(i);
            
            // 检查鼠标悬停
            boolean isMouseOver = mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                                mouseY >= rowY && mouseY < rowY + rowHeight - 2;
            
            // 背景
            int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF555555;
            guiGraphics.fill(leftPos + 8, rowY, leftPos + GUI_WIDTH - 8, rowY + rowHeight - 2, bgColor);
            
            // 边框
            int borderColor = isMouseOver ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(leftPos + 8, rowY, leftPos + GUI_WIDTH - 8, rowY + 1, borderColor);
            guiGraphics.fill(leftPos + 8, rowY + rowHeight - 2, leftPos + GUI_WIDTH - 8, rowY + rowHeight - 1, borderColor);
            
            // 渲染代表性物品/流体图标
            RenderSystem.enableDepthTest();
            if (tag.representativeFluid != null) {
                renderFluid(guiGraphics, tag.representativeFluid, leftPos + 12, rowY + 2, 16, 16);
            } else {
                guiGraphics.renderItem(tag.representativeItem, leftPos + 12, rowY + 2);
            }
            RenderSystem.disableDepthTest();
            
            // 标签ID
            String displayText = "#" + tag.tagId.toString();
            if (displayText.length() > 35) {
                displayText = displayText.substring(0, 32) + "...";
            }
            guiGraphics.drawString(this.font, displayText, leftPos + 32, rowY + 4, 0xFFFFFF, false);
            
            // 物品数量
            String countText = "(" + tag.itemCount + "项)";
            guiGraphics.drawString(this.font, countText, leftPos + 32, rowY + 13, 0xCCCCCC, false);
        }
    }
    
    private void renderFluid(GuiGraphics guiGraphics, Fluid fluid, int x, int y, int width, int height) {
        if (fluid == null) return;
        
        FluidStack fluidStack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid);
        
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
        if (stillTexture == null) return;
        
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        
        int tintColor = fluidExtensions.getTintColor(fluidStack);
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        
        RenderSystem.setShaderColor(r, g, b, 1.0f);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        
        guiGraphics.blit(x, y, 0, width, height, sprite);
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * TAGS_PER_PAGE;
        int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
        
        int startY = topPos + 55;
        int rowHeight = 22;
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * rowHeight;
            
            if (mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                mouseY >= rowY && mouseY < rowY + rowHeight - 2) {
                
                TagEntry tag = filteredTags.get(i);
                List<Component> tooltip = new ArrayList<>();
                
                tooltip.add(Component.literal("§6标签: §f#" + tag.tagId));
                tooltip.add(Component.literal("§7类型: §f" + tag.tagType.getDisplayName()));
                tooltip.add(Component.literal("§7包含 " + tag.itemCount + " 个" + tag.tagType.getDisplayName()));
                tooltip.add(Component.literal("§a左键: §f选择此标签"));
                tooltip.add(Component.literal("§e右键: §f预览标签内容"));
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键
            int startIndex = currentPage * TAGS_PER_PAGE;
            int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
            
            int startY = topPos + 55;
            int rowHeight = 22;
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int rowY = startY + relativeIndex * rowHeight;
                
                if (mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                    mouseY >= rowY && mouseY < rowY + rowHeight - 2) {
                    
                    TagEntry tag = filteredTags.get(i);
                    onTagSelected.accept(tag.tagId);
                    onClose();
                    return true;
                }
            }
        } else if (button == 1) { // 右键
            int startIndex = currentPage * TAGS_PER_PAGE;
            int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
            
            int startY = topPos + 55;
            int rowHeight = 22;
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int rowY = startY + relativeIndex * rowHeight;
                
                if (mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                    mouseY >= rowY && mouseY < rowY + rowHeight - 2) {
                    
                    TagEntry tag = filteredTags.get(i);
                    if (minecraft != null) {
                        minecraft.setScreen(new TagPreviewScreen(this, tag.tagId, tag.tagType));
                    }
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}