package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SawingLayout implements RecipeLayout {
    
    private static final int SPACING = 28;
    
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
    
    @Override
    public List<RecipeComponent> generateOutputComponents(int outputX, int outputY) {
        List<RecipeComponent> outputs = new ArrayList<>();
        
        outputs.add(new SlotComponent(
            outputX, outputY,
            "main_output",
            1,
            "主输出", true
        ));
        
        outputs.add(new SlotComponent(
            outputX + SPACING, outputY,
            "secondary_output",
            2,
            "副输出", true
        ));
        
        return outputs;
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
        return "Precision Sawmill (精密锯木机)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
