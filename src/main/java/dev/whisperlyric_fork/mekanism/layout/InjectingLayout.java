package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.component.GasSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class InjectingLayout implements RecipeLayout {
    
    private static final int SPACING = 28;
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(
            baseX, baseY,
            "item_input",
            0, "", true
        ));
        
        components.add(new GasSlotComponent(
            baseX + SPACING, baseY,
            "gas_input",
            1,
            "",
            0,
            true
        ));
        
        return components;
    }
    
    public int getOutputSlotIndex() {
        return 2;
    }
    
    public String getOutputType() {
        return "item";
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
        return "Chemical Injection Chamber (化学压射室)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
