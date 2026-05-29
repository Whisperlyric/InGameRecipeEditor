package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.integration.jei.IJEIGhostTarget;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Consumer;

public class JEISelectionScreen extends AbstractContainerScreen<JEISelectionContainer> implements IJEIGhostTarget {
    
    private final Screen parent;
    private final SlotSelectionScreen.SlotType slotType;
    private final Consumer<SlotSelectionScreen.SlotSelectionResult> callback;
    private final boolean gasOnly;
    
    private Object selectedValue = null;
    
    public boolean switchingToJEI = false;
    
    private Button btnConfirm;
    private Button btnCancel;
    
    private int ghostSlotX;
    private int ghostSlotY;
    private int ghostSlotSize = 18;
    
    public int getGhostSlotX() { return ghostSlotX; }
    public int getGhostSlotY() { return ghostSlotY; }
    public int getGhostSlotSize() { return ghostSlotSize; }
    public SlotSelectionScreen.SlotType getSlotType() { return slotType; }
    
    public JEISelectionScreen(Screen parent, SlotSelectionScreen.SlotType slotType, Consumer<SlotSelectionScreen.SlotSelectionResult> callback) {
        this(parent, slotType, callback, false);
    }
    
    public JEISelectionScreen(Screen parent, SlotSelectionScreen.SlotType slotType, Consumer<SlotSelectionScreen.SlotSelectionResult> callback, boolean gasOnly) {
        super(new JEISelectionContainer(0, Minecraft.getInstance().player.getInventory()), 
              Minecraft.getInstance().player.getInventory(), 
              Component.literal(""));
        this.parent = parent;
        this.slotType = slotType;
        this.callback = callback;
        this.gasOnly = gasOnly;
        this.imageWidth = 176;
        this.imageHeight = 80;
    }
    
    @Override
    protected void init() {
        super.init();
        
        ghostSlotX = this.leftPos + this.imageWidth / 2 - ghostSlotSize / 2;
        ghostSlotY = this.topPos + 20;
        
        int centerX = this.leftPos + this.imageWidth / 2;
        int buttonY = ghostSlotY + ghostSlotSize + 20;
        
        btnConfirm = Button.builder(
            Component.literal("确认选择"),
            button -> confirmSelection()
        ).bounds(centerX - 105, buttonY, 100, 20).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> {
                callback.accept(new SlotSelectionScreen.SlotSelectionResult(
                    SlotSelectionScreen.SlotSelectionResult.SelectionType.CANCELLED, null, 0));
                Minecraft.getInstance().setScreen(parent);
            }
        ).bounds(centerX + 5, buttonY, 100, 20).build();
        
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
        
