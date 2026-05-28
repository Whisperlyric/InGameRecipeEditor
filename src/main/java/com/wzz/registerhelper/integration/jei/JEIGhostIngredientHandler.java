package com.wzz.registerhelper.integration.jei;

import dev.whisperlyric_fork.gui.JEISelectionScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JEIGhostIngredientHandler<GUI extends Screen> implements IGhostIngredientHandler<GUI> {

    @Override
    public <INGREDIENT> List<Target<INGREDIENT>> getTargetsTyped(GUI gui, ITypedIngredient<INGREDIENT> ingredient, boolean doStart) {
        List<TargetInfo<INGREDIENT>> ghostTargets = new ArrayList<>();
        
        com.wzz.registerhelper.util.ModLogger.getLogger().info("JEI getTargetsTyped called: guiClass={}, ingredientClass={}, ingredient={}",
            gui.getClass().getName(),
            ingredient.getIngredient() != null ? ingredient.getIngredient().getClass().getName() : "null",
            ingredient.getIngredient());
        
        if (gui instanceof JEISelectionScreen screen) {
            com.wzz.registerhelper.util.ModLogger.getLogger().info("GUI is JEISelectionScreen, slotType={}", screen.getSlotType());
            IJEIGhostTarget.IGhostIngredientConsumer ghostHandler = screen.getGhostHandler();
            com.wzz.registerhelper.util.ModLogger.getLogger().info("ghostHandler obtained: {}", ghostHandler != null ? "yes" : "null");
            if (ghostHandler != null) {
                boolean supports = ghostHandler.supportsIngredient(ingredient.getIngredient());
                com.wzz.registerhelper.util.ModLogger.getLogger().info("supportsIngredient result: {}", supports);
                if (supports) {
                    ghostTargets.add(new TargetInfo<>(screen, ghostHandler, 
                        screen.getGhostSlotX(), 
                        screen.getGhostSlotY(), 
                        screen.getGhostSlotSize(), 
                        screen.getGhostSlotSize()));
                    com.wzz.registerhelper.util.ModLogger.getLogger().info("Added ghost target at x={}, y={}, size={}",
                        screen.getGhostSlotX(), screen.getGhostSlotY(), screen.getGhostSlotSize());
                }
            }
        }
        
        ghostTargets.addAll(getTargets(gui.children(), ingredient));
        
        com.wzz.registerhelper.util.ModLogger.getLogger().info("Total ghostTargets count: {}", ghostTargets.size());
        
        if (ghostTargets.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Target<INGREDIENT>> targets = new ArrayList<>();
        for (TargetInfo<INGREDIENT> ghostTarget : ghostTargets) {
            targets.addAll(ghostTarget.convertToTargets());
        }
        return targets;
    }

    private <INGREDIENT> List<TargetInfo<INGREDIENT>> getTargets(List<? extends GuiEventListener> children, ITypedIngredient<INGREDIENT> ingredient) {
        List<TargetInfo<INGREDIENT>> ghostTargets = new ArrayList<>();
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget) {
                if (widget.visible) {
                    if (widget instanceof IJEIGhostTarget ghostTarget) {
                        IJEIGhostTarget.IGhostIngredientConsumer ghostHandler = ghostTarget.getGhostHandler();
                        if (ghostHandler != null && ghostHandler.supportsIngredient(ingredient.getIngredient())) {
                            ghostTargets.add(new TargetInfo<>(ghostTarget, ghostHandler, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
                        }
                    }
                }
            }
        }
        return ghostTargets;
    }

    @Override
    public void onComplete() {
    }

    private static class TargetInfo<INGREDIENT> {
        
        private final IJEIGhostTarget.IGhostIngredientConsumer ghostHandler;
        private final int x, y, width, height;

        public TargetInfo(IJEIGhostTarget ghostTarget, IJEIGhostTarget.IGhostIngredientConsumer ghostHandler, int x, int y, int width, int height) {
            this.ghostHandler = ghostHandler;
            int borderSize = ghostTarget.borderSize();
            this.x = x + borderSize;
            this.y = y + borderSize;
            this.width = width - 2 * borderSize;
            this.height = height - 2 * borderSize;
        }

        public List<Target<INGREDIENT>> convertToTargets() {
            List<Rect2i> visibleAreas = new ArrayList<>();
            visibleAreas.add(new Rect2i(x, y, width, height));
            
            return visibleAreas.stream().map(visibleArea -> new Target<INGREDIENT>() {
                @Override
                public Rect2i getArea() {
                    return visibleArea;
                }

                @Override
                public void accept(INGREDIENT ingredient) {
                    com.wzz.registerhelper.util.ModLogger.getLogger().info("Target.accept called: ingredientClass={}, ingredient={}",
                        ingredient != null ? ingredient.getClass().getName() : "null",
                        ingredient);
                    com.wzz.registerhelper.util.ModLogger.getLogger().info("Calling ghostHandler.accept...");
                    ghostHandler.accept(ingredient);
                    com.wzz.registerhelper.util.ModLogger.getLogger().info("ghostHandler.accept completed");
                }
            }).collect(Collectors.toList());
        }
    }
}