package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import com.wzz.registerhelper.gui.recipe.component.EnergySlotComponent;
import dev.whisperlyric_fork.gui.NumberAdjustmentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;

public class EnergySlotRenderer implements ComponentRenderer {
    private final EnergySlotComponent component;
    private boolean active = true;
    
    private static final ResourceLocation POWER_SLOT = new ResourceLocation("registerhelper", "textures/gui/slot/power.png");
    private static final ResourceLocation OVERLAY_POWER = new ResourceLocation("registerhelper", "textures/gui/slot/overlay_power.png");
    
    public EnergySlotRenderer(EnergySlotComponent component) {
        this.component = component;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!active) return;
        
        int x = component.getX();
        int y = component.getY();
        
        boolean isMouseOver = mouseX >= x && mouseX < x + 18 &&
                             mouseY >= y && mouseY < y + 18;
        
        RenderSystem.setShaderTexture(0, POWER_SLOT);
        guiGraphics.blit(POWER_SLOT, x, y, 0, 0, 18, 18, 18, 18);
        
        double energyRatio = component.getEnergyRatio();
        if (energyRatio > 0) {
            int barHeight = (int) (energyRatio * 16);
            
            RenderSystem.setShaderTexture(0, OVERLAY_POWER);
            guiGraphics.blit(OVERLAY_POWER, x + 1, y + 17 - barHeight, 0, 16 - barHeight, 16, barHeight, 16, 16);
        }
        
        if (isMouseOver) {
            String tooltip = String.format("%,d FE (output: %,d)", component.getEnergyInFE(), component.getEnergy());
            guiGraphics.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active) return false;
        
        Rectangle bounds = getBounds();
        if (bounds.contains((int)mouseX, (int)mouseY)) {
            if (button == 0) {
                openAmountAdjustment();
                return true;
            }
        }
        return false;
    }
    
    private void openAmountAdjustment() {
        Screen currentScreen = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new NumberAdjustmentScreen(
            currentScreen,
            0L,
            component.getMaxEnergy(),
            component.getEnergy(),
            energy -> {
                component.setEnergy(energy);
            },
            true
        ));
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
    
    @Override
    public Rectangle getBounds() {
        return component.getBounds();
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
