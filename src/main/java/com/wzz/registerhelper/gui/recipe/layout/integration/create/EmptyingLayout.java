package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class EmptyingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        components.add(new SlotComponent(
            baseX + 66, baseY + 66,
            "input_item",
            0
        ));
        return components;
    }
    
    @Override
    public List<RecipeComponent> generateOutputComponents(int outputX, int outputY) {
        List<RecipeComponent> outputs = new ArrayList<>();
        
        outputs.add(new SlotComponent(
            outputX, outputY + 18,
            "output_item",
            0
        ));
        
        outputs.add(new FluidSlotComponent(
            outputX + 26, outputY + 18,
            "fluid_output",
            1,
            "",
            0,
            1500
        ));
        
        return outputs;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 200, 200);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Emptying (倒空)";
    }
}