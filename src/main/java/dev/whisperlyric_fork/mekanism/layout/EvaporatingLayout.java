package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class EvaporatingLayout implements RecipeLayout {
    
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
        
        outputs.add(new FluidSlotComponent(
            outputX, outputY,
            "fluid_output",
            1,
            "",
            0,
            10000
        ));
        
        return outputs;
    }
    
    public String getOutputType() {
        return "fluid";
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
        return "Evaporation Plant (热力蒸馏塔)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
