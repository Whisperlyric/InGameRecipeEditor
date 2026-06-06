package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import com.wzz.registerhelper.gui.util.RecipePreviewRenderer;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ChemicalGridSelectionScreen extends Screen {
    private static final int SLOT = 18;
    private static final int SLOT_SPACING = 20;
    private static final int COLS = 6;
    private static final int ROWS = 5;
    
    private final Screen parent;
    private final ChemicalSlotComponent.ChemicalType chemicalType;
    private final Consumer<ChemicalSelectionResult> callback;
    
    private final List<ChemicalEntry> allChemicals = new ArrayList<>();
    private final List<ChemicalEntry> filteredChemicals = new ArrayList<>();
    private EditBox searchBox;
    private int scroll = 0;
    private ChemicalEntry selectedChemical = null;
    
    private final PinyinSearchHelper<ChemicalEntry> searchHelper;
    
    private int gridX, gridY;
    private int gridWidth, gridHeight;
    
    public static class ChemicalSelectionResult {
        public final String chemicalId;
        public final long amount;
        public final RecipePreviewRenderer.ContentType type;
        
        public ChemicalSelectionResult(String chemicalId, long amount, RecipePreviewRenderer.ContentType type) {
            this.chemicalId = chemicalId;
            this.amount = amount;
            this.type = type;
        }
    }
    
    /**
     * 化学品条目
     */
    public static class ChemicalEntry {
        final ResourceLocation id;
        final RecipePreviewRenderer.ContentType type;
        final int color;
        final String displayName;
        
        ChemicalEntry(ResourceLocation id, RecipePreviewRenderer.ContentType type, int color, String displayName) {
            this.id = id;
            this.type = type;
            this.color = color;
            this.displayName = displayName;
        }
        
        String getDisplayName() {
            return displayName;
        }
        
        String getIdString() {
            return id != null ? id.toString() : "";
        }
    }
    
    public ChemicalGridSelectionScreen(Screen parent, ChemicalSlotComponent.ChemicalType chemicalType, Consumer<ChemicalSelectionResult> callback) {
        super(Component.literal(chemicalType.getDisplayName() + "选择"));
        this.parent = parent;
        this.chemicalType = chemicalType;
        this.callback = callback;
        this.searchHelper = new PinyinSearchHelper<>(
            entry -> entry.getDisplayName(),
            entry -> entry.getIdString()
        );
        
        // 初始化化学品列表
        initChemicals();
    }
    
    private void initChemicals() {
        if (!ModList.get().isLoaded("mekanism")) {
            return;
        }
        
        try {
            // 根据类型加载化学品
            switch (chemicalType) {
                case GAS -> loadGases();
                case SLURRY -> loadSlurries();
                case PIGMENT -> loadPigments();
                case INFUSE_TYPE -> loadInfuseTypes();
                case ANY -> loadAllChemicals();
            }
        } catch (Exception e) {
            // 加载失败时忽略
        }
        
        filteredChemicals.addAll(allChemicals);
        searchHelper.buildCache(allChemicals);
    }
    
    private void loadGases() {
        for (var gas : mekanism.api.MekanismAPI.gasRegistry().getValues()) {
            if (!gas.isEmptyType()) {
                ResourceLocation id = mekanism.api.MekanismAPI.gasRegistry().getKey(gas);
                if (id != null) {
                    String displayName = getGasDisplayName(gas);
                    int color = gas.getTint();
                    allChemicals.add(new ChemicalEntry(id, RecipePreviewRenderer.ContentType.GAS, color, displayName));
                }
            }
        }
    }
    
    private void loadSlurries() {
        for (var slurry : mekanism.api.MekanismAPI.slurryRegistry().getValues()) {
            if (!slurry.isEmptyType()) {
                ResourceLocation id = mekanism.api.MekanismAPI.slurryRegistry().getKey(slurry);
                if (id != null) {
                    String displayName = slurry.getTextComponent().getString();
                    int color = slurry.getTint();
                    allChemicals.add(new ChemicalEntry(id, RecipePreviewRenderer.ContentType.SLURRY, color, displayName));
                }
            }
        }
    }
    
    private void loadPigments() {
        for (var pigment : mekanism.api.MekanismAPI.pigmentRegistry().getValues()) {
            if (!pigment.isEmptyType()) {
                ResourceLocation id = mekanism.api.MekanismAPI.pigmentRegistry().getKey(pigment);
                if (id != null) {
                    String displayName = pigment.getTextComponent().getString();
                    int color = pigment.getTint();
                    allChemicals.add(new ChemicalEntry(id, RecipePreviewRenderer.ContentType.PIGMENT, color, displayName));
                }
            }
        }
    }
    
    private void loadInfuseTypes() {
        for (var infuseType : mekanism.api.MekanismAPI.infuseTypeRegistry().getValues()) {
            if (!infuseType.isEmptyType()) {
                ResourceLocation id = mekanism.api.MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                if (id != null) {
                    String displayName = infuseType.getTextComponent().getString();
                    int color = infuseType.getTint();
                    allChemicals.add(new ChemicalEntry(id, RecipePreviewRenderer.ContentType.INFUSE_TYPE, color, displayName));
                }
            }
        }
    }
    
    private void loadAllChemicals() {
        loadGases();
        loadSlurries();
        loadPigments();
        loadInfuseTypes();
    }
    
    private String getGasDisplayName(mekanism.api.chemical.gas.Gas gas) {
        try {
            return gas.getTextComponent().getString();
        } catch (Exception e) {
            return gas.getRegistryName() != null ? gas.getRegistryName().getPath() : "Unknown";
        }
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
        filteredChemicals.clear();
        if (query.isEmpty()) {
            filteredChemicals.addAll(allChemicals);
        } else {
            filteredChemicals.addAll(searchHelper.filter(allChemicals, query));
        }
        scroll = 0;
    }
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        
        // 标题
        g.drawCenteredString(font, "§6" + chemicalType.getDisplayName() + "选择", this.width / 2, 5, 0xFFFFFF);
        
        // 网格背景
        g.fill(gridX - 1, gridY - 1, gridX + gridWidth + 1, gridY + gridHeight + 1, 0xFF333340);
        g.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0xFF0C0C18);
        
        // 渲染化学品槽
        int maxScroll = Math.max(0, (filteredChemicals.size() + COLS - 1) / COLS - ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        
        // 记录hover的槽位信息，用于最后渲染tooltip
        ChemicalEntry hoveredChemical = null;
        int hoveredMouseX = mouseX;
        int hoveredMouseY = mouseY;
        
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filteredChemicals.size()) break;
                
                ChemicalEntry chemical = filteredChemicals.get(idx);
                int sx = gridX + col * SLOT_SPACING;
                int sy = gridY + row * SLOT_SPACING;
                
                boolean hover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
                boolean selected = selectedChemical != null && chemical.id.equals(selectedChemical.id);
                
                if (selected) {
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF3A6A3A);
                } else if (hover) {
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF3A3A6A);
                }
                
                // 渲染化学品
                renderChemicalSlot(g, chemical, sx + 1, sy + 1);
                
                if (hover) {
                    hoveredChemical = chemical;
                }
            }
        }
        
        // 滚动条
        if (maxScroll > 0) {
            int scrollBarX = gridX + gridWidth + 2;
            int scrollBarHeight = gridHeight;
            int scrollThumbHeight = Math.max(10, scrollBarHeight * ROWS / ((filteredChemicals.size() + COLS - 1) / COLS));
            int scrollThumbY = gridY + (int)((scroll / (float)maxScroll) * (scrollBarHeight - scrollThumbHeight));
            
            g.fill(scrollBarX, gridY, scrollBarX + 3, gridY + scrollBarHeight, 0xFF333340);
            g.fill(scrollBarX, scrollThumbY, scrollBarX + 3, scrollThumbY + scrollThumbHeight, 0xFFAAAAAA);
        }
        
        super.render(g, mouseX, mouseY, partialTick);
        
        // 在所有渲染完成后，最后渲染tooltip
        if (hoveredChemical != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredChemical.getDisplayName()));
            tooltip.add(Component.literal("§8" + hoveredChemical.id));
            tooltip.add(Component.literal("§7类型: " + hoveredChemical.type.name()));
            g.renderTooltip(font, tooltip, Optional.empty(), hoveredMouseX, hoveredMouseY);
        }
        
        // 提示
        g.drawCenteredString(font, "§7点击选择化学品 | ESC取消", this.width / 2, gridY + gridHeight + 10, 0xAAAAAA);
    }
    
    private void renderChemicalSlot(GuiGraphics guiGraphics, ChemicalEntry chemical, int x, int y) {
        // 使用与RecipePreviewRenderer相同的渲染方式
        int color = chemical.color;
        if (color == -1) {
            // 默认颜色根据类型
            color = switch (chemical.type) {
                case GAS -> 0xFF88CCFF;
                case SLURRY -> 0xFFAA88FF;
                case PIGMENT -> 0xFFFF88AA;
                case INFUSE_TYPE -> 0xFF88FFAA;
                default -> 0xFFFFFFFF;
            };
        }
        
        ResourceLocation texture = getChemicalTexture(chemical.id, chemical.type);
        
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        
        int width = SLOT - 2;
        int height = SLOT - 2;
        
        if (texture != null) {
            // 有纹理：使用纹理渲染
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = 
                Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(texture);
            
            if (sprite != null && !sprite.contents().name().toString().contains("missingno")) {
                RenderSystem.setShaderTexture(0, sprite.atlasLocation());
                
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
            } else {
                // 纹理缺失：使用颜色填充
                RenderSystem.setShaderColor(r, g, b, 1.0f);
                guiGraphics.fill(x, y, x + width, y + height, 0xFFFFFFFF);
            }
        } else {
            // 无纹理：使用颜色填充
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            guiGraphics.fill(x, y, x + width, y + height, 0xFFFFFFFF);
        }
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private ResourceLocation getChemicalTexture(ResourceLocation chemicalId, RecipePreviewRenderer.ContentType type) {
        if (chemicalId == null) {
            return null;
        }
        
        try {
            if (type == RecipePreviewRenderer.ContentType.GAS) {
                mekanism.api.chemical.gas.Gas gas = mekanism.api.MekanismAPI.gasRegistry().getValue(chemicalId);
                if (gas != null && !gas.isEmptyType()) {
                    return gas.getIcon();
                }
            } else if (type == RecipePreviewRenderer.ContentType.INFUSE_TYPE) {
                mekanism.api.chemical.infuse.InfuseType infuseType = mekanism.api.MekanismAPI.infuseTypeRegistry().getValue(chemicalId);
                if (infuseType != null && !infuseType.isEmptyType()) {
                    return infuseType.getIcon();
                }
            } else if (type == RecipePreviewRenderer.ContentType.PIGMENT) {
                mekanism.api.chemical.pigment.Pigment pigment = mekanism.api.MekanismAPI.pigmentRegistry().getValue(chemicalId);
                if (pigment != null && !pigment.isEmptyType()) {
                    return pigment.getIcon();
                }
            } else if (type == RecipePreviewRenderer.ContentType.SLURRY) {
                mekanism.api.chemical.slurry.Slurry slurry = mekanism.api.MekanismAPI.slurryRegistry().getValue(chemicalId);
                if (slurry != null && !slurry.isEmptyType()) {
                    return slurry.getIcon();
                }
            }
        } catch (Exception e) {
        }
        
        return null;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 检查是否点击了化学品槽
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = (row + scroll) * COLS + col;
                    if (idx >= filteredChemicals.size()) break;
                    
                    int sx = gridX + col * SLOT_SPACING;
                    int sy = gridY + row * SLOT_SPACING;
                    
                    if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
                        selectedChemical = filteredChemicals.get(idx);
                        callback.accept(new ChemicalSelectionResult(
                            selectedChemical.id.toString(),
                            1000,
                            selectedChemical.type
                        ));
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
        int maxScroll = Math.max(0, (filteredChemicals.size() + COLS - 1) / COLS - ROWS);
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