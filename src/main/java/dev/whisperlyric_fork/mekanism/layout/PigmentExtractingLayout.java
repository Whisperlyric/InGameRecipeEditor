package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PigmentExtractingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(
            baseX, baseY,
            "item_input",
            0, "", true
        ));
        
        return components;
    }
    
    public int getOutputSlotIndex() {
        return 1;
    }
    
    public String getOutputType() {
        return "pigment";
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
        return "Pigment Extracting (颜料提取器)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
