package dev.whisperlyric.ingamerecipeeditor.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.whisperlyric.ingamerecipeeditor.util.PinyinSearchHelper;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.slurry.Slurry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 化学品选择界面 - 用于选择 Mekanism 化学品（气体/浆液/颜料/浸染类型）
 * 移植自 src-old 的 ChemicalGridSelectionScreen，并适配当前模组的视觉风格
 */
@OnlyIn(Dist.CLIENT)
public class ChemicalSelectorScreen extends Screen {

    /**
     * 化学品类型枚举，对应 Mekanism 的化学品注册表
     */
    public enum ChemicalType {
        GAS("气体"),
        SLURRY("浆液"),
        PIGMENT("颜料"),
        INFUSE_TYPE("浸染类型"),
        ANY("所有化学品");

        private final String displayName;

        ChemicalType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final int SLOT_SIZE = 18;
    private static final int HEADER_HEIGHT = 96;
    private static final int FOOTER_HEIGHT = 28;

    private int guiWidth, guiHeight;
    private int chemicalsPerRow, chemicalsPerPage;

    private final Screen parentScreen;
    private final Consumer<String> onChemicalSelected;
    private final ChemicalType chemicalType;

    private EditBox searchBox;
    private Button prevPageButton, nextPageButton, cancelButton;

    private final List<ChemicalEntry> allChemicals = new ArrayList<>();
    private final List<ChemicalEntry> filteredChemicals = new ArrayList<>();
    private final PinyinSearchHelper<ChemicalEntry> searchHelper;

    private int currentPage = 0;
    private int maxPage = 0;
    private int leftPos, topPos;

    private static final int C_BG_OUTER    = 0xFF0F0F0F;
    private static final int C_BG_MAIN     = 0xFF252525;
    private static final int C_TITLE_BAR   = 0xFF3A1A6A;
    private static final int C_PANEL       = 0xFF1A1A1A;
    private static final int C_SLOT_EMPTY  = 0xFF141414;
    private static final int C_SLOT_HOVER  = 0xFF351D55;
    private static final int C_DIVIDER     = 0xFF333333;
    private static final int C_FOOTER      = 0xFF1E1E1E;
    private static final int C_TEXT        = 0xFFE0E0E0;
    private static final int C_TEXT_DIM    = 0xFF888888;

    /**
     * 化学品条目
     */
    public static class ChemicalEntry {
        final ResourceLocation id;
        final ChemicalType type;
        final int color;
        final ResourceLocation icon;
        final String displayName;

        ChemicalEntry(ResourceLocation id, ChemicalType type, int color, ResourceLocation icon, String displayName) {
            this.id = id;
            this.type = type;
            this.color = color;
            this.icon = icon;
            this.displayName = displayName;
        }

        String getDisplayName() {
            return displayName;
        }

        String getIdString() {
            return id != null ? id.toString() : "";
        }
    }

    public ChemicalSelectorScreen(Screen parentScreen, Consumer<String> onChemicalSelected, ChemicalType chemicalType) {
        super(Component.literal(chemicalType.getDisplayName() + "选择"));
        this.parentScreen = parentScreen;
        this.onChemicalSelected = onChemicalSelected;
        this.chemicalType = chemicalType;
        this.searchHelper = new PinyinSearchHelper<>(
                ChemicalEntry::getDisplayName,
                ChemicalEntry::getIdString
        );
        this.chemicalsPerRow = 9;
        this.chemicalsPerPage = 54;
        collectAllChemicals();
        searchHelper.buildCache(allChemicals);
        updateFilteredChemicals("");
    }

    private void collectAllChemicals() {
        allChemicals.clear();
        if (!ModList.get().isLoaded("mekanism")) {
            return;
        }
        try {
            switch (chemicalType) {
                case GAS -> loadGases();
                case SLURRY -> loadSlurries();
                case PIGMENT -> loadPigments();
                case INFUSE_TYPE -> loadInfuseTypes();
                case ANY -> loadAllChemicals();
            }
        } catch (Exception ignored) {
            // 加载失败时保持空列表
        }
        allChemicals.sort((a, b) -> a.getIdString().compareTo(b.getIdString()));
    }

