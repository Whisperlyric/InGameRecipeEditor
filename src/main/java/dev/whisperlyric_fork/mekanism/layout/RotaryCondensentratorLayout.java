package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.GasSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RotaryCondensentratorLayout implements RecipeLayout {
    public enum Mode {
        REVERSIBLE("可逆模式"),
        DECONDENSATION("液体蒸发"),
        CONDENSATION("气体冷凝");
        
        private final String displayName;
        
        Mode(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private Mode currentMode = Mode.REVERSIBLE;
    private static final int SPACING = 28;
    
    public void setMode(Mode mode) {
        this.currentMode = mode;
    }
    
    public Mode getMode() {
        return currentMode;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        switch (currentMode) {
            case REVERSIBLE -> {
                components.add(new FluidSlotComponent(
                    baseX, baseY,
                    "fluid_input",
                    0,
                    "",
                    0,
                    10000
                ));
            }
            
            case DECONDENSATION -> {
                components.add(new FluidSlotComponent(
                    baseX, baseY,
                    "fluid_input",
                    0,
                    "",
                    0,
                    10000
                ));
            }
            
            case CONDENSATION -> {
                components.add(new GasSlotComponent(
                    baseX, baseY,
                    "gas_input",
                    0,
                    "",
                    0,
                    true
                ));
            }
        }
        
        return components;
    }
    
    public int getOutputSlotIndex() {
        return switch (currentMode) {
            case REVERSIBLE, DECONDENSATION, CONDENSATION -> 1;
        };
    }
    
    public String getOutputType() {
        return switch (currentMode) {
            case REVERSIBLE, DECONDENSATION -> "gas";
            case CONDENSATION -> "fluid";
        };
    }
    
    public int getOutputYOffset() {
        return 20;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 40, 60);
    }
    
    @Override
    public String getLayoutName() {
        return "Rotary Condensentrator (回旋式气液转换器) - " + currentMode.getDisplayName();
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
