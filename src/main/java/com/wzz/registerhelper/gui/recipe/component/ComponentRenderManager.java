package com.wzz.registerhelper.gui.recipe.component;

import com.wzz.registerhelper.gui.recipe.component.renderer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ComponentRenderManager {
    private final List<ComponentRenderer> renderers = new ArrayList<>();
    private final List<EditBox> editBoxes = new ArrayList<>();
    private final ComponentDataManager dataManager;
    private Font font;

    private final Map<Integer, ItemStack> slotItems = new HashMap<>();
    private ItemStack resultItem = ItemStack.EMPTY;

    private Consumer<Integer> onSlotLeftClick;
    private Consumer<Integer> onSlotRightClick;
    private Runnable onResultClick;
    private Consumer<Integer> onOutputSlotLeftClick;
    private Consumer<Integer> onOutputSlotRightClick;
    private Consumer<FluidSlotComponent> onFluidSlotClick;
    private Consumer<GasSlotComponent> onGasSlotClick;
    private Consumer<ChemicalSlotComponent> onChemicalSlotClick;

    public ComponentRenderManager(Font font) {
        this.font = font;
        this.dataManager = new ComponentDataManager();
    }

    public void setSlotCallbacks(Consumer<Integer> onLeftClick, Consumer<Integer> onRightClick) {
        this.onSlotLeftClick = onLeftClick;
        this.onSlotRightClick = onRightClick;
    }

    public void setOutputSlotCallbacks(Consumer<Integer> onLeftClick, Consumer<Integer> onRightClick) {
        this.onOutputSlotLeftClick = onLeftClick;
        this.onOutputSlotRightClick = onRightClick;
    }

    public void setResultCallback(Runnable onClick) {
        this.onResultClick = onClick;
    }
    
    public void setFluidSlotCallback(Consumer<FluidSlotComponent> onClick) {
        this.onFluidSlotClick = onClick;
    }
    
    public void setGasSlotCallback(Consumer<GasSlotComponent> onClick) {
        this.onGasSlotClick = onClick;
    }
    
    public void setChemicalSlotCallback(Consumer<ChemicalSlotComponent> onClick) {
        this.onChemicalSlotClick = onClick;
    }

    public void updateSlotItem(int index, ItemStack item) {
        slotItems.put(index, item.copy());
    }

    public void updateResultItem(ItemStack item) {
        this.resultItem = item.copy();
    }

    public void initializeRenderers(List<RecipeComponent> components) {
        renderers.clear();
        editBoxes.clear();

        for (RecipeComponent component : components) {
            ComponentRenderer renderer = createRenderer(component);
            if (renderer != null) {
                renderers.add(renderer);
                if (renderer instanceof NumberInputRenderer numberRenderer) {
                    editBoxes.add(numberRenderer.getEditBox());
                } else if (renderer instanceof StringInputRenderer stringRenderer) {
                    editBoxes.add(stringRenderer.getEditBox());
                }
            }
        }
    }

    public List<EditBox> getEditBoxes() {
        return new ArrayList<>(editBoxes);
    }

    public ComponentRenderer createRenderer(RecipeComponent component) {
        switch (component.getType()) {
            case SLOT:
                if (component instanceof SlotComponent slotComp) {
                    int slotIndex = slotComp.getSlotIndex();
                    String slotId = slotComp.getId().toLowerCase();
                    boolean isOutputSlot = slotId.contains("output") || 
                                          slotId.contains("main_output") || 
                                          slotId.contains("secondary_output");
                    
                    if (isOutputSlot) {
                        return new SlotRenderer(slotComp,
                                () -> slotItems.getOrDefault(slotIndex, ItemStack.EMPTY),
                                idx -> {
                                    if (onOutputSlotLeftClick != null) onOutputSlotLeftClick.accept(slotIndex);
                                },
                                idx -> {
                                    if (onOutputSlotRightClick != null) onOutputSlotRightClick.accept(slotIndex);
                                });
                    } else {
                        return new SlotRenderer(slotComp,
                                () -> slotItems.getOrDefault(slotIndex, ItemStack.EMPTY),
                                idx -> {
                                    if (onSlotLeftClick != null) onSlotLeftClick.accept(slotIndex);
                                },
                                idx -> {
                                    if (onSlotRightClick != null) onSlotRightClick.accept(slotIndex);
                                });
                    }
                }

            case FLUID_SLOT:
                if (component instanceof FluidSlotComponent fluidComp) {
                    FluidSlotRenderer renderer = new FluidSlotRenderer(fluidComp, onFluidSlotClick);
                    renderer.setOutput(fluidComp.getId().contains("output"));
                    return renderer;
                }
                
            case GAS_SLOT:
                if (component instanceof GasSlotComponent gasComp) {
                    GasSlotRenderer renderer = new GasSlotRenderer(gasComp, onGasSlotClick);
                    renderer.setOutput(gasComp.getId().contains("output"));
                    return renderer;
                }
                
            case SLURRY_SLOT:
            case PIGMENT_SLOT:
            case INFUSE_TYPE_SLOT:
                if (component instanceof ChemicalSlotComponent chemicalComp) {
                    ChemicalSlotRenderer renderer = new ChemicalSlotRenderer(chemicalComp, onChemicalSlotClick);
                    renderer.setOutput(chemicalComp.getId().contains("output"));
                    return renderer;
                }
                
            case ENERGY_SLOT:
                if (component instanceof EnergySlotComponent energyComp) {
                    return new EnergySlotRenderer(energyComp);
                }

            case LABEL:
                if (component instanceof LabelComponent labelComponent)
                    return new LabelRenderer(labelComponent);

            case NUMBER_INPUT:
                if (component instanceof NumberInputComponent numberInputComponent)
                    return new NumberInputRenderer(numberInputComponent, font, dataManager);

            case STRING_INPUT:
                if (component instanceof StringInputComponent stringInputComponent)
                    return new StringInputRenderer(stringInputComponent, font, dataManager);

            default:
                return null;
        }
    }

    public void renderAll(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.font == null)
            this.font = Minecraft.getInstance().font;
        for (ComponentRenderer renderer : renderers) {
            renderer.render(guiGraphics, font, mouseX, mouseY);
        }
    }

    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        for (ComponentRenderer renderer : renderers) {
            if (!(renderer instanceof NumberInputRenderer) &&
                    !(renderer instanceof StringInputRenderer)) {
                if (renderer.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ComponentDataManager getDataManager() {
        return dataManager;
    }

    public void clear() {
        dataManager.clear();
        slotItems.clear();
        resultItem = ItemStack.EMPTY;
        editBoxes.clear();
    }
}