package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class FluidGridSelectionScreen extends Screen {
    private static final int SLOT = 18;
    private static final int SLOT_SPACING = 20;
    private static final int COLS = 6;
    private static final int ROWS = 5;
    
    private final Screen parent;
    private final Consumer<FluidStack> callback;
    
    private final List<FluidStack> allFluids = new ArrayList<>();
    private final List<FluidStack> filteredFluids = new ArrayList<>();
    private EditBox searchBox;
    private int scroll = 0;
    private FluidStack selectedFluid = FluidStack.EMPTY;
    
    private final PinyinSearchHelper<FluidStack> searchHelper;
    
    private int gridX, gridY;
    private int gridWidth, gridHeight;
    
    public FluidGridSelectionScreen(Screen parent, Consumer<FluidStack> callback) {
        super(Component.literal("流体选择"));
        this.parent = parent;
        this.callback = callback;
        this.searchHelper = new PinyinSearchHelper<>(
            fluid -> fluid.getDisplayName().getString(),
            fluid -> {
                ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
                return id != null ? id.toString() : "";
            }
        );
        
        // 初始化流体列表
        initFluids();
    }
    
    private void initFluids() {
        for (net.minecraft.world.level.material.Fluid fluid : ForgeRegistries.FLUIDS) {
            if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                allFluids.add(new FluidStack(fluid, 1000));
            }
        }
        filteredFluids.addAll(allFluids);
        searchHelper.buildCache(allFluids);
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 计算网格位置和大小
        gridWidth = COLS * SLOT_SPACING;
        gridHeight = ROWS * SLOT_SPACING;
        gridX = (this.width - gridWidth) / 2;
        gridY = 40;
        
        // 搜索框
        int searchWidth = gridWidth;
        searchBox = new EditBox(Minecraft.getInstance().font, 
            gridX, 15, searchWidth, 20, Component.literal("搜索"));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
    }
    
    private void onSearchChanged(String query) {
        filteredFluids.clear();
        if (query.isEmpty()) {
            filteredFluids.addAll(allFluids);
        } else {
            filteredFluids.addAll(searchHelper.filter(allFluids, query));
        }
        scroll = 0;
    }
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        
        // 标题
        g.drawCenteredString(font, "§6流体选择", this.width / 2, 5, 0xFFFFFF);
        
        // 网格背景
        g.fill(gridX - 1, gridY - 1, gridX + gridWidth + 1, gridY + gridHeight + 1, 0xFF333340);
        g.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0xFF0C0C18);
        
        // 渲染流体槽
        int maxScroll = Math.max(0, (filteredFluids.size() + COLS - 1) / COLS - ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        
        // 记录hover的槽位信息，用于最后渲染tooltip
        FluidStack hoveredFluid = null;
        int hoveredMouseX = mouseX;
        int hoveredMouseY = mouseY;
        
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filteredFluids.size()) break;
                
                FluidStack fluid = filteredFluids.get(idx);
                int sx = gridX + col * SLOT_SPACING;
                int sy = gridY + row * SLOT_SPACING;
                
                boolean hover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
                boolean selected = !selectedFluid.isEmpty() && fluid.isFluidStackIdentical(selectedFluid);
                
                if (selected) {
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF3A6A3A);
                } else if (hover) {
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF3A3A6A);
                }
                
                // 渲染流体
                renderFluidSlot(g, fluid, sx + 1, sy + 1);
                
                if (hover) {
                    hoveredFluid = fluid;
                }
            }
        }
        
        // 滚动条
        if (maxScroll > 0) {
            int scrollBarX = gridX + gridWidth + 2;
            int scrollBarHeight = gridHeight;
            int scrollThumbHeight = Math.max(10, scrollBarHeight * ROWS / ((filteredFluids.size() + COLS - 1) / COLS));
            int scrollThumbY = gridY + (int)((scroll / (float)maxScroll) * (scrollBarHeight - scrollThumbHeight));
            
            g.fill(scrollBarX, gridY, scrollBarX + 3, gridY + scrollBarHeight, 0xFF333340);
            g.fill(scrollBarX, scrollThumbY, scrollBarX + 3, scrollThumbY + scrollThumbHeight, 0xFFAAAAAA);
        }
        
        super.render(g, mouseX, mouseY, partialTick);
        
        // 在所有渲染完成后，最后渲染tooltip
        if (hoveredFluid != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hoveredFluid.getDisplayName());
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(hoveredFluid.getFluid());
            if (id != null) {
                tooltip.add(Component.literal("§8" + id));
            }
            g.renderTooltip(font, tooltip, Optional.empty(), hoveredMouseX, hoveredMouseY);
        }
        
        // 提示
        g.drawCenteredString(font, "§7点击选择流体 | ESC取消", this.width / 2, gridY + gridHeight + 10, 0xAAAAAA);
    }
    
    private void renderFluidSlot(GuiGraphics g, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) return;
        
        net.minecraft.world.level.material.Fluid f = fluid.getFluid();
        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(f);
        
        int tintColor = fluidExt.getTintColor(fluid);
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g2 = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        
        ResourceLocation stillTexture = fluidExt.getStillTexture(fluid);
        if (stillTexture != null) {
            try {
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = 
                    Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
                
                if (sprite != null) {
                    RenderSystem.setShaderTexture(0, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
                    RenderSystem.setShaderColor(r, g2, b, 1.0f);
                    
                    // 渲染流体纹理
                    g.blit(x, y, 0, SLOT, SLOT, sprite);
                    
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                }
            } catch (Exception e) {
                // 使用颜色填充
                g.fill(x, y, x + SLOT, y + SLOT, tintColor);
            }
        } else {
            // 使用颜色填充
            g.fill(x, y, x + SLOT, y + SLOT, tintColor);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 检查是否点击了流体槽
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = (row + scroll) * COLS + col;
                    if (idx >= filteredFluids.size()) break;
                    
                    int sx = gridX + col * SLOT_SPACING;
                    int sy = gridY + row * SLOT_SPACING;
                    
                    if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
                        selectedFluid = filteredFluids.get(idx).copy();
                        callback.accept(selectedFluid);
                        minecraft.setScreen(parent);
                        return true;
                    }
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, (filteredFluids.size() + COLS - 1) / COLS - ROWS);
        scroll = Math.max(0, Math.min(scroll - (int)delta, maxScroll));
        return true;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}