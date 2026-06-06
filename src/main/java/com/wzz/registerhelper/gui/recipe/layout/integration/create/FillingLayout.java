package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FillingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();

        components.add(new SlotComponent(
            baseX + 72, baseY + 126,
            "filling",
            0
        ));
        components.add(new FluidSlotComponent(
            baseX + 50, baseY + 70,
            "fluid_input",
            1,
            "",
            0,
            1000
        ));
        
        return components;
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
        return "Filling (填充)";
    }
}