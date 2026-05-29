package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MetallurgicInfusingLayout implements RecipeLayout {
    
    private static final int SPACING = 28;
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(
            baseX, baseY,
            "item_input",
            0, "", true
        ));
        
        components.add(new ChemicalSlotComponent(
            baseX + SPACING, baseY,
            "infuse_input",
            1,
            ChemicalSlotComponent.ChemicalType.INFUSE_TYPE,
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
        return "Metallurgic Infuser (冶金灌注机)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