    private void loadGases() {
        for (Gas gas : MekanismAPI.gasRegistry().getValues()) {
            if (!gas.isEmptyType()) {
                ResourceLocation id = MekanismAPI.gasRegistry().getKey(gas);
                if (id != null) {
                    String name = safeGetTextComponent(gas);
                    allChemicals.add(new ChemicalEntry(id, ChemicalType.GAS, gas.getTint(), gas.getIcon(), name));
                }
            }
        }
    }

    private void loadSlurries() {
        for (Slurry slurry : MekanismAPI.slurryRegistry().getValues()) {
            if (!slurry.isEmptyType()) {
                ResourceLocation id = MekanismAPI.slurryRegistry().getKey(slurry);
                if (id != null) {
                    String name = safeGetTextComponent(slurry);
                    allChemicals.add(new ChemicalEntry(id, ChemicalType.SLURRY, slurry.getTint(), slurry.getIcon(), name));
                }
            }
        }
    }

    private void loadPigments() {
        for (Pigment pigment : MekanismAPI.pigmentRegistry().getValues()) {
            if (!pigment.isEmptyType()) {
                ResourceLocation id = MekanismAPI.pigmentRegistry().getKey(pigment);
                if (id != null) {
                    String name = safeGetTextComponent(pigment);
                    allChemicals.add(new ChemicalEntry(id, ChemicalType.PIGMENT, pigment.getTint(), pigment.getIcon(), name));
                }
            }
        }
    }

