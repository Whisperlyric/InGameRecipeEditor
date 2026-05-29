package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.GasSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ElectrolyticSeparatorLayout implements RecipeLayout {
    
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
        
        return components;
    }
    
    @Override
    public List<RecipeComponent> generateOutputComponents(int outputX, int outputY) {
        List<RecipeComponent> outputs = new ArrayList<>();
        
        outputs.add(new GasSlotComponent(
            outputX, outputY,
            "left_gas_output",
            1,
            "",
            0,
            true
        ));
        
        outputs.add(new GasSlotComponent(
            outputX + SPACING, outputY,
            "right_gas_output",
            2,
            "",
            0,
            true
        ));
        
        return outputs;
    }
    
    public String getOutputType() {
        return "gas";
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
        return "Electrolytic Separator (电解分离器)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
