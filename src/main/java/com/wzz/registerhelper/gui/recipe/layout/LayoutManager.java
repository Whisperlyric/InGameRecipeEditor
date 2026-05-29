package com.wzz.registerhelper.gui.recipe.layout;

import com.wzz.registerhelper.gui.recipe.layout.integration.astralrail_cube.PathAscension;
import com.wzz.registerhelper.gui.recipe.layout.integration.astralrail_cube.PathTransmuter;
import com.wzz.registerhelper.gui.recipe.layout.integration.botania.*;
import com.wzz.registerhelper.gui.recipe.layout.integration.builtin.*;
import com.wzz.registerhelper.gui.recipe.layout.integration.create.CompactingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.create.EmptyingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.create.FillingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.create.PressingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.farmersdelight.CookingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.farmersdelight.CuttingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.immersive_engineering.ArcFurnaceLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture.InfusionLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture.ReprocessorLayout;
import dev.whisperlyric_fork.mekanism.layout.ChemicalCrystallizerLayout;
import dev.whisperlyric_fork.mekanism.layout.RotaryCondensentratorLayout;

import java.util.*;

public class LayoutManager {
    private static final Map<String, RecipeLayout> layouts = new HashMap<>();
    
    static {
        registerLayout("rectangular_3x3", new RectangularLayout(3, 3));
        registerLayout("rectangular_9x9", new RectangularLayout(9, 9));
        registerLayout("rectangular_4x1", new RectangularLayout(4, 1));
        registerLayout("rectangular_1x1", new RectangularLayout(1, 1));
        registerLayout("rectangular_2x2", new RectangularLayout(2, 2));
        registerLayout("rectangular_5x5", new RectangularLayout(5, 5));
        registerLayout("rectangular_11x11", new RectangularLayout(11, 11));
        registerLayout("rectangular_7x7", new RectangularLayout(7, 7));

        registerLayout("minecraft_brewing", new MinecraftBrewingLayout());
        registerLayout("stonecutting", new StonecuttingLayout());
        registerLayout("smithing", new SmithingLayout());
        registerLayout("anvil", new AnvilLayout());

        registerLayout("runic_altar", new RunicAltarLayout());
        registerLayout("arc_furnace", new ArcFurnaceLayout());
        registerLayout("infusion", new InfusionLayout());
        registerLayout("reprocessor", new ReprocessorLayout());
        registerLayout("cutting", new CuttingLayout());
        registerLayout("cooking", new CookingLayout());
        registerLayout("emptying", new EmptyingLayout());
        registerLayout("create_cutting", new com.wzz.registerhelper.gui.recipe.layout.integration.create.CuttingLayout());
        registerLayout("compacting", new CompactingLayout());
        registerLayout("filling", new FillingLayout());
        registerLayout("pressing", new PressingLayout());
        registerLayout("path_ascension", new PathAscension());
        registerLayout("path_transmuter", new PathTransmuter());
    }
    
    public static void registerLayout(String id, RecipeLayout layout) {
        layouts.put(id, layout);
    }

    public static RecipeLayout getLayout(String id) {
        return layouts.get(id);
    }
    
    public static Set<String> getAllLayoutIds() {
        return layouts.keySet();
    }
    
    public static List<RecipeLayout> getAllLayouts() {
        return new ArrayList<>(layouts.values());
    }
    
    public static void setRotaryCondensentratorMode(String mode) {
        RecipeLayout layout = layouts.get("mekanism_rotary_condensentrator");
        if (layout instanceof RotaryCondensentratorLayout rotaryLayout) {
            RotaryCondensentratorLayout.Mode layoutMode = switch (mode) {
                case "reversible" -> RotaryCondensentratorLayout.Mode.REVERSIBLE;
                case "decondensation" -> RotaryCondensentratorLayout.Mode.DECONDENSATION;
                case "condensation" -> RotaryCondensentratorLayout.Mode.CONDENSATION;
                default -> RotaryCondensentratorLayout.Mode.REVERSIBLE;
            };
            rotaryLayout.setMode(layoutMode);
        }
    }
    
    public static void setChemicalCrystallizerInputType(String inputType) {
        RecipeLayout layout = layouts.get("mekanism_chemical_crystallizer");
        if (layout instanceof ChemicalCrystallizerLayout crystallizerLayout) {
            ChemicalCrystallizerLayout.InputType type = switch (inputType.toLowerCase()) {
                case "gas" -> ChemicalCrystallizerLayout.InputType.GAS;
                case "infuse_type", "infuse" -> ChemicalCrystallizerLayout.InputType.INFUSE_TYPE;
                case "pigment" -> ChemicalCrystallizerLayout.InputType.PIGMENT;
                case "slurry" -> ChemicalCrystallizerLayout.InputType.SLURRY;
                default -> ChemicalCrystallizerLayout.InputType.GAS;
            };
            crystallizerLayout.setInputType(type);
        }
    }
}