    private void loadInfuseTypes() {
        for (InfuseType infuseType : MekanismAPI.infuseTypeRegistry().getValues()) {
            if (!infuseType.isEmptyType()) {
                ResourceLocation id = MekanismAPI.infuseTypeRegistry().getKey(infuseType);
                if (id != null) {
                    String name = safeGetTextComponent(infuseType);
                    allChemicals.add(new ChemicalEntry(id, ChemicalType.INFUSE_TYPE, infuseType.getTint(), infuseType.getIcon(), name));
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

    private String safeGetTextComponent(Chemical<?> chemical) {
        try {
            Component c = chemical.getTextComponent();
            if (c != null) {
                return c.getString();
            }
        } catch (Exception ignored) {
        }
        ResourceLocation rl = chemical.getRegistryName();
        return rl != null ? rl.getPath() : "unknown";
    }

    private void updateFilteredChemicals(String text) {
        filteredChemicals.clear();
        if (text.isEmpty()) {
            filteredChemicals.addAll(allChemicals);
        } else {
            String lowerSearch = text.toLowerCase();
            for (ChemicalEntry entry : allChemicals) {
                if (entry.getIdString().toLowerCase().contains(lowerSearch)) {
                    filteredChemicals.add(entry);
                    continue;
                }
                if (entry.getDisplayName().toLowerCase().contains(lowerSearch)) {
                    filteredChemicals.add(entry);
                    continue;
                }
                if (searchHelper.matches(entry, text)) {
                    filteredChemicals.add(entry);
                }
            }
        }
        maxPage = chemicalsPerPage > 0 ? Math.max(0, (filteredChemicals.size() - 1) / chemicalsPerPage) : 0;
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    @Override
    protected void init() {
        int maxW = Math.min(this.width - 30, 400);
        int maxH = Math.min(this.height - 50, 400);
        maxW = Math.max(maxW, 200);
        maxH = Math.max(maxH, 200);

        this.chemicalsPerRow  = Math.max(4, (maxW - 20) / SLOT_SIZE);
        int gridRows      = Math.max(3, (maxH - HEADER_HEIGHT - FOOTER_HEIGHT) / SLOT_SIZE);
        this.chemicalsPerPage = this.chemicalsPerRow * gridRows;
        this.guiWidth     = 20 + this.chemicalsPerRow * SLOT_SIZE;
        this.guiHeight    = HEADER_HEIGHT + gridRows * SLOT_SIZE + FOOTER_HEIGHT;

        this.leftPos = (this.width  - guiWidth)  / 2;
        this.topPos  = (this.height - guiHeight) / 2;

        updateFilteredChemicals(searchBox != null ? searchBox.getValue() : "");

        searchBox = new EditBox(this.font, leftPos + 10, topPos + 6, guiWidth - 20, 18, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入化学品名或ID..."));
        searchBox.setResponder(this::updateFilteredChemicals);
        addWidget(searchBox);

        prevPageButton = addRenderableWidget(Button.builder(Component.literal("◀"), b -> previousPage())
                .bounds(leftPos + 4, topPos + guiHeight - 22, 24, 18).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal("▶"), b -> nextPage())
                .bounds(leftPos + guiWidth - 28, topPos + guiHeight - 22, 24, 18).build());
        cancelButton = addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(leftPos + (guiWidth - 50) / 2, topPos + guiHeight - 22, 50, 18).build());

        updateButtons();
    }

    private void updateButtons() {
        if (prevPageButton != null) prevPageButton.active = currentPage > 0;
        if (nextPageButton != null) nextPageButton.active = currentPage < maxPage;
    }

    private void previousPage() { if (currentPage > 0) { currentPage--; updateButtons(); } }
    private void nextPage()     { if (currentPage < maxPage) { currentPage++; updateButtons(); } }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        g.fill(leftPos - 1, topPos - 1, leftPos + guiWidth + 1, topPos + guiHeight + 1, C_BG_OUTER);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, C_BG_MAIN);

        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + HEADER_HEIGHT - 2, 0xFF1E1E1E);
        g.fill(leftPos + 5, topPos + HEADER_HEIGHT - 3, leftPos + guiWidth - 5, topPos + HEADER_HEIGHT - 2, C_DIVIDER);

        int gridTop = topPos + HEADER_HEIGHT;
        int gridBot = topPos + guiHeight - FOOTER_HEIGHT;
        g.fill(leftPos, gridTop, leftPos + guiWidth, gridBot, C_PANEL);

        g.fill(leftPos, gridBot, leftPos + guiWidth, topPos + guiHeight, C_FOOTER);
        g.fill(leftPos + 5, gridBot, leftPos + guiWidth - 5, gridBot + 1, C_DIVIDER);

        renderChemicalGrid(g, mouseX, mouseY);
        searchBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        String pageInfo = String.format("§7第 %d / %d 页  (%d 个)", currentPage + 1, maxPage + 1, filteredChemicals.size());
        g.drawCenteredString(this.font, pageInfo, leftPos + guiWidth / 2, topPos + HEADER_HEIGHT - 14, C_TEXT_DIM);

        renderChemicalTooltip(g, mouseX, mouseY);
    }

    private void renderChemicalGrid(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx    = currentPage * chemicalsPerPage;
        int endIdx      = Math.min(startIdx + chemicalsPerPage, filteredChemicals.size());
        int gridStartX  = leftPos + 10;
        int gridStartY  = topPos + HEADER_HEIGHT + 1;
        int totalSlots  = chemicalsPerPage;

        for (int i = 0; i < totalSlots; i++) {
            int sx = gridStartX + (i % chemicalsPerRow) * SLOT_SIZE;
            int sy = gridStartY + (i / chemicalsPerRow) * SLOT_SIZE;
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_SLOT_EMPTY);
            g.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF0A0A0A);
            g.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF0A0A0A);
            g.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF3A3A3A);
            g.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF3A3A3A);
        }

        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx  = gridStartX + (rel % chemicalsPerRow) * SLOT_SIZE;
            int sy  = gridStartY + (rel / chemicalsPerRow) * SLOT_SIZE;
            boolean hover = mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;

            if (hover) {
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, C_SLOT_HOVER);
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x30AA88FF);
            }

            ChemicalEntry entry = filteredChemicals.get(i);
            renderChemicalSlot(g, entry, sx + 1, sy + 1, 16, 16);
        }
    }

    /**
     * 渲染单个化学品槽位（颜色 + 纹理）
     */
    private void renderChemicalSlot(GuiGraphics g, ChemicalEntry entry, int x, int y, int width, int height) {
        int color = entry.color;
        if (color == -1) {
            color = switch (entry.type) {
                case GAS -> 0xFF88CCFF;
                case SLURRY -> 0xFFAA88FF;
                case PIGMENT -> 0xFFFF88AA;
                case INFUSE_TYPE -> 0xFF88FFAA;
                default -> 0xFFFFFFFF;
            };
        }

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float gr = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        if (entry.icon != null) {
            try {
                RenderSystem.setShaderColor(r, gr, b, 1.0f);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(entry.icon);
                if (sprite != null && !sprite.contents().name().toString().contains("missingno")) {
                    RenderSystem.setShaderTexture(0, sprite.atlasLocation());
                    renderTiledSprite(g, x, y, width, height, sprite);
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    return;
                }
            } catch (Exception ignored) {
                // 纹理渲染失败，回退到颜色填充
            }
        }

        // 无纹理或纹理缺失：使用颜色填充
        RenderSystem.setShaderColor(r, gr, b, 1.0f);
        g.fill(x, y, x + width, y + height, 0xFFFFFFFF);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * 平铺渲染纹理精灵（与 ChemicalSlotRenderer 一致的实现）
     */
    private void renderTiledSprite(GuiGraphics guiGraphics, int xPosition, int yPosition, int desiredWidth, int desiredHeight, TextureAtlasSprite sprite) {
        int textureSize = 16;
        int xTileCount = desiredWidth / textureSize;
        int xRemainder = desiredWidth - (xTileCount * textureSize);
        int yTileCount = desiredHeight / textureSize;
        int yRemainder = desiredHeight - (yTileCount * textureSize);
        int yStart = yPosition + desiredHeight;

        float uMin = sprite.getU0();
        float uMax = sprite.getU1();
        float vMin = sprite.getV0();
        float vMax = sprite.getV1();
        float uDif = uMax - uMin;
        float vDif = vMax - vMin;

        RenderSystem.enableBlend();
        BufferBuilder vertexBuffer = Tesselator.getInstance().getBuilder();
        vertexBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix4f = guiGraphics.pose().last().pose();

        for (int xTile = 0; xTile <= xTileCount; xTile++) {
            int tileWidth = (xTile == xTileCount) ? xRemainder : textureSize;
            if (tileWidth == 0) break;
            int tileX = xPosition + (xTile * textureSize);
            int maskRight = textureSize - tileWidth;
            int shiftedX = tileX + textureSize - maskRight;
            float uLocalDif = uDif * maskRight / textureSize;
            float uLocalMin = uMin;
            float uLocalMax = uMax - uLocalDif;

            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                int tileHeight = (yTile == yTileCount) ? yRemainder : textureSize;
                if (tileHeight == 0) break;
                int tileY = yStart - ((yTile + 1) * textureSize);
                int maskTop = textureSize - tileHeight;
                float vLocalDif = vDif * maskTop / textureSize;
                float vLocalMin = vMin + vLocalDif;
                float vLocalMax = vMax;

                vertexBuffer.vertex(matrix4f, tileX, tileY + textureSize, 0).uv(uLocalMin, vLocalMax).endVertex();
                vertexBuffer.vertex(matrix4f, shiftedX, tileY + textureSize, 0).uv(uLocalMax, vLocalMax).endVertex();
                vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
            }
        }

        BufferUploader.drawWithShader(vertexBuffer.end());
        RenderSystem.disableBlend();
    }

    private void renderChemicalTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx   = currentPage * chemicalsPerPage;
        int endIdx     = Math.min(startIdx + chemicalsPerPage, filteredChemicals.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + HEADER_HEIGHT + 1;

        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx  = gridStartX + (rel % chemicalsPerRow) * SLOT_SIZE;
            int sy  = gridStartY + (rel / chemicalsPerRow) * SLOT_SIZE;

            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                ChemicalEntry entry = filteredChemicals.get(i);
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal(entry.getDisplayName()));
                tt.add(Component.literal("§7" + entry.id));
                tt.add(Component.literal("§8类型: " + entry.type.getDisplayName()));
                g.renderTooltip(this.font, tt, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startIdx   = currentPage * chemicalsPerPage;
            int endIdx     = Math.min(startIdx + chemicalsPerPage, filteredChemicals.size());
            int gridStartX = leftPos + 10;
            int gridStartY = topPos + HEADER_HEIGHT + 1;
            for (int i = startIdx; i < endIdx; i++) {
                int rel = i - startIdx;
                int sx  = gridStartX + (rel % chemicalsPerRow) * SLOT_SIZE;
                int sy  = gridStartY + (rel / chemicalsPerRow) * SLOT_SIZE;
                if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                    onChemicalSelected.accept(filteredChemicals.get(i).id.toString());
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) previousPage(); else nextPage();
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
}
