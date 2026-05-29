package dev.whisperlyric_fork.mekanism.layout;

import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChemicalCrystallizerLayout implements RecipeLayout {
    public enum InputType {
        GAS("气体", ChemicalSlotComponent.ChemicalType.GAS),
        INFUSE_TYPE("灌注类型", ChemicalSlotComponent.ChemicalType.INFUSE_TYPE),
        PIGMENT("颜料", ChemicalSlotComponent.ChemicalType.PIGMENT),
        SLURRY("浆液", ChemicalSlotComponent.ChemicalType.SLURRY);
        
        private final String displayName;
        private final ChemicalSlotComponent.ChemicalType chemicalType;
        
        InputType(String displayName, ChemicalSlotComponent.ChemicalType chemicalType) {
            this.displayName = displayName;
            this.chemicalType = chemicalType;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public ChemicalSlotComponent.ChemicalType getChemicalType() {
            return chemicalType;
        }
    }
    
    private InputType currentInputType = InputType.GAS;
    
    public void setInputType(InputType inputType) {
        this.currentInputType = inputType;
    }
    
    public InputType getInputType() {
        return currentInputType;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new ChemicalSlotComponent(
            baseX, baseY,
            "chemical_input",
            0,
            currentInputType.getChemicalType(),
            "",
            0,
            10000
        ));
        
        return components;
    }
    
    public int getOutputSlotIndex() {
        return 1;
    }
    
    public String getOutputType() {
        return "item";
    }
    
    public int getOutputYOffset() {
        return 0;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 40, 60);
    }
    
    @Override
    public String getLayoutName() {
        return "Chemical Crystallizer (化学结晶器)" + currentInputType.getDisplayName();
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
}
