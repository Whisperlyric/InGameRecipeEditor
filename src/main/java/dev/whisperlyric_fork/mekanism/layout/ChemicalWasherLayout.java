package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChemicalWasherLayout implements RecipeLayout {
    
    private static final int SPACING = 28;
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new FluidSlotComponent(
            baseX, baseY,
            "fluid_input",
            0,
            "",
            0,
            10000
        ));
        
        components.add(new ChemicalSlotComponent(
            baseX + SPACING, baseY,
            "slurry_input",
            1,
            ChemicalSlotComponent.ChemicalType.SLURRY,
            "",
            0,
            10000
        ));
        
        return components;
    }
    
    public int getOutputSlotIndex() {
        return 2;
    }
    
    public String getOutputType() {
        return "slurry";
    }
    
    public int getOutputYOffset() {
        return 20;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 40, 80);
    }
    
    @Override
    public String getLayoutName() {
        return "Chemical Washer (化学清洗机)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
