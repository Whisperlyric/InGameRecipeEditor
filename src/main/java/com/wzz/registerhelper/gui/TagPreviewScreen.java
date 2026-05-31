package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class TagPreviewScreen extends Screen {
    
    private static final int ITEMS_PER_PAGE = 36;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 280;
    
    private final Screen parentScreen;
    private final ResourceLocation tagId;
    private final TagSelectorScreen.TagType tagType;
    
    private Button prevPageButton;
    private Button nextPageButton;
    private Button closeButton;
    
    private final List<ItemStack> items = new ArrayList<>();
    private final List<Fluid> fluids = new ArrayList<>();
    
    private int currentPage = 0;
    private int maxPage = 0;
    private int leftPos, topPos;
    
    public TagPreviewScreen(Screen parentScreen, ResourceLocation tagId, TagSelectorScreen.TagType tagType) {
        super(Component.literal("标签预览: #" + tagId));
        this.parentScreen = parentScreen;
        this.tagId = tagId;
        this.tagType = tagType;
        collectTagContents();
    }
    
    private void collectTagContents() {
        items.clear();
        fluids.clear();
        
        if (tagType == TagSelectorScreen.TagType.ITEMS) {
            TagKey<Item> tag = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId);
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item.builtInRegistryHolder().is(tag)) {
                    items.add(new ItemStack(item, 1));
                }
            }
        } else if (tagType == TagSelectorScreen.TagType.BLOCKS) {
            TagKey<Block> tag = TagKey.create(ForgeRegistries.BLOCKS.getRegistryKey(), tagId);
            for (Block block : ForgeRegistries.BLOCKS.getValues()) {
                if (block.builtInRegistryHolder().is(tag)) {
                    items.add(new ItemStack(block.asItem(), 1));
                }
            }
        } else if (tagType == TagSelectorScreen.TagType.FLUIDS) {
            TagKey<Fluid> tag = TagKey.create(ForgeRegistries.FLUIDS.getRegistryKey(), tagId);
            for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
                if (fluid.builtInRegistryHolder().is(tag)) {
                    fluids.add(fluid);
                }
            }
        }
        
        int totalItems = tagType == TagSelectorScreen.TagType.FLUIDS ? fluids.size() : items.size();
        maxPage = Math.max(0, (totalItems - 1) / ITEMS_PER_PAGE);
    }
    
    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
        
        prevPageButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> previousPage())
                .bounds(leftPos - 24, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());
        
        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> nextPage())
                .bounds(leftPos + GUI_WIDTH + 4, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());
        
        closeButton = addRenderableWidget(Button.builder(
                Component.literal("关闭"),
                button -> onClose())
                .bounds(leftPos + (GUI_WIDTH - 48) / 2, topPos + GUI_HEIGHT - 24, 48, 20)
                .build());
        
        updateButtons();
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
        
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);
        
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos + 6, 0xFFFFFF);
        
        String typeText = "类型: " + tagType.getDisplayName() + " | 数量: " + 
                         (tagType == TagSelectorScreen.TagType.FLUIDS ? fluids.size() : items.size());
        guiGraphics.drawCenteredString(this.font, typeText, this.width / 2, topPos + 20, 0xAAAAAA);
        
        renderItems(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        String pageInfo = String.format("第 %d/%d 页", currentPage + 1, maxPage + 1);
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT + 5, 0x404040);
        
        renderTooltips(guiGraphics, mouseX, mouseY);
    }
    
    private void renderItems(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int cols = 9;
        int rows = 4;
        int itemSize = 18;
        int startX = leftPos + 8;
        int startY = topPos + 35;
        
        if (tagType == TagSelectorScreen.TagType.FLUIDS) {
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, fluids.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int col = relativeIndex % cols;
                int row = relativeIndex / cols;
                int x = startX + col * itemSize;
                int y = startY + row * itemSize;
                
                Fluid fluid = fluids.get(i);
                
                guiGraphics.fill(x, y, x + 16, y + 16, 0xFF555555);
                
                renderFluid(guiGraphics, fluid, x, y, 16, 16);
            }
        } else {
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int col = relativeIndex % cols;
                int row = relativeIndex / cols;
                int x = startX + col * itemSize;
                int y = startY + row * itemSize;
                
                ItemStack item = items.get(i);
                
                RenderSystem.enableDepthTest();
                guiGraphics.renderItem(item, x, y);
                RenderSystem.disableDepthTest();
            }
        }
    }
    
    private void renderFluid(GuiGraphics guiGraphics, Fluid fluid, int x, int y, int width, int height) {
        if (fluid == null) return;
        
        FluidStack fluidStack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid);
        
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
        if (stillTexture == null) return;
        
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = 
            Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        
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
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int cols = 9;
        int itemSize = 18;
        int startX = leftPos + 8;
        int startY = topPos + 35;
        
        if (tagType == TagSelectorScreen.TagType.FLUIDS) {
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, fluids.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int col = relativeIndex % cols;
                int row = relativeIndex / cols;
                int x = startX + col * itemSize;
                int y = startY + row * itemSize;
                
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    Fluid fluid = fluids.get(i);
                    ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
                    
                    List<Component> tooltip = new ArrayList<>();
                    FluidStack fluidStack = new FluidStack(fluid, 1000);
                    tooltip.add(fluidStack.getDisplayName());
                    tooltip.add(Component.literal("§7" + fluidId));
                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                    break;
                }
            }
        } else {
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int col = relativeIndex % cols;
                int row = relativeIndex / cols;
                int x = startX + col * itemSize;
                int y = startY + row * itemSize;
                
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    ItemStack item = items.get(i);
                    guiGraphics.renderTooltip(this.font, item, mouseX, mouseY);
                    break;
                }
            }
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
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
