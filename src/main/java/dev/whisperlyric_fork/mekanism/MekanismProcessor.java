package dev.whisperlyric_fork.mekanism;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import static com.wzz.registerhelper.util.RecipeUtil.*;

public class MekanismProcessor implements ModRecipeProcessor {

    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("mekanism");
    }
    
    private JsonObject wrapIngredient(Object ingredient) {
        JsonObject wrapper = new JsonObject();
        wrapper.add("ingredient", createIngredientJson(ingredient));
        return wrapper;
    }
    
    private int getIntValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
    
    private String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        net.minecraft.resources.ResourceLocation location = 
            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return location != null ? location.toString() : "minecraft:air";
    }
    
    private String getItemId(Item item) {
        net.minecraft.resources.ResourceLocation location = 
            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        return location != null ? location.toString() : "minecraft:air";
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
            "crushing",
            "enriching", 
            "smelting",
            "combining",
            "compressing",
            "purifying",
            "injecting",
            "metallurgic_infusing",
            "sawing",
            "chemical_infusing",
            "crystallizing",
            "dissolution",
            "energy_conversion",
            "rotary_condensentrator",
            "electrolytic_separator",
            "pressurized_reaction_chamber",
            "isotopic_centrifuge",
            "solar_neutron_activator",
            "antiprotonic_nucleosynthesizer",
            "thermal_evaporation",
            "chemical_oxidizer"
        };
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        
        String type = request.recipeType;
        if (!type.contains(":")) {
            type = "mekanism:" + type;
        }
        
        recipe.addProperty("type", type);
        
        switch (type) {
            case "mekanism:crushing" -> createCrushingRecipe(recipe, request);
            case "mekanism:enriching" -> createEnrichingRecipe(recipe, request);
            case "mekanism:smelting" -> createSmeltingRecipe(recipe, request);
            case "mekanism:combining" -> createCombiningRecipe(recipe, request);
            case "mekanism:compressing" -> createCompressingRecipe(recipe, request);
            case "mekanism:purifying" -> createPurifyingRecipe(recipe, request);
            case "mekanism:injecting" -> createInjectingRecipe(recipe, request);
            case "mekanism:metallurgic_infusing" -> createMetallurgicInfusingRecipe(recipe, request);
            case "mekanism:sawing" -> createSawingRecipe(recipe, request);
            case "mekanism:chemical_infusing" -> createChemicalInfusingRecipe(recipe, request);
            case "mekanism:crystallizing" -> createCrystallizingRecipe(recipe, request);
            case "mekanism:dissolution" -> createDissolutionRecipe(recipe, request);
            case "mekanism:energy_conversion" -> createEnergyConversionRecipe(recipe, request);
            case "mekanism:rotary_condensentrator" -> createRotaryCondensentratorRecipe(recipe, request);
            case "mekanism:electrolytic_separator" -> createElectrolyticSeparatorRecipe(recipe, request);
            case "mekanism:pressurized_reaction_chamber" -> createPressurizedReactionChamberRecipe(recipe, request);
            case "mekanism:isotopic_centrifuge" -> createIsotopicCentrifugeRecipe(recipe, request);
            case "mekanism:solar_neutron_activator" -> createSolarNeutronActivatorRecipe(recipe, request);
            case "mekanism:antiprotonic_nucleosynthesizer" -> createAntiprotonicNucleosynthesizerRecipe(recipe, request);
            case "mekanism:thermal_evaporation" -> createThermalEvaporationRecipe(recipe, request);
            case "mekanism:chemical_oxidizer" -> createChemicalOxidizerRecipe(recipe, request);
        }
        
        return recipe;
    }

    private void createCrushingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createEnrichingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createSmeltingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createCombiningRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("mainInput", wrapIngredient(request.ingredients[0]));
        }
        
        if (request.ingredients != null && request.ingredients.length > 1) {
            recipe.add("extraInput", wrapIngredient(request.ingredients[1]));
        } else {
            JsonObject emptyInput = new JsonObject();
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", "minecraft:air");
            emptyInput.add("ingredient", ingredient);
            recipe.add("extraInput", emptyInput);
        }
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("mainOutput", output);
    }

    private void createCompressingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject gasInput = new JsonObject();
        String gasType = (String) request.properties.getOrDefault("gas", "mekanism:hydrogen");
        int gasAmount = getIntValue(request.properties.get("gasAmount"), 100);
        gasInput.addProperty("gas", gasType);
        gasInput.addProperty("amount", gasAmount);
        recipe.add("gasInput", gasInput);
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createPurifyingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject gasInput = new JsonObject();
        String gasType = (String) request.properties.getOrDefault("gas", "mekanism:oxygen");
        int gasAmount = getIntValue(request.properties.get("gasAmount"), 100);
        gasInput.addProperty("gas", gasType);
        gasInput.addProperty("amount", gasAmount);
        recipe.add("gasInput", gasInput);
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createInjectingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject gasInput = new JsonObject();
        String gasType = (String) request.properties.getOrDefault("gas", "mekanism:hydrogen");
        int gasAmount = getIntValue(request.properties.get("gasAmount"), 100);
        gasInput.addProperty("gas", gasType);
        gasInput.addProperty("amount", gasAmount);
        recipe.add("gasInput", gasInput);
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createMetallurgicInfusingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject infusionInput = new JsonObject();
        String infusionType = (String) request.properties.getOrDefault("infusionType", "mekanism:carbon");
        int infusionAmount = getIntValue(request.properties.get("infusionAmount"), 10);
        infusionInput.addProperty("infuse_type", infusionType);
        infusionInput.addProperty("amount", infusionAmount);
        recipe.add("infusionInput", infusionInput);
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createSawingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        
        JsonObject mainOutput = new JsonObject();
        mainOutput.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            mainOutput.addProperty("count", request.resultCount);
        }
        recipe.add("mainOutput", mainOutput);
        
        Object extraOutput = request.properties.get("extraOutput");
        if (extraOutput != null) {
            JsonObject secondaryOutput = new JsonObject();
            if (extraOutput instanceof ItemStack stack) {
                secondaryOutput.addProperty("item", getItemId(stack));
                if (stack.getCount() > 1) {
                    secondaryOutput.addProperty("count", stack.getCount());
                }
            }
            recipe.add("secondaryOutput", secondaryOutput);
            
            double chance = (double) request.properties.getOrDefault("secondaryChance", 1.0);
            recipe.addProperty("secondaryChance", chance);
        }
    }

    private void createChemicalInfusingRecipe(JsonObject recipe, RecipeRequest request) {
        JsonObject leftInput = new JsonObject();
        String leftGas = (String) request.properties.getOrDefault("leftGas", "mekanism:hydrogen");
        int leftAmount = getIntValue(request.properties.get("leftAmount"), 100);
        leftInput.addProperty("gas", leftGas);
        leftInput.addProperty("amount", leftAmount);
        recipe.add("leftInput", leftInput);
        
        JsonObject rightInput = new JsonObject();
        String rightGas = (String) request.properties.getOrDefault("rightGas", "mekanism:oxygen");
        int rightAmount = getIntValue(request.properties.get("rightAmount"), 100);
        rightInput.addProperty("gas", rightGas);
        rightInput.addProperty("amount", rightAmount);
        recipe.add("rightInput", rightInput);
        
        JsonObject output = new JsonObject();
        String outputGas = (String) request.properties.getOrDefault("outputGas", "mekanism:water");
        int outputAmount = getIntValue(request.properties.get("outputAmount"), 100);
        output.addProperty("gas", outputGas);
        output.addProperty("amount", outputAmount);
        recipe.add("output", output);
    }

    private void createCrystallizingRecipe(JsonObject recipe, RecipeRequest request) {
        String chemicalType = (String) request.properties.getOrDefault("chemicalType", "gas");
        recipe.addProperty("chemicalType", chemicalType);
        
        JsonObject input = new JsonObject();
        String inputGas = (String) request.properties.getOrDefault("inputGas", "mekanism:lithium");
        int inputAmount = getIntValue(request.properties.get("inputAmount"), 100);
        input.addProperty(chemicalType, inputGas);
        input.addProperty("amount", inputAmount);
        recipe.add("input", input);
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createDissolutionRecipe(JsonObject recipe, RecipeRequest request) {
        JsonObject gasInput = new JsonObject();
        String inputGas = (String) request.properties.getOrDefault("inputGas", "mekanism:sulfuric_acid");
        int inputAmount = getIntValue(request.properties.get("inputAmount"), 100);
        gasInput.addProperty("gas", inputGas);
        gasInput.addProperty("amount", inputAmount);
        recipe.add("gasInput", gasInput);
        
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", createIngredientJson(request.ingredients[0]));
        }
        
        JsonObject output = new JsonObject();
        String outputGas = (String) request.properties.getOrDefault("outputGas", "mekanism:hydrogen");
        int outputAmount = getIntValue(request.properties.get("outputAmount"), 100);
        output.addProperty("gas", outputGas);
        output.addProperty("amount", outputAmount);
        recipe.add("output", output);
    }

    private void createEnergyConversionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = new JsonObject();
            input.add("ingredient", createIngredientJson(request.ingredients[0]));
            recipe.add("input", input);
        }
        
        long energy = ((Number) request.properties.getOrDefault("energy", 1000)).longValue();
        recipe.addProperty("output", energy);
    }

    private void createRotaryCondensentratorRecipe(JsonObject recipe, RecipeRequest request) {
        recipe.addProperty("type", "mekanism:rotary");
        
        String mode = (String) request.properties.getOrDefault("rotaryMode", "reversible");
        
        switch (mode) {
            case "reversible" -> {
                JsonObject fluidInput = new JsonObject();
                String fluidInputName = (String) request.properties.getOrDefault("fluidInput", "minecraft:water");
                int fluidInputAmount = getIntValue(request.properties.get("fluidInputAmount"), 100);
                fluidInput.addProperty("fluid", fluidInputName);
                fluidInput.addProperty("amount", fluidInputAmount);
                recipe.add("fluidInput", fluidInput);
                
                String gasOutputName = (String) request.properties.getOrDefault("gasOutput", "mekanism:steam");
                int gasOutputAmount = getIntValue(request.properties.get("gasOutputAmount"), 100);
                
                JsonObject gasInput = new JsonObject();
                gasInput.addProperty("gas", gasOutputName);
                gasInput.addProperty("amount", gasOutputAmount);
                recipe.add("gasInput", gasInput);
                
                JsonObject fluidOutput = new JsonObject();
                fluidOutput.addProperty("fluid", fluidInputName);
                fluidOutput.addProperty("amount", fluidInputAmount);
                recipe.add("fluidOutput", fluidOutput);
                
                JsonObject gasOutput = new JsonObject();
                gasOutput.addProperty("gas", gasOutputName);
                gasOutput.addProperty("amount", gasOutputAmount);
                recipe.add("gasOutput", gasOutput);
            }
            
            case "evaporation" -> {
                JsonObject fluidInput = new JsonObject();
                String fluidInputName = (String) request.properties.getOrDefault("fluidInput", "minecraft:water");
                int fluidInputAmount = getIntValue(request.properties.get("fluidInputAmount"), 100);
                fluidInput.addProperty("fluid", fluidInputName);
                fluidInput.addProperty("amount", fluidInputAmount);
                recipe.add("fluidInput", fluidInput);
                
                JsonObject gasOutput = new JsonObject();
                String gasOutputName = (String) request.properties.getOrDefault("gasOutput", "mekanism:steam");
                int gasOutputAmount = getIntValue(request.properties.get("gasOutputAmount"), 100);
                gasOutput.addProperty("gas", gasOutputName);
                gasOutput.addProperty("amount", gasOutputAmount);
                recipe.add("gasOutput", gasOutput);
            }
            
            case "condensation" -> {
                JsonObject gasInput = new JsonObject();
                String gasInputName = (String) request.properties.getOrDefault("gasInput", "mekanism:steam");
                int gasInputAmount = getIntValue(request.properties.get("gasInputAmount"), 100);
                gasInput.addProperty("gas", gasInputName);
                gasInput.addProperty("amount", gasInputAmount);
                recipe.add("gasInput", gasInput);
                
                JsonObject fluidOutput = new JsonObject();
                String fluidOutputName = (String) request.properties.getOrDefault("fluidOutput", "minecraft:water");
                int fluidOutputAmount = getIntValue(request.properties.get("fluidOutputAmount"), 100);
                fluidOutput.addProperty("fluid", fluidOutputName);
                fluidOutput.addProperty("amount", fluidOutputAmount);
                recipe.add("fluidOutput", fluidOutput);
            }
        }
    }

    private void createElectrolyticSeparatorRecipe(JsonObject recipe, RecipeRequest request) {
        JsonObject fluidInput = new JsonObject();
        String fluidInputName = (String) request.properties.getOrDefault("fluidInput", "minecraft:water");
        int fluidInputAmount = getIntValue(request.properties.get("fluidInputAmount"), 100);
        fluidInput.addProperty("fluid", fluidInputName);
        fluidInput.addProperty("amount", fluidInputAmount);
        recipe.add("input", fluidInput);
        
        JsonObject leftGasOutput = new JsonObject();
        String leftGas = (String) request.properties.getOrDefault("leftGasOutput", "mekanism:hydrogen");
        int leftGasAmount = getIntValue(request.properties.get("leftGasOutputAmount"), 100);
        leftGasOutput.addProperty("gas", leftGas);
        leftGasOutput.addProperty("amount", leftGasAmount);
        recipe.add("leftGasOutput", leftGasOutput);
        
        JsonObject rightGasOutput = new JsonObject();
        String rightGas = (String) request.properties.getOrDefault("rightGasOutput", "mekanism:oxygen");
        int rightGasAmount = getIntValue(request.properties.get("rightGasOutputAmount"), 100);
        rightGasOutput.addProperty("gas", rightGas);
        rightGasOutput.addProperty("amount", rightGasAmount);
        recipe.add("rightGasOutput", rightGasOutput);
    }

    private void createPressurizedReactionChamberRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", createIngredientJson(request.ingredients[0]));
        }
        
        JsonObject fluidInput = new JsonObject();
        String fluidInputName = (String) request.properties.getOrDefault("fluidInput", "minecraft:water");
        int fluidInputAmount = getIntValue(request.properties.get("fluidInputAmount"), 100);
        fluidInput.addProperty("fluid", fluidInputName);
        fluidInput.addProperty("amount", fluidInputAmount);
        recipe.add("fluidInput", fluidInput);
        
        JsonObject gasInput = new JsonObject();
        String gasInputName = (String) request.properties.getOrDefault("gasInput", "mekanism:hydrogen");
        int gasInputAmount = getIntValue(request.properties.get("gasInputAmount"), 100);
        gasInput.addProperty("gas", gasInputName);
        gasInput.addProperty("amount", gasInputAmount);
        recipe.add("gasInput", gasInput);
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
        
        JsonObject gasOutput = new JsonObject();
        String gasOutputName = (String) request.properties.getOrDefault("gasOutput", "mekanism:water");
        int gasOutputAmount = getIntValue(request.properties.get("gasOutputAmount"), 100);
        gasOutput.addProperty("gas", gasOutputName);
        gasOutput.addProperty("amount", gasOutputAmount);
        recipe.add("gasOutput", gasOutput);
        
        int duration = getIntValue(request.properties.get("duration"), 100);
        recipe.addProperty("duration", duration);
        
        int energyRequired = getIntValue(request.properties.get("energyRequired"), 1000);
        recipe.addProperty("energyRequired", energyRequired);
    }

    private void createIsotopicCentrifugeRecipe(JsonObject recipe, RecipeRequest request) {
        JsonObject gasInput = new JsonObject();
        String inputGas = (String) request.properties.getOrDefault("inputGas", "mekanism:uranium");
        int inputAmount = getIntValue(request.properties.get("inputAmount"), 100);
        gasInput.addProperty("gas", inputGas);
        gasInput.addProperty("amount", inputAmount);
        recipe.add("input", gasInput);
        
        JsonObject gasOutput = new JsonObject();
        String outputGas = (String) request.properties.getOrDefault("outputGas", "mekanism:uranium_235");
        int outputAmount = getIntValue(request.properties.get("outputAmount"), 100);
        gasOutput.addProperty("gas", outputGas);
        gasOutput.addProperty("amount", outputAmount);
        recipe.add("output", gasOutput);
    }

    private void createSolarNeutronActivatorRecipe(JsonObject recipe, RecipeRequest request) {
        JsonObject gasInput = new JsonObject();
        String inputGas = (String) request.properties.getOrDefault("inputGas", "mekanism:lithium");
        int inputAmount = getIntValue(request.properties.get("inputAmount"), 100);
        gasInput.addProperty("gas", inputGas);
        gasInput.addProperty("amount", inputAmount);
        recipe.add("input", gasInput);
        
        JsonObject gasOutput = new JsonObject();
        String outputGas = (String) request.properties.getOrDefault("outputGas", "mekanism:tritium");
        int outputAmount = getIntValue(request.properties.get("outputAmount"), 100);
        gasOutput.addProperty("gas", outputGas);
        gasOutput.addProperty("amount", outputAmount);
        recipe.add("output", gasOutput);
    }

    private void createAntiprotonicNucleosynthesizerRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", createIngredientJson(request.ingredients[0]));
        }
        
        JsonObject gasInput = new JsonObject();
        String gasInputName = (String) request.properties.getOrDefault("gasInput", "mekanism:antimatter");
        int gasInputAmount = getIntValue(request.properties.get("gasInputAmount"), 100);
        gasInput.addProperty("gas", gasInputName);
        gasInput.addProperty("amount", gasInputAmount);
        recipe.add("gasInput", gasInput);
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
        
        int duration = getIntValue(request.properties.get("duration"), 500);
        recipe.addProperty("duration", duration);
    }

    private void createThermalEvaporationRecipe(JsonObject recipe, RecipeRequest request) {
        JsonObject fluidInput = new JsonObject();
        String fluidInputName = (String) request.properties.getOrDefault("fluidInput", "minecraft:water");
        int fluidInputAmount = getIntValue(request.properties.get("fluidInputAmount"), 100);
        fluidInput.addProperty("fluid", fluidInputName);
        fluidInput.addProperty("amount", fluidInputAmount);
        recipe.add("input", fluidInput);
        
        JsonObject fluidOutput = new JsonObject();
        String fluidOutputName = (String) request.properties.getOrDefault("fluidOutput", "mekanism:brine");
        int fluidOutputAmount = getIntValue(request.properties.get("fluidOutputAmount"), 100);
        fluidOutput.addProperty("fluid", fluidOutputName);
        fluidOutput.addProperty("amount", fluidOutputAmount);
        recipe.add("output", fluidOutput);
    }

    private void createChemicalOxidizerRecipe(JsonObject recipe, RecipeRequest request) {
        JsonObject gasInput = new JsonObject();
        String inputGas = (String) request.properties.getOrDefault("inputGas", "mekanism:oxygen");
        int inputAmount = getIntValue(request.properties.get("inputAmount"), 100);
        gasInput.addProperty("gas", inputGas);
        gasInput.addProperty("amount", inputAmount);
        recipe.add("input", gasInput);
        
        JsonObject gasOutput = new JsonObject();
        String outputGas = (String) request.properties.getOrDefault("outputGas", "mekanism:oxygen");
        int outputAmount = getIntValue(request.properties.get("outputAmount"), 100);
        gasOutput.addProperty("gas", outputGas);
        gasOutput.addProperty("amount", outputAmount);
        recipe.add("output", gasOutput);
    }
}
