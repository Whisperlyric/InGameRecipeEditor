package dev.whisperlyric.ingamerecipeeditor.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 流体选择界面
 * 继承 AbstractGridSelectorScreen，复用网格渲染和分页逻辑
 */
@OnlyIn(Dist.CLIENT)
public class FluidSelectorScreen extends AbstractGridSelectorScreen<ResourceLocation> {

    // 流体特定颜色
    private static final int C_FLUID_SLOT_HOVER = 0xFF1D3555;
    private static final int C_FLUID_HOVER_OVERLAY = 0x30AACCFF;

    private final List<ResourceLocation> allFluids = new ArrayList<>();
    private final PinyinSearchHelper<ResourceLocation> searchHelper;
    private final Consumer<ResourceLocation> onFluidIdSelected;

    public FluidSelectorScreen(net.minecraft.client.gui.screens.Screen parentScreen, Consumer<ResourceLocation> onFluidSelected) {
        super(parentScreen, onFluidSelected, Component.literal("选择流体"));
        this.onFluidIdSelected = onFluidSelected;
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
        collectAllItems();
        searchHelper.buildCache(allFluids);
        updateFilteredItems("");
    }

    // ========== 抽象方法实现 ==========

    @Override
    protected String getSearchHint() {
        return "输入流体名或ID...";
    }

    @Override
    protected void collectAllItems() {
        allFluids.clear();
        for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
            if (fluid != Fluids.EMPTY) {
                ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
                if (fluidId != null) {
                    allFluids.add(fluidId);
                }
            }
        }
        allFluids.sort(Comparator.comparing(ResourceLocation::toString));
    }

    @Override
    protected void updateFilteredItems(String searchText) {
        filteredItems.clear();
        if (searchText.isEmpty()) {
            filteredItems.addAll(allFluids);
        } else {
            String lowerSearch = searchText.toLowerCase();
            for (ResourceLocation fluidId : allFluids) {
                if (fluidId.toString().toLowerCase().contains(lowerSearch)) {
                    filteredItems.add(fluidId);
                    continue;
                }
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    String displayName = new FluidStack(fluid, 1000).getDisplayName().getString();
                    if (displayName.toLowerCase().contains(lowerSearch)) {
                        filteredItems.add(fluidId);
                        continue;
                    }
                }
                if (searchHelper.matches(fluidId, searchText)) {
                    filteredItems.add(fluidId);
                }
            }
        }
        calculateMaxPage();
        updateButtons();
    }

    @Override
    protected void renderItem(GuiGraphics g, ResourceLocation fluidId, int x, int y) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) return;

        FluidStack fluidStack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation tex = ext.getStillTexture(fluidStack);
        if (tex == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
        int tint = ext.getTintColor(fluidStack);
        float r = ((tint >> 16) & 0xFF) / 255.0f;
        float gr = ((tint >> 8) & 0xFF) / 255.0f;
        float b = (tint & 0xFF) / 255.0f;

        RenderSystem.setShaderColor(r, gr, b, 1.0f);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        g.blit(x, y, 0, 16, 16, sprite);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    protected List<Component> getItemTooltip(ResourceLocation fluidId) {
        List<Component> tooltip = new ArrayList<>();
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid != null && fluid != Fluids.EMPTY) {
            tooltip.add(new FluidStack(fluid, 1000).getDisplayName());
        }
        tooltip.add(Component.literal("§7" + fluidId));
        return tooltip;
    }

    @Override
    protected void onItemSelected(ResourceLocation fluidId) {
        onFluidIdSelected.accept(fluidId);
    }

    // ========== 覆盖的颜色方法 ==========

    @Override
    protected int getSlotHoverColor() {
        return C_FLUID_SLOT_HOVER;
    }

    @Override
    protected int getSlotHoverOverlayColor() {
        return C_FLUID_HOVER_OVERLAY;
    }
}