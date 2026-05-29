package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PigmentMixingLayout implements RecipeLayout {
    
    private static final int SPACING = 28;
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new ChemicalSlotComponent(
            baseX, baseY,
            "left_pigment_input",
            0,
            ChemicalSlotComponent.ChemicalType.PIGMENT,
            "",
            0,
            10000
        ));
        
        components.add(new ChemicalSlotComponent(
            baseX + SPACING, baseY,
            "right_pigment_input",
            1,
            ChemicalSlotComponent.ChemicalType.PIGMENT,
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
        return "pigment";
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
        return "Pigment Mixing (颜料混合器)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
