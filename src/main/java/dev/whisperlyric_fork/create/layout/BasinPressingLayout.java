package dev.whisperlyric_fork.create.layout;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BasinPressingLayout implements RecipeLayout {
    private static final int SLOT_SIZE = 18;
    private static final int SPACING = 20;
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        int inputStartX = baseX;
        int inputStartY = baseY;
        
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int index = y * 3 + x;
                components.add(new SlotComponent(
                    inputStartX + x * SPACING,
                    inputStartY + y * SPACING,
                    "item_input_" + index,
                    index
                ));
            }
        }
        
        int fluidInputX = baseX + 3 * SPACING + 10;
        int fluidInputY = baseY + SPACING;
        
        components.add(new FluidSlotComponent(
            fluidInputX, fluidInputY,
            "fluid_input_1",
            9,
            "minecraft:water",
            0,
            1000
        ));
        
        components.add(new FluidSlotComponent(
            fluidInputX, fluidInputY + SPACING,
            "fluid_input_2",
            10,
            "minecraft:lava",
            0,
            1000
        ));
        
        int outputStartX = baseX + 3 * SPACING + 110;
        int outputStartY = baseY;
        
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int index = y * 3 + x;
                components.add(new SlotComponent(
                    outputStartX + x * SPACING,
                    outputStartY + y * SPACING,
                    "item_output_" + index,
                    11 + index
                ));
            }
        }
        
        int fluidOutputX = outputStartX + 3 * SPACING + 10;
        int fluidOutputY = baseY + SPACING;
        
        components.add(new FluidSlotComponent(
            fluidOutputX, fluidOutputY,
            "fluid_output_1",
            20,
            "minecraft:water",
            0,
            1000
        ));
        
        components.add(new FluidSlotComponent(
            fluidOutputX, fluidOutputY + SPACING,
            "fluid_output_2",
            21,
            "minecraft:lava",
            0,
            1000
        ));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 450, 120);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Basin Pressing (9物品+2流体)";
    }
}
