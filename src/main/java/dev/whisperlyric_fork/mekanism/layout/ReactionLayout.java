package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.component.GasSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ReactionLayout implements RecipeLayout {
    
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
        
        components.add(new FluidSlotComponent(
            baseX + SPACING * 2, baseY,
            "fluid_input",
            2,
            "",
            0,
            10000
        ));
        
        return components;
    }
    
    @Override
    public List<RecipeComponent> generateOutputComponents(int outputX, int outputY) {
        List<RecipeComponent> outputs = new ArrayList<>();
        
        outputs.add(new SlotComponent(
            outputX, outputY,
            "item_output",
            3, "", true
        ));
        
        outputs.add(new GasSlotComponent(
            outputX + SPACING, outputY,
            "gas_output",
            4,
            "",
            0,
            true
        ));
        
        return outputs;
    }
    
    public String getOutputType() {
        return "mixed";
    }
    
    public int getOutputYOffset() {
        return 20;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 40, 100);
    }
    
    @Override
    public String getLayoutName() {
        return "Reaction Chamber (加压反应室)";
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
