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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.IForgeRegistry;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 化学品选择界面 - 用于选择 Mekanism 化学品（气体/浆液/颜料/灌注类型）
 * 继承 AbstractGridSelectorScreen，复用网格渲染和分页逻辑
 */
public class ChemicalSelectorScreen extends AbstractGridSelectorScreen<ChemicalSelectorScreen.ChemicalEntry> {

    // 化学品特定颜色
    private static final int C_CHEM_SLOT_HOVER = 0xFF351D55;
    private static final int C_CHEM_HOVER_OVERLAY = 0x30AA88FF;

    /**
     * 化学品类型枚举，对应 Mekanism 的化学品注册表
     */
    public enum ChemicalType {
        GAS("气体"),
        SLURRY("浆液"),
        PIGMENT("颜料"),
        INFUSE_TYPE("灌注类型"),
        ANY("所有化学品");

        private final String displayName;

        ChemicalType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

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

    private final List<ChemicalEntry> allChemicals = new ArrayList<>();
    private final ChemicalType chemicalType;
    private final PinyinSearchHelper<ChemicalEntry> searchHelper;
    private final Consumer<String> onChemicalIdSelected;

    public ChemicalSelectorScreen(net.minecraft.client.gui.screens.Screen parentScreen, Consumer<String> onChemicalSelected, ChemicalType chemicalType) {
        super(parentScreen, entry -> onChemicalSelected.accept(entry.id.toString()), Component.literal(chemicalType.getDisplayName() + "选择"));
        this.chemicalType = chemicalType;
        this.onChemicalIdSelected = onChemicalSelected;
        this.searchHelper = new PinyinSearchHelper<>(ChemicalEntry::getDisplayName, ChemicalEntry::getIdString);
        collectAllItems();
        searchHelper.buildCache(allChemicals);
        updateFilteredItems("");
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    // ========== 抽象方法实现 ==========

    @Override
    protected String getSearchHint() {
        return "输入化学品名或ID...";
    }

    @Override
    protected void collectAllItems() {
        allChemicals.clear();
        if (!ModList.get().isLoaded("mekanism")) {
            return;
        }
        try {
            switch (chemicalType) {
                case GAS -> loadChemicals(MekanismAPI.gasRegistry(), ChemicalType.GAS, Gas::getTint, Gas::getIcon);
                case SLURRY -> loadChemicals(MekanismAPI.slurryRegistry(), ChemicalType.SLURRY, Slurry::getTint, Slurry::getIcon);
                case PIGMENT -> loadChemicals(MekanismAPI.pigmentRegistry(), ChemicalType.PIGMENT, Pigment::getTint, Pigment::getIcon);
                case INFUSE_TYPE -> loadChemicals(MekanismAPI.infuseTypeRegistry(), ChemicalType.INFUSE_TYPE, InfuseType::getTint, InfuseType::getIcon);
                case ANY -> {
                    loadChemicals(MekanismAPI.gasRegistry(), ChemicalType.GAS, Gas::getTint, Gas::getIcon);
                    loadChemicals(MekanismAPI.slurryRegistry(), ChemicalType.SLURRY, Slurry::getTint, Slurry::getIcon);
                    loadChemicals(MekanismAPI.pigmentRegistry(), ChemicalType.PIGMENT, Pigment::getTint, Pigment::getIcon);
                    loadChemicals(MekanismAPI.infuseTypeRegistry(), ChemicalType.INFUSE_TYPE, InfuseType::getTint, InfuseType::getIcon);
                }
            }
        } catch (Exception ignored) {
        }
        allChemicals.sort(Comparator.comparing(ChemicalEntry::getIdString));
    }

    private <CHEM extends Chemical<CHEM>> void loadChemicals(
            IForgeRegistry<CHEM> registry,
            ChemicalType type,
            Function<CHEM, Integer> tintGetter,
            Function<CHEM, ResourceLocation> iconGetter) {
        for (CHEM chemical : registry.getValues()) {
            if (!chemical.isEmptyType()) {
                ResourceLocation id = registry.getKey(chemical);
                if (id != null) {
                    String name = safeGetTextComponent(chemical);
                    allChemicals.add(new ChemicalEntry(id, type, tintGetter.apply(chemical), iconGetter.apply(chemical), name));
                }
            }
        }
    }

    private String safeGetTextComponent(Chemical<?> chemical) {
        try {
            Component c = chemical.getTextComponent();
            return c.getString();
        } catch (Exception ignored) {
        }
        ResourceLocation rl = chemical.getRegistryName();
        return rl.getPath();
    }

    @Override
    protected void updateFilteredItems(String searchText) {
        filteredItems.clear();
        if (searchText.isEmpty()) {
            filteredItems.addAll(allChemicals);
        } else {
            String lowerSearch = searchText.toLowerCase();
            for (ChemicalEntry entry : allChemicals) {
                if (entry.getIdString().toLowerCase().contains(lowerSearch)) {
                    filteredItems.add(entry);
                    continue;
                }
                if (entry.getDisplayName().toLowerCase().contains(lowerSearch)) {
                    filteredItems.add(entry);
                    continue;
                }
                if (searchHelper.matches(entry, searchText)) {
                    filteredItems.add(entry);
                }
            }
        }
        calculateMaxPage();
        updateButtons();
    }

    @Override
    protected void renderItem(GuiGraphics g, ChemicalEntry entry, int x, int y) {
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
                TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(entry.icon);
                if (sprite != null && !sprite.contents().name().toString().contains("missingno")) {
                    RenderSystem.setShaderTexture(0, sprite.atlasLocation());
                    renderTiledSprite(g, x, y, sprite);
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        RenderSystem.setShaderColor(r, gr, b, 1.0f);
        g.fill(x, y, x + 16, y + 16, 0xFFFFFFFF);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    protected List<Component> getItemTooltip(ChemicalEntry entry) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(entry.getDisplayName()));
        tooltip.add(Component.literal("§7" + entry.id));
        tooltip.add(Component.literal("§8类型: " + entry.type.getDisplayName()));
        return tooltip;
    }

    @Override
    protected void onItemSelected(ChemicalEntry entry) {
        onChemicalIdSelected.accept(entry.id.toString());
    }

    // ========== 覆盖的颜色方法 ==========

    @Override
    protected int getSlotHoverColor() {
        return C_CHEM_SLOT_HOVER;
    }

    @Override
    protected int getSlotHoverOverlayColor() {
        return C_CHEM_HOVER_OVERLAY;
    }

    // ========== 私有辅助方法 ==========

    private void renderTiledSprite(GuiGraphics guiGraphics, int xPosition, int yPosition, TextureAtlasSprite sprite) {
        int textureSize = 16;
        int xTileCount = 16 / textureSize;
        int xRemainder = 0;
        int yTileCount = 16 / textureSize;
        int yRemainder = 0;
        int yStart = yPosition + 16;

        float uLocalMin = sprite.getU0();
        float uMax = sprite.getU1();
        float vMin = sprite.getV0();
        float vLocalMax = sprite.getV1();
        float uDif = uMax - uLocalMin;
        float vDif = vLocalMax - vMin;

        RenderSystem.enableBlend();
        BufferBuilder vertexBuffer = Tesselator.getInstance().getBuilder();
        vertexBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix4f = guiGraphics.pose().last().pose();

        for (int xTile = 0; xTile <= xTileCount; xTile++) {
            int tileWidth = (xTile == xTileCount) ? xRemainder : textureSize;
            if (tileWidth == 0) break;
            int tileX = xPosition + (xTile * textureSize);
            int maskRight = 0;
            int shiftedX = tileX + textureSize - maskRight;
            float uLocalDif = uDif * maskRight / textureSize;
            float uLocalMax = uMax - uLocalDif;

            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                int tileHeight = (yTile == yTileCount) ? yRemainder : textureSize;
                if (tileHeight == 0) break;
                int tileY = yStart - ((yTile + 1) * textureSize);
                int maskTop = 0;
                float vLocalDif = vDif * maskTop / textureSize;
                float vLocalMin = vMin + vLocalDif;

                vertexBuffer.vertex(matrix4f, tileX, tileY + textureSize, 0).uv(uLocalMin, vLocalMax).endVertex();
                vertexBuffer.vertex(matrix4f, shiftedX, tileY + textureSize, 0).uv(uLocalMax, vLocalMax).endVertex();
                vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
            }
        }

        BufferUploader.drawWithShader(vertexBuffer.end());
        RenderSystem.disableBlend();
    }
}