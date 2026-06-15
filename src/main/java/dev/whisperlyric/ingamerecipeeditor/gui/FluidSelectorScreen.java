package dev.whisperlyric.ingamerecipeeditor.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class FluidSelectorScreen extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int HEADER_HEIGHT = 60;
    private static final int FOOTER_HEIGHT = 28;

    private int guiWidth, guiHeight;
    private int fluidsPerRow, fluidsPerPage;

    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onFluidSelected;

    private EditBox searchBox;
    private Button prevPageButton, nextPageButton, cancelButton;

    private final List<ResourceLocation> allFluids = new ArrayList<>();
    private final List<ResourceLocation> filteredFluids = new ArrayList<>();
    private final PinyinSearchHelper<ResourceLocation> searchHelper;

    private int currentPage = 0;
    private int maxPage = 0;
    private int leftPos, topPos;

    private static final int C_BG_OUTER    = 0xFF0F0F0F;
    private static final int C_BG_MAIN     = 0xFF252525;
    private static final int C_TITLE_BAR   = 0xFF1A3A6A;
    private static final int C_PANEL       = 0xFF1A1A1A;
    private static final int C_SLOT_EMPTY  = 0xFF141414;
    private static final int C_SLOT_HOVER  = 0xFF1D3555;
    private static final int C_DIVIDER     = 0xFF333333;
    private static final int C_FOOTER      = 0xFF1E1E1E;
    private static final int C_TEXT        = 0xFFE0E0E0;
    private static final int C_TEXT_DIM    = 0xFF888888;

    public FluidSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onFluidSelected) {
        super(Component.literal("选择流体"));
        this.parentScreen = parentScreen;
        this.onFluidSelected = onFluidSelected;
        this.searchHelper = new PinyinSearchHelper<>(
                fluidId -> {
                    Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                    if (fluid != null && fluid != Fluids.EMPTY) {
                        return new FluidStack(fluid, 1000).getDisplayName().getString();
                    }
                    return fluidId != null ? fluidId.getPath() : "";
                },
                fluidId -> fluidId != null ? fluidId.toString() : ""
        );
        this.fluidsPerRow = 9;
        this.fluidsPerPage = 54;
        collectAllFluids();
        searchHelper.buildCache(allFluids);
        updateFilteredFluids("");
    }

    private void collectAllFluids() {
        allFluids.clear();
        for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
            if (fluid != Fluids.EMPTY) {
                ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
                if (fluidId != null) {
                    allFluids.add(fluidId);
                }
            }
        }
        allFluids.sort((a, b) -> a.toString().compareTo(b.toString()));
    }

    private void updateFilteredFluids(String text) {
        filteredFluids.clear();
        if (text.isEmpty()) {
            filteredFluids.addAll(allFluids);
        } else {
            String lowerSearch = text.toLowerCase();
            for (ResourceLocation fluidId : allFluids) {
                if (fluidId.toString().toLowerCase().contains(lowerSearch)) {
                    filteredFluids.add(fluidId);
                    continue;
                }
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    String displayName = new FluidStack(fluid, 1000).getDisplayName().getString();
                    if (displayName.toLowerCase().contains(lowerSearch)) {
                        filteredFluids.add(fluidId);
                        continue;
                    }
                }
                if (searchHelper.matches(fluidId, text)) {
                    filteredFluids.add(fluidId);
                }
            }
        }
        maxPage = fluidsPerPage > 0 ? Math.max(0, (filteredFluids.size() - 1) / fluidsPerPage) : 0;
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    @Override
    protected void init() {
        int maxW = Math.min(this.width - 30, 400);
        int maxH = Math.min(this.height - 50, 400);
        maxW = Math.max(maxW, 200);
        maxH = Math.max(maxH, 200);

        this.fluidsPerRow  = Math.max(4, (maxW - 20) / SLOT_SIZE);
        int gridRows      = Math.max(3, (maxH - HEADER_HEIGHT - FOOTER_HEIGHT) / SLOT_SIZE);
        this.fluidsPerPage = this.fluidsPerRow * gridRows;
        this.guiWidth     = 20 + this.fluidsPerRow * SLOT_SIZE;
        this.guiHeight    = HEADER_HEIGHT + gridRows * SLOT_SIZE + FOOTER_HEIGHT;

        this.leftPos = (this.width  - guiWidth)  / 2;
        this.topPos  = (this.height - guiHeight) / 2;

        updateFilteredFluids(searchBox != null ? searchBox.getValue() : "");

        searchBox = new EditBox(this.font, leftPos + 10, topPos + 32, guiWidth - 20, 18, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入流体名或ID..."));
        searchBox.setResponder(this::updateFilteredFluids);
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

        int tb = topPos + 28;
        g.fill(leftPos, topPos, leftPos + guiWidth, tb, C_TITLE_BAR);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + 1, 0xFF4A7ACF);
        g.drawCenteredString(this.font, "选择流体", leftPos + guiWidth / 2, topPos + 9, C_TEXT);

        g.fill(leftPos, tb, leftPos + guiWidth, topPos + HEADER_HEIGHT - 2, 0xFF1E1E1E);
        g.fill(leftPos + 5, topPos + HEADER_HEIGHT - 3, leftPos + guiWidth - 5, topPos + HEADER_HEIGHT - 2, C_DIVIDER);

        int gridTop = topPos + HEADER_HEIGHT;
        int gridBot = topPos + guiHeight - FOOTER_HEIGHT;
        g.fill(leftPos, gridTop, leftPos + guiWidth, gridBot, C_PANEL);

        g.fill(leftPos, gridBot, leftPos + guiWidth, topPos + guiHeight, C_FOOTER);
        g.fill(leftPos + 5, gridBot, leftPos + guiWidth - 5, gridBot + 1, C_DIVIDER);

        renderFluidGrid(g, mouseX, mouseY);
        searchBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        String pageInfo = String.format("§7第 %d / %d 页  (%d 个)", currentPage + 1, maxPage + 1, filteredFluids.size());
        g.drawCenteredString(this.font, pageInfo, leftPos + guiWidth / 2, topPos + 81, C_TEXT_DIM);

        renderFluidTooltip(g, mouseX, mouseY);
    }

    private void renderFluidGrid(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx    = currentPage * fluidsPerPage;
        int endIdx      = Math.min(startIdx + fluidsPerPage, filteredFluids.size());
        int gridStartX  = leftPos + 10;
        int gridStartY  = topPos + HEADER_HEIGHT + 1;
        int totalSlots  = fluidsPerPage;

        for (int i = 0; i < totalSlots; i++) {
            int sx = gridStartX + (i % fluidsPerRow) * SLOT_SIZE;
            int sy = gridStartY + (i / fluidsPerRow) * SLOT_SIZE;
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_SLOT_EMPTY);
            g.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF0A0A0A);
            g.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF0A0A0A);
            g.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF3A3A3A);
            g.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF3A3A3A);
        }

        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx  = gridStartX + (rel % fluidsPerRow) * SLOT_SIZE;
            int sy  = gridStartY + (rel / fluidsPerRow) * SLOT_SIZE;
            boolean hover = mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;

            if (hover) {
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, C_SLOT_HOVER);
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x30AACCFF);
            }

            ResourceLocation fluidId = filteredFluids.get(i);
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid != null && fluid != Fluids.EMPTY) {
                renderFluid(g, fluid, sx + 1, sy + 1, 16, 16);
            }
        }
    }

    private void renderFluid(GuiGraphics g, Fluid fluid, int x, int y, int width, int height) {
        FluidStack fluidStack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation tex = ext.getStillTexture(fluidStack);
        if (tex == null) return;
        
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(tex);
        int tint = ext.getTintColor(fluidStack);
        float r = ((tint >> 16) & 0xFF) / 255.0f;
        float gr = ((tint >> 8) & 0xFF) / 255.0f;
        float b = (tint & 0xFF) / 255.0f;
        
        RenderSystem.setShaderColor(r, gr, b, 1.0f);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        g.blit(x, y, 0, width, height, sprite);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderFluidTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx   = currentPage * fluidsPerPage;
        int endIdx     = Math.min(startIdx + fluidsPerPage, filteredFluids.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + HEADER_HEIGHT + 1;

        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx  = gridStartX + (rel % fluidsPerRow) * SLOT_SIZE;
            int sy  = gridStartY + (rel / fluidsPerRow) * SLOT_SIZE;

            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                ResourceLocation fluidId = filteredFluids.get(i);
                List<Component> tt = new ArrayList<>();
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    tt.add(new FluidStack(fluid, 1000).getDisplayName());
                }
                tt.add(Component.literal("§7" + fluidId));
                g.renderTooltip(this.font, tt, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startIdx   = currentPage * fluidsPerPage;
            int endIdx     = Math.min(startIdx + fluidsPerPage, filteredFluids.size());
            int gridStartX = leftPos + 10;
            int gridStartY = topPos + HEADER_HEIGHT + 1;
            for (int i = startIdx; i < endIdx; i++) {
                int rel = i - startIdx;
                int sx  = gridStartX + (rel % fluidsPerRow) * SLOT_SIZE;
                int sy  = gridStartY + (rel / fluidsPerRow) * SLOT_SIZE;
                if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                    onFluidSelected.accept(filteredFluids.get(i));
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