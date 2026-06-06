package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MixingLayout implements RecipeLayout {
    private static final int SLOT_SIZE = 18;
    private static final int SPACING = 20;
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        int offsetY = baseY + 18;
        
        int inputStartX = baseX;
        int inputStartY = offsetY;
        
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int index = y * 3 + x;
                components.add(new SlotComponent(
                    inputStartX + x * SPACING,
                    inputStartY + y * SPACING,
                    "item_input_" + index,
                    index,
                    "", true
                ));
            }
        }
        
        int fluidInputX = baseX + 3 * SPACING + 4;
        int fluidInputY = offsetY + SPACING - 10;
        
        components.add(new FluidSlotComponent(
            fluidInputX, fluidInputY,
            "fluid_input_1",
            9,
            "",
            0,
            1000
        ));
        
        components.add(new FluidSlotComponent(
            fluidInputX + 20, fluidInputY,
            "fluid_input_2",
            10,
            "",
            0,
            1000
        ));
        
        return components;
    }
    
    @Override
    public List<RecipeComponent> generateOutputComponents(int outputX, int outputY) {
        List<RecipeComponent> outputs = new ArrayList<>();
        
        int offsetY = outputY + 18;
        
        int outputStartX = outputX;
        int outputStartY = offsetY;
        
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int index = y * 3 + x;
                outputs.add(new SlotComponent(
                    outputStartX + x * SPACING,
                    outputStartY + y * SPACING,
                    "item_output_" + index,
                    11 + index,
                    "", true
                ));
            }
        }
        
        int fluidOutputX = outputStartX;
        int fluidOutputY = offsetY + 3 * SPACING + 4;
        
        outputs.add(new FluidSlotComponent(
            fluidOutputX, fluidOutputY,
            "fluid_output_1",
            20,
            "",
            0,
            1000
        ));
        
        outputs.add(new FluidSlotComponent(
            fluidOutputX + 20, fluidOutputY,
            "fluid_output_2",
            21,
            "",
            0,
            1000
        ));
        
        return outputs;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 350, 180);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Mixing (混合搅拌)";
    }
    
    @Override
    public String getOutputType() {
        return "mixed";
    }
}