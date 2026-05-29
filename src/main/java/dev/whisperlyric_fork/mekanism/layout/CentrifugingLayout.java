package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.GasSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CentrifugingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new GasSlotComponent(
            baseX, baseY,
            "gas_input",
            0,
            "",
            0,
            true
        ));
        
        return components;
    }
    
    public int getOutputSlotIndex() {
        return 1;
    }
    
    public String getOutputType() {
        return "gas";
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
        return "Isotopic Centrifuge (同位素离心机)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
