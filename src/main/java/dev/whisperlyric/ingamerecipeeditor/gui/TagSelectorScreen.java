package dev.whisperlyric.ingamerecipeeditor.gui;

import dev.whisperlyric.ingamerecipeeditor.util.PinyinSearchHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;


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
    private Button firstPageButton;
    private Button prevTenPageButton;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button nextTenPageButton;
    private Button lastPageButton;
    private Button cancelButton;
    private Button createTagButton;
    private final List<Button> typeButtons = new ArrayList<>();
    
    private final List<TagEntry> allTags = new ArrayList<>();
    private final List<TagEntry> filteredTags = new ArrayList<>();
    private PinyinSearchHelper<TagEntry> searchHelper;
    
    private TagType currentTagType = TagType.ALL;
    private int currentPage = 0;
    private int maxPage = 0;
    private int leftPos, topPos;
    

    private static class TagEntry {
        final ResourceLocation tagId;
        final ItemStack representativeItem;
        final Fluid representativeFluid;
        final String displayName;
        final int itemCount;
        final TagType tagType;
        final List<String> containedNames = new ArrayList<>();
        
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
        
        void addContainedName(String name) {
            containedNames.add(name.toLowerCase());
        }
    }
    
    public TagSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onTagSelected) {
        super(Component.literal("选择标签"));
        this.parentScreen = parentScreen;
        this.onTagSelected = onTagSelected;
        this.searchHelper = new PinyinSearchHelper<>(
                tag -> {
                    if (tag.representativeFluid != null) {
                        FluidStack fs = new FluidStack(tag.representativeFluid, 1000);
                        return fs.getDisplayName().getString();
                    }
                    return tag.representativeItem.getDisplayName().getString();
                },
                tag -> tag.tagId.toString()
        );
        collectAllTags();
        updateFilteredTags("");
    }
    
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
        
        allTags.sort(Comparator.comparing(tag -> tag.tagId.toString()));
        searchHelper.buildCache(allTags);
    }
    
    @SuppressWarnings("null")
    private void collectItemTags(Set<ResourceLocation> processedTags) {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            
            item.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                
                if (!processedTags.contains(tagId)) {
                    processedTags.add(tagId);
                    
                    List<Item> taggedItems = ForgeRegistries.ITEMS.getValues().stream()
                            .filter(i -> i.builtInRegistryHolder().is(tagKey))
                            .toList();
                    
                    int itemCount = taggedItems.size();
                    ItemStack representativeItem = new ItemStack(item, 1);
                    TagEntry entry = new TagEntry(tagId, representativeItem, itemCount, TagType.ITEMS);
                    
                    for (Item taggedItem : taggedItems) {
                        ItemStack stack = new ItemStack(taggedItem, 1);
                        entry.addContainedName(stack.getDisplayName().getString());
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(taggedItem);
                        if (itemId != null) {
                            entry.addContainedName(itemId.toString());
                        }
                    }
                    
                    allTags.add(entry);
                }
            });
        }
    }
    
    @SuppressWarnings("null")
    private void collectBlockTags(Set<ResourceLocation> processedTags) {
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (block == net.minecraft.world.level.block.Blocks.AIR) continue;
            
            block.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                
                if (!processedTags.contains(tagId)) {
                    processedTags.add(tagId);
                    
                    List<Block> taggedBlocks = ForgeRegistries.BLOCKS.getValues().stream()
                            .filter(b -> b.builtInRegistryHolder().is(tagKey))
                            .toList();
                    
                    int blockCount = taggedBlocks.size();
                    ItemStack representativeItem = new ItemStack(block.asItem(), 1);
                    TagEntry entry = new TagEntry(tagId, representativeItem, blockCount, TagType.BLOCKS);
                    
                    for (Block taggedBlock : taggedBlocks) {
                        ItemStack stack = new ItemStack(taggedBlock.asItem(), 1);
                        entry.addContainedName(stack.getDisplayName().getString());
                        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(taggedBlock);
                        if (blockId != null) {
                            entry.addContainedName(blockId.toString());
                        }
                    }
                    
                    allTags.add(entry);
                }
            });
        }
    }
    
    @SuppressWarnings("null")
    private void collectFluidTags(Set<ResourceLocation> processedTags) {
        for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
            if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) continue;
            
            fluid.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                
                if (!processedTags.contains(tagId)) {
                    processedTags.add(tagId);
                    
                    List<Fluid> taggedFluids = ForgeRegistries.FLUIDS.getValues().stream()
                            .filter(f -> f.builtInRegistryHolder().is(tagKey))
                            .toList();
                    
                    int fluidCount = taggedFluids.size();
                    TagEntry entry = new TagEntry(tagId, fluid, fluidCount, TagType.FLUIDS);
                    
                    for (Fluid taggedFluid : taggedFluids) {
                        FluidStack fs = new FluidStack(taggedFluid, 1000);
                        entry.addContainedName(fs.getDisplayName().getString());
                        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(taggedFluid);
                        if (fluidId != null) {
                            entry.addContainedName(fluidId.toString());
                        }
                    }
                    
                    allTags.add(entry);
                }
            });
        }
    }
    
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
    
    private boolean matchesSearch(TagEntry tag, String searchText) {
        if (searchText.isEmpty()) return true;

        String tagStr = tag.tagId.toString().toLowerCase();
        if (tagStr.contains(searchText)) {
            return true;
        }

        for (String name : tag.containedNames) {
            if (name.contains(searchText)) {
                return true;
            }
        }

        if (searchHelper.matches(tag, searchText)) {
            return true;
        }
        
        for (String name : tag.containedNames) {
            if (searchHelper.matchesText(name, searchText)) {
                return true;
            }
        }
        
        return false;
    }
    
    @SuppressWarnings("null")
    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
        
        searchBox = new EditBox(this.font, leftPos + 8, topPos + 6, GUI_WIDTH - 16, 20, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入标签ID或物品名称..."));
        searchBox.setResponder(this::updateFilteredTags);
        addWidget(searchBox);
        
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
        
        // 分页按钮: << <10 < > 10> >> 取消
        int buttonY = topPos + GUI_HEIGHT + 20;
        int btnWidth = 24;
        int cancelWidth = 40;
        int spacing = 2;
        int totalWidth = btnWidth * 6 + cancelWidth + spacing * 6;
        int btnStartX = leftPos + (GUI_WIDTH - totalWidth) / 2;
        
        // << 到首页
        firstPageButton = addRenderableWidget(Button.builder(
                Component.literal("<<"),
                button -> firstPage())
                .bounds(btnStartX, buttonY, btnWidth, 20)
                .build());
        
        // <10 向前10页
        prevTenPageButton = addRenderableWidget(Button.builder(
                Component.literal("<10"),
                button -> previousTenPages())
                .bounds(btnStartX + btnWidth + spacing, buttonY, btnWidth, 20)
                .build());
        
        // < 上一页
        prevPageButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> previousPage())
                .bounds(btnStartX + (btnWidth + spacing) * 2, buttonY, btnWidth, 20)
                .build());
        
        // > 下一页
        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> nextPage())
                .bounds(btnStartX + (btnWidth + spacing) * 3, buttonY, btnWidth, 20)
                .build());
        
        // 10> 向后10页
        nextTenPageButton = addRenderableWidget(Button.builder(
                Component.literal("10>"),
                button -> nextTenPages())
                .bounds(btnStartX + (btnWidth + spacing) * 4, buttonY, btnWidth, 20)
                .build());
        
        // >> 到尾页
        lastPageButton = addRenderableWidget(Button.builder(
                Component.literal(">>"),
                button -> lastPage())
                .bounds(btnStartX + (btnWidth + spacing) * 5, buttonY, btnWidth, 20)
                .build());
        
        // 取消
        cancelButton = addRenderableWidget(Button.builder(
                Component.literal("取消"),
                button -> onClose())
                .bounds(btnStartX + (btnWidth + spacing) * 6, buttonY, cancelWidth, 20)
                .build());
        
        // 创建新标签组按钮（右侧）
        createTagButton = addRenderableWidget(Button.builder(
                Component.literal("创建新标签组"),
                button -> openTagCreator())
                .bounds(leftPos + GUI_WIDTH + 10, topPos + 30, 100, 20)
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
        if (firstPageButton != null) {
            firstPageButton.active = currentPage > 0;
        }
        if (prevTenPageButton != null) {
            prevTenPageButton.active = currentPage > 0;
        }
        if (prevPageButton != null) {
            prevPageButton.active = currentPage > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = currentPage < maxPage;
        }
        if (nextTenPageButton != null) {
            nextTenPageButton.active = currentPage < maxPage;
        }
        if (lastPageButton != null) {
            lastPageButton.active = currentPage < maxPage;
        }
    }
    
    private void firstPage() {
        if (currentPage > 0) {
            currentPage = 0;
            updateButtons();
        }
    }
    
    private void lastPage() {
        if (currentPage < maxPage) {
            currentPage = maxPage;
            updateButtons();
        }
    }
    
    private void previousTenPages() {
        if (currentPage > 0) {
            currentPage = Math.max(0, currentPage - 10);
            updateButtons();
        }
    }
    
    private void nextTenPages() {
        if (currentPage < maxPage) {
            currentPage = Math.min(maxPage, currentPage + 10);
            updateButtons();
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
    
    private void openTagCreator() {
        if (minecraft != null) {
            minecraft.setScreen(new CustomTagCreatorScreen(this, result -> {
                if (result != null) {
                    // 刷新标签列表
                    collectAllTags();
                    updateFilteredTags(searchBox.getValue());
                }
            }));
        }
    }
    
    @SuppressWarnings("null")
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);
        
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);
        
        renderTagList(guiGraphics, mouseX, mouseY);
        
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        String pageInfo = String.format("第 %d/%d 页 (共%d个标签)",
                currentPage + 1, maxPage + 1, filteredTags.size());
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT + 5, 0x404040);
        
        renderTooltips(guiGraphics, mouseX, mouseY);
    }
    
    @SuppressWarnings("null")
    private void renderTagList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * TAGS_PER_PAGE;
        int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
        
        int startY = topPos + 55;
        int rowHeight = 22;
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * rowHeight;
            
            TagEntry tag = filteredTags.get(i);
            
            boolean isMouseOver = mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                                mouseY >= rowY && mouseY < rowY + rowHeight - 2;
            
            int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF555555;
            guiGraphics.fill(leftPos + 8, rowY, leftPos + GUI_WIDTH - 8, rowY + rowHeight - 2, bgColor);
            
            int borderColor = isMouseOver ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(leftPos + 8, rowY, leftPos + GUI_WIDTH - 8, rowY + 1, borderColor);
            guiGraphics.fill(leftPos + 8, rowY + rowHeight - 2, leftPos + GUI_WIDTH - 8, rowY + rowHeight - 1, borderColor);
            
            RenderSystem.enableDepthTest();
            if (tag.representativeFluid != null) {
                renderFluid(guiGraphics, tag.representativeFluid, leftPos + 12, rowY + 2, 16, 16);
            } else {
                guiGraphics.renderItem(tag.representativeItem, leftPos + 12, rowY + 2);
            }
            RenderSystem.disableDepthTest();
            
            String displayText = "#" + tag.tagId.toString();
            if (displayText.length() > 35) {
                displayText = displayText.substring(0, 32) + "...";
            }
            guiGraphics.drawString(this.font, displayText, leftPos + 32, rowY + 4, 0xFFFFFF, false);
            
            String countText = "(" + tag.itemCount + "项)";
            guiGraphics.drawString(this.font, countText, leftPos + 32, rowY + 13, 0xCCCCCC, false);
        }
    }
    
    @SuppressWarnings("null")
    private void renderFluid(GuiGraphics guiGraphics, Fluid fluid, int x, int y, int width, int height) {
        if (fluid == null) return;
        
        FluidStack fluidStack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid);
        
        int tintColor = fluidExtensions.getTintColor(fluidStack);
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
        TextureAtlasSprite sprite = null;
        
        if (stillTexture != null) {
            try {
                sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
                if (sprite != null && sprite.atlasLocation().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
                    sprite = null;
                }
            } catch (Exception e) {
                sprite = null;
            }
        }
        
        if (sprite != null) {
            RenderSystem.setShaderTexture(0, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            int textureWidth = 16;
            int textureHeight = 16;
            
            int xTileCount = width / textureWidth;
            int xRemainder = width - (xTileCount * textureWidth);
            int yTileCount = height / textureHeight;
            int yRemainder = height - (yTileCount * textureHeight);
            int yStart = y + height;
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            float uDif = uMax - uMin;
            float vDif = vMax - vMin;
            
            RenderSystem.enableBlend();
            
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            for (int xTile = 0; xTile <= xTileCount; xTile++) {
                int tileWidth = (xTile == xTileCount) ? xRemainder : textureWidth;
                if (tileWidth == 0) {
                    break;
                }
                int tileX = x + (xTile * textureWidth);
                int maskRight = textureWidth - tileWidth;
                int shiftedX = tileX + textureWidth - maskRight;
                float uLocalDif = uDif * maskRight / textureWidth;
                float uLocalMin = uMin;
                float uLocalMax = uMax - uLocalDif;
                
                for (int yTile = 0; yTile <= yTileCount; yTile++) {
                    int tileHeight = (yTile == yTileCount) ? yRemainder : textureHeight;
                    if (tileHeight == 0) {
                        break;
                    }
                    int tileY = yStart - ((yTile + 1) * textureHeight);
                    int maskTop = textureHeight - tileHeight;
                    float vLocalDif = vDif * maskTop / textureHeight;
                    float vLocalMin = vMin + vLocalDif;
                    float vLocalMax = vMax;
                    
                    vertexBuffer.vertex(matrix4f, tileX, tileY + textureHeight, 0).uv(uLocalMin, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + textureHeight, 0).uv(uLocalMax, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                    vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
                }
            }
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            guiGraphics.fill(x, y, x + width, y + height, 0xFFFFFFFF);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
    
    @SuppressWarnings("null")
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * TAGS_PER_PAGE;
        int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
        
        int startY = topPos + 55;
        int rowHeight = 22;
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * rowHeight;
            
            TagEntry tag = filteredTags.get(i);
            
            boolean isMouseOver = mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                                mouseY >= rowY && mouseY < rowY + rowHeight - 2;
            
            if (isMouseOver) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("#" + tag.tagId.toString()));
                tooltip.add(Component.literal("§7类型: " + tag.tagType.getDisplayName()));
                tooltip.add(Component.literal("§7包含: " + tag.itemCount + "项"));
                
                if (tag.representativeFluid != null) {
                    FluidStack fs = new FluidStack(tag.representativeFluid, 1000);
                    tooltip.add(Component.literal("§e代表流体: " + fs.getDisplayName().getString()));
                } else if (!tag.representativeItem.isEmpty()) {
                    tooltip.add(Component.literal("§e代表物品: " + tag.representativeItem.getDisplayName().getString()));
                }
                
                tooltip.add(Component.literal("§a点击查看详情"));
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }
    
    @SuppressWarnings("null")
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startIndex = currentPage * TAGS_PER_PAGE;
            int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
            
            int startY = topPos + 55;
            int rowHeight = 22;
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int rowY = startY + relativeIndex * rowHeight;
                
                TagEntry tag = filteredTags.get(i);
                
                boolean isMouseOver = mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                                    mouseY >= rowY && mouseY < rowY + rowHeight - 2;
                
                if (isMouseOver) {
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
        if (keyCode == 256) {
            onClose();
            return true;
        }
        
        if (searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
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