        updateButtonStates();
    }
    
    private void updateButtonStates() {
        btnConfirm.active = selectedValue != null;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0101010);
        
        String title;
        if (slotType == SlotSelectionScreen.SlotType.ITEM) {
            title = "从JEI拖入物品";
        } else if (slotType == SlotSelectionScreen.SlotType.FLUID) {
            title = "从JEI拖入流体";
        } else if (slotType == SlotSelectionScreen.SlotType.GAS) {
            title = "选择气体(GasOnly)";
        } else if (slotType == SlotSelectionScreen.SlotType.SLURRY) {
            title = "选择浆液(SlurryOnly)";
        } else if (slotType == SlotSelectionScreen.SlotType.PIGMENT) {
            title = "选择颜料(PigmentOnly)";
        } else if (slotType == SlotSelectionScreen.SlotType.INFUSE_TYPE) {
            title = "选择灌注类型(InfuseTypeOnly)";
        } else {
            title = "选择化学品";
        }
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal(title),
            this.leftPos + this.imageWidth / 2,
            this.topPos + 5,
            0xFFFFFF
        );
        
        renderGhostSlot(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        if (selectedValue != null) {
            String selectedName = getSelectedName();
            guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                Component.literal("§e已选择: §f" + selectedName),
                this.leftPos + this.imageWidth / 2,
                ghostSlotY + ghostSlotSize + 10,
                0xFFFFFF
            );
        }
        
        if (mouseX >= ghostSlotX && mouseX < ghostSlotX + ghostSlotSize &&
            mouseY >= ghostSlotY && mouseY < ghostSlotY + ghostSlotSize) {
            if (selectedValue != null) {
                renderTooltip(guiGraphics, mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(Minecraft.getInstance().font, 
                    Component.literal("从JEI拖入物品"), mouseX, mouseY);
            }
        }
    }
    
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }
    
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
    
    private void renderGhostSlot(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean isMouseOver = mouseX >= ghostSlotX && mouseX < ghostSlotX + ghostSlotSize &&
                            mouseY >= ghostSlotY && mouseY < ghostSlotY + ghostSlotSize;
        
        int bgColor = selectedValue != null ? 0xFF2A5A2A : (isMouseOver ? 0xFF1D3555 : 0xFF141414);
        guiGraphics.fill(ghostSlotX, ghostSlotY, ghostSlotX + ghostSlotSize, ghostSlotY + ghostSlotSize, bgColor);
        
        guiGraphics.fill(ghostSlotX, ghostSlotY, ghostSlotX + ghostSlotSize, ghostSlotY + 1, 0xFF080808);
        guiGraphics.fill(ghostSlotX, ghostSlotY, ghostSlotX + 1, ghostSlotY + ghostSlotSize, 0xFF080808);
        guiGraphics.fill(ghostSlotX, ghostSlotY + ghostSlotSize - 1, ghostSlotX + ghostSlotSize, ghostSlotY + ghostSlotSize, 0xFF3A3A3A);
        guiGraphics.fill(ghostSlotX + ghostSlotSize - 1, ghostSlotY, ghostSlotX + ghostSlotSize, ghostSlotY + ghostSlotSize, 0xFF3A3A3A);
        
        if (isMouseOver && selectedValue == null) {
            guiGraphics.fill(ghostSlotX + 1, ghostSlotY + 1, ghostSlotX + ghostSlotSize - 1, ghostSlotY + ghostSlotSize - 1, 0x30AACCFF);
        }
        
        if (selectedValue != null) {
            renderSelectedContent(guiGraphics);
        }
    }
    
    private void renderSelectedContent(GuiGraphics guiGraphics) {
        if (selectedValue instanceof net.minecraft.world.item.ItemStack stack) {
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(stack, ghostSlotX + 1, ghostSlotY + 1);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, ghostSlotX + 1, ghostSlotY + 1);
            RenderSystem.disableDepthTest();
        } else if (selectedValue instanceof FluidStack fluidStack) {
            int fluidColor = getFluidColor(fluidStack);
            guiGraphics.fill(ghostSlotX + 1, ghostSlotY + 1, ghostSlotX + ghostSlotSize - 1, ghostSlotY + ghostSlotSize - 1, fluidColor);
            String name = fluidStack.getDisplayName().getString();
            if (name.length() > 6) {
                name = name.substring(0, 6);
            }
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, name, ghostSlotX + ghostSlotSize / 2, ghostSlotY + 5, 0xFFFFFF);
        } else if (selectedValue instanceof String gasId) {
            int color = getGasColor(gasId);
            guiGraphics.fill(ghostSlotX + 1, ghostSlotY + 1, ghostSlotX + ghostSlotSize - 1, ghostSlotY + ghostSlotSize - 1, color);
            String name = gasId.contains(":") ? gasId.substring(gasId.lastIndexOf(":") + 1) : gasId;
            if (name.length() > 6) {
                name = name.substring(0, 6);
            }
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, name, ghostSlotX + ghostSlotSize / 2, ghostSlotY + 5, 0xFFFFFF);
        }
    }
    
    private int getFluidColor(FluidStack fluidStack) {
        try {
            net.minecraft.world.level.material.Fluid fluid = fluidStack.getFluid();
            net.minecraft.resources.ResourceLocation fluidId = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fluid);
            if (fluidId != null) {
                String idStr = fluidId.toString();
                if (idStr.contains("water")) return 0xFF3F76E4;
                if (idStr.contains("lava")) return 0xFFE05000;
                if (idStr.contains("oil")) return 0xFF333333;
                if (idStr.contains("fuel")) return 0xFFCC6600;
                if (idStr.contains("acid")) return 0xFF00AA00;
                if (idStr.contains("uranium")) return 0xFF00FF00;
            }
        } catch (Exception e) {
        }
        return 0xFF5555FF;
    }
    
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (selectedValue instanceof net.minecraft.world.item.ItemStack stack) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
        } else {
            String name = getSelectedName();
            guiGraphics.renderTooltip(Minecraft.getInstance().font, Component.literal(name), mouseX, mouseY);
        }
    }
    
    private String getSelectedName() {
        if (selectedValue instanceof net.minecraft.world.item.ItemStack stack) {
            return stack.getHoverName().getString();
        } else if (selectedValue instanceof FluidStack fluidStack) {
            return fluidStack.getDisplayName().getString() + " (" + fluidStack.getAmount() + "mB)";
        } else if (selectedValue instanceof String gasId) {
            return gasId;
        }
        return "未知";
    }
    
    private int getGasColor(String gasId) {
        if (gasId.contains("hydrogen")) return 0xFFAAAAAA;
        if (gasId.contains("oxygen")) return 0xFF87CEEB;
        if (gasId.contains("water") || gasId.contains("steam")) return 0xFF3F76E4;
        if (gasId.contains("uranium")) return 0xFF00FF00;
        if (gasId.contains("plutonium")) return 0xFF800080;
        if (gasId.contains("sulfur")) return 0xFFFFCC00;
        if (gasId.contains("chlorine")) return 0xFF00FF88;
        if (gasId.contains("slurry")) return 0xFF8B4513;
        if (gasId.contains("pigment")) return 0xFFFF69B4;
        if (gasId.contains("infuse")) return 0xFFDAA520;
        if (gasId.contains("carbon")) return 0xFF333333;
        if (gasId.contains("ethene") || gasId.contains("ethylene")) return 0xFFE0E0E0;
        if (gasId.contains("methane")) return 0xFF666666;
        if (gasId.contains("nitrogen")) return 0xFFE0FFFF;
        if (gasId.contains("deuterium")) return 0xFFADD8E6;
        if (gasId.contains("tritium")) return 0xFFD8BFD8;
        if (gasId.contains("fusion")) return 0xFFDC143C;
        if (gasId.contains("heavy")) return 0xFF4169E1;
        return 0xFF888888;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && mouseX >= ghostSlotX && mouseX < ghostSlotX + ghostSlotSize &&
            mouseY >= ghostSlotY && mouseY < ghostSlotY + ghostSlotSize) {
            selectedValue = null;
            updateButtonStates();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void confirmSelection() {
        if (selectedValue != null) {
            SlotSelectionScreen.SlotSelectionResult.SelectionType type = null;
            int amount = 1;
            
            if (selectedValue instanceof net.minecraft.world.item.ItemStack stack) {
                type = SlotSelectionScreen.SlotSelectionResult.SelectionType.ITEM_SELECTED;
                amount = stack.getCount();
            } else if (selectedValue instanceof FluidStack fluidStack) {
                type = SlotSelectionScreen.SlotSelectionResult.SelectionType.FLUID_SELECTED;
                amount = fluidStack.getAmount();
                if (amount <= 0) amount = 1;
            } else if (selectedValue instanceof String gasId) {
                String expectedType = getExpectedChemicalType();
                if (expectedType != null && gasOnly && !isPureGasId(gasId)) {
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c错误: 此槽位只接受" + expectedType + "类型"),
                        false
                    );
                    return;
                }
                type = SlotSelectionScreen.SlotSelectionResult.SelectionType.GAS_SELECTED;
                amount = 1;
            }
            
            if (type != null) {
                SlotSelectionScreen.SlotSelectionResult result = new SlotSelectionScreen.SlotSelectionResult(type, selectedValue, amount);
                callback.accept(result);
            }
        }
        Minecraft.getInstance().setScreen(parent);
    }
    
    private boolean isPureGasId(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) return false;
        String lowerId = chemicalId.toLowerCase();
        return !lowerId.contains("slurry") && 
               !lowerId.contains("pigment") && 
               !lowerId.contains("infuse");
    }
    
    @Override
    public IJEIGhostTarget.IGhostIngredientConsumer getGhostHandler() {
        return new IJEIGhostTarget.IGhostIngredientConsumer() {
            @Override
            public boolean supportsIngredient(Object ingredient) {
                if (slotType == SlotSelectionScreen.SlotType.ITEM) {
                    return ingredient instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty();
                } else if (slotType == SlotSelectionScreen.SlotType.FLUID) {
                    boolean isFluidStack = ingredient instanceof FluidStack stack && !stack.isEmpty();
                    if (!isFluidStack && ingredient != null) {
                        String className = ingredient.getClass().getName();
                        if (className.contains("FluidStack") || className.contains("Fluid")) {
                            return true;
                        }
                    }
                    return isFluidStack;
                } else if (slotType == SlotSelectionScreen.SlotType.GAS) {
                    return isMekanismChemicalStack(ingredient, "GasStack");
                } else if (slotType == SlotSelectionScreen.SlotType.SLURRY) {
                    return isMekanismChemicalStack(ingredient, "SlurryStack");
                } else if (slotType == SlotSelectionScreen.SlotType.PIGMENT) {
                    return isMekanismChemicalStack(ingredient, "PigmentStack");
                } else if (slotType == SlotSelectionScreen.SlotType.INFUSE_TYPE) {
                    return isMekanismChemicalStack(ingredient, "InfusionStack");
                }
                return false;
            }
            
            @Override
            public void accept(Object ingredient) {
                if (ingredient instanceof net.minecraft.world.item.ItemStack stack) {
                    selectedValue = stack.copy();
                } else if (ingredient instanceof FluidStack fluidStack) {
                    FluidStack copy = fluidStack.copy();
                    if (copy.getAmount() <= 0) {
                        copy.setAmount(1000);
                    }
                    selectedValue = copy;
                } else if (isMekanismChemicalStack(ingredient, null)) {
                    String chemicalId = extractChemicalId(ingredient);
                    String expectedType = getExpectedChemicalType();
                    if (expectedType != null && !isCorrectChemicalType(ingredient, expectedType)) {
                        net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c错误: 此槽位只接受" + expectedType + "类型"),
                            false
                        );
                        return;
                    }
                    selectedValue = chemicalId;
                } else if (slotType == SlotSelectionScreen.SlotType.FLUID && ingredient != null) {
                    selectedValue = extractFluidFromUnknown(ingredient);
                }
                
                updateButtonStates();
                playClickSound();
            }
        };
    }
    
    private FluidStack extractFluidFromUnknown(Object unknownFluid) {
        try {
            Class<?> clazz = unknownFluid.getClass();
            java.lang.reflect.Method getFluidMethod = clazz.getMethod("getFluid");
            Object fluid = getFluidMethod.invoke(unknownFluid);
            if (fluid instanceof net.minecraft.world.level.material.Fluid mcFluid) {
                java.lang.reflect.Method getAmountMethod = clazz.getMethod("getAmount");
                long amount = ((Number) getAmountMethod.invoke(unknownFluid)).longValue();
                if (amount <= 0) amount = 1000;
                return new FluidStack(mcFluid, (int) amount);
            }
        } catch (Exception e) {
            try {
                java.lang.reflect.Field fluidField = unknownFluid.getClass().getDeclaredField("fluid");
                fluidField.setAccessible(true);
                Object fluid = fluidField.get(unknownFluid);
                java.lang.reflect.Field amountField = unknownFluid.getClass().getDeclaredField("amount");
                amountField.setAccessible(true);
                long amount = ((Number) amountField.get(unknownFluid)).longValue();
                if (amount <= 0) amount = 1000;
                if (fluid instanceof net.minecraft.world.level.material.Fluid mcFluid) {
                    return new FluidStack(mcFluid, (int) amount);
                }
            } catch (Exception e2) {
            }
        }
        return null;
    }
    
    private boolean isMekanismChemicalStack(Object ingredient, String expectedType) {
        if (ingredient == null) return false;
        String className = ingredient.getClass().getName();
        
        if (expectedType != null) {
            return className.contains(expectedType);
        }
        
        return className.contains("GasStack") || 
               className.contains("SlurryStack") || 
               className.contains("PigmentStack") || 
               className.contains("InfusionStack");
    }
    
    private String getExpectedChemicalType() {
        if (slotType == SlotSelectionScreen.SlotType.GAS) return "气体(Gas)";
        if (slotType == SlotSelectionScreen.SlotType.SLURRY) return "浆液(Slurry)";
        if (slotType == SlotSelectionScreen.SlotType.PIGMENT) return "颜料(Pigment)";
        if (slotType == SlotSelectionScreen.SlotType.INFUSE_TYPE) return "灌注类型(InfuseType)";
        return null;
    }
    
    private boolean isCorrectChemicalType(Object ingredient, String expectedType) {
        if (ingredient == null) return false;
        String className = ingredient.getClass().getName();
        
        if (slotType == SlotSelectionScreen.SlotType.GAS) {
            return className.contains("GasStack");
        } else if (slotType == SlotSelectionScreen.SlotType.SLURRY) {
            return className.contains("SlurryStack");
        } else if (slotType == SlotSelectionScreen.SlotType.PIGMENT) {
            return className.contains("PigmentStack");
        } else if (slotType == SlotSelectionScreen.SlotType.INFUSE_TYPE) {
            return className.contains("InfusionStack");
        }
        return false;
    }
    
    private String extractChemicalId(Object chemicalStack) {
        try {
            java.lang.reflect.Method getTypeMethod = chemicalStack.getClass().getMethod("getType");
            Object chemical = getTypeMethod.invoke(chemicalStack);
            java.lang.reflect.Method getRegistryNameMethod = chemical.getClass().getMethod("getRegistryName");
            Object registryName = getRegistryNameMethod.invoke(chemical);
            if (registryName instanceof net.minecraft.resources.ResourceLocation rl) {
                return rl.toString();
            }
        } catch (Exception e) {
        }
        return "unknown:chemical";
    }
    
    @Override
    public int borderSize() {
        return 1;
    }
    
    private void playClickSound() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
        }
    }
    
    @Override
    public void removed() {
        if (!switchingToJEI) {
            super.removed();
        }
    }
    
    public void setSwitchingToJEI(boolean value) {
        this.switchingToJEI = value;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return false;
    }
}