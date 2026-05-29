package dev.whisperlyric_fork.mekanism;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Map;

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
            "rotary",
            "reaction",
            "centrifuging",
            "activating",
            "nucleosynthesizing",
            "evaporating",
            "oxidizing",
            "washing",
            "painting",
            "pigment_mixing",
            "pigment_extracting",
            "separating"
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
            case "mekanism:rotary" -> createRotaryRecipe(recipe, request);
            case "mekanism:reaction" -> createReactionRecipe(recipe, request);
            case "mekanism:centrifuging" -> createIsotopicCentrifugeRecipe(recipe, request);
            case "mekanism:activating" -> createSolarNeutronActivatorRecipe(recipe, request);
            case "mekanism:nucleosynthesizing" -> createAntiprotonicNucleosynthesizerRecipe(recipe, request);
            case "mekanism:evaporating" -> createEvaporatingRecipe(recipe, request);
            case "mekanism:oxidizing" -> createOxidizerRecipe(recipe, request);
            case "mekanism:washing" -> createWashingRecipe(recipe, request);
            case "mekanism:painting" -> createPaintingRecipe(recipe, request);
            case "mekanism:pigment_mixing" -> createPigmentMixingRecipe(recipe, request);
            case "mekanism:pigment_extracting" -> createPigmentExtractingRecipe(recipe, request);
            case "mekanism:separating" -> createSeparatingRecipe(recipe, request);
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
        
        if (request.properties.containsKey("gas") && request.properties.containsKey("gasAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("gas"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("gasAmount"), 100));
            recipe.add("gasInput", gasInput);
        }
        
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
        
        if (request.properties.containsKey("gas") && request.properties.containsKey("gasAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("gas"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("gasAmount"), 100));
            recipe.add("gasInput", gasInput);
        }
        
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
        
        if (request.properties.containsKey("gas") && request.properties.containsKey("gasAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("gas"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("gasAmount"), 100));
            recipe.add("gasInput", gasInput);
        }
        
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
        
        if (request.properties.containsKey("infusionType") && request.properties.containsKey("infusionAmount")) {
            JsonObject infusionInput = new JsonObject();
            infusionInput.addProperty("infuse_type", (String) request.properties.get("infusionType"));
            infusionInput.addProperty("amount", getIntValue(request.properties.get("infusionAmount"), 10));
            recipe.add("infusionInput", infusionInput);
        }
        
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
            
            if (request.properties.containsKey("secondaryChance")) {
                recipe.addProperty("secondaryChance", (double) request.properties.get("secondaryChance"));
            }
        }
    }

    private void createChemicalInfusingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("leftGas") && request.properties.containsKey("leftAmount")) {
            JsonObject leftInput = new JsonObject();
            leftInput.addProperty("gas", (String) request.properties.get("leftGas"));
            leftInput.addProperty("amount", getIntValue(request.properties.get("leftAmount"), 100));
            recipe.add("leftInput", leftInput);
        }
        
        if (request.properties.containsKey("rightGas") && request.properties.containsKey("rightAmount")) {
            JsonObject rightInput = new JsonObject();
            rightInput.addProperty("gas", (String) request.properties.get("rightGas"));
            rightInput.addProperty("amount", getIntValue(request.properties.get("rightAmount"), 100));
            recipe.add("rightInput", rightInput);
        }
        
        if (request.properties.containsKey("outputGas") && request.properties.containsKey("outputAmount")) {
            JsonObject output = new JsonObject();
            output.addProperty("gas", (String) request.properties.get("outputGas"));
            output.addProperty("amount", getIntValue(request.properties.get("outputAmount"), 100));
            recipe.add("output", output);
        }
    }

    private void createCrystallizingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("chemicalType")) {
            recipe.addProperty("chemicalType", (String) request.properties.get("chemicalType"));
        }
        
        String chemicalType = (String) request.properties.getOrDefault("chemicalType", "gas");
        if (request.properties.containsKey("inputGas") && request.properties.containsKey("inputAmount")) {
            JsonObject input = new JsonObject();
            input.addProperty(chemicalType, (String) request.properties.get("inputGas"));
            input.addProperty("amount", getIntValue(request.properties.get("inputAmount"), 100));
            recipe.add("input", input);
        }
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }

    private void createDissolutionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("inputGas") && request.properties.containsKey("inputAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("inputGas"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("inputAmount"), 100));
            recipe.add("gasInput", gasInput);
        }
        
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", createIngredientJson(request.ingredients[0]));
        }
        
        if (request.properties.containsKey("outputGas") && request.properties.containsKey("outputAmount")) {
            JsonObject output = new JsonObject();
            output.addProperty("gas", (String) request.properties.get("outputGas"));
            output.addProperty("amount", getIntValue(request.properties.get("outputAmount"), 100));
            recipe.add("output", output);
        }
    }

    private void createEnergyConversionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = new JsonObject();
            input.add("ingredient", createIngredientJson(request.ingredients[0]));
            recipe.add("input", input);
        }
        
        if (request.properties.containsKey("energy")) {
            recipe.addProperty("output", ((Number) request.properties.get("energy")).longValue());
        }
    }

    private void createRotaryRecipe(JsonObject recipe, RecipeRequest request) {
        recipe.addProperty("type", "mekanism:rotary");
        
        String mode = (String) request.properties.getOrDefault("rotaryMode", "reversible");
        
        switch (mode) {
            case "reversible" -> {
                if (request.properties.containsKey("fluidInput") && request.properties.containsKey("fluidInputAmount")) {
                    JsonObject fluidInput = new JsonObject();
                    fluidInput.addProperty("fluid", (String) request.properties.get("fluidInput"));
                    fluidInput.addProperty("amount", getIntValue(request.properties.get("fluidInputAmount"), 100));
                    recipe.add("fluidInput", fluidInput);
                }
                
                if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
                    String gasOutputName = (String) request.properties.get("gasOutput");
                    int gasOutputAmount = getIntValue(request.properties.get("gasOutputAmount"), 100);
                    
                    JsonObject gasInput = new JsonObject();
                    gasInput.addProperty("gas", gasOutputName);
                    gasInput.addProperty("amount", gasOutputAmount);
                    recipe.add("gasInput", gasInput);
                    
                    if (request.properties.containsKey("fluidInput") && request.properties.containsKey("fluidInputAmount")) {
                        JsonObject fluidOutput = new JsonObject();
                        fluidOutput.addProperty("fluid", (String) request.properties.get("fluidInput"));
                        fluidOutput.addProperty("amount", getIntValue(request.properties.get("fluidInputAmount"), 100));
                        recipe.add("fluidOutput", fluidOutput);
                    }
                    
                    JsonObject gasOutput = new JsonObject();
                    gasOutput.addProperty("gas", gasOutputName);
                    gasOutput.addProperty("amount", gasOutputAmount);
                    recipe.add("gasOutput", gasOutput);
                }
            }
            
            case "decondensation" -> {
                if (request.properties.containsKey("fluidInput") && request.properties.containsKey("fluidInputAmount")) {
                    JsonObject fluidInput = new JsonObject();
                    fluidInput.addProperty("fluid", (String) request.properties.get("fluidInput"));
                    fluidInput.addProperty("amount", getIntValue(request.properties.get("fluidInputAmount"), 100));
                    recipe.add("fluidInput", fluidInput);
                }
                
                if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
                    JsonObject gasOutput = new JsonObject();
                    gasOutput.addProperty("gas", (String) request.properties.get("gasOutput"));
                    gasOutput.addProperty("amount", getIntValue(request.properties.get("gasOutputAmount"), 100));
                    recipe.add("gasOutput", gasOutput);
                }
            }
            
            case "condensation" -> {
                if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
                    JsonObject gasInput = new JsonObject();
                    gasInput.addProperty("gas", (String) request.properties.get("gasInput"));
                    gasInput.addProperty("amount", getIntValue(request.properties.get("gasInputAmount"), 100));
                    recipe.add("gasInput", gasInput);
                }
                
                if (request.properties.containsKey("fluidOutput") && request.properties.containsKey("fluidOutputAmount")) {
                    JsonObject fluidOutput = new JsonObject();
                    fluidOutput.addProperty("fluid", (String) request.properties.get("fluidOutput"));
                    fluidOutput.addProperty("amount", getIntValue(request.properties.get("fluidOutputAmount"), 100));
                    recipe.add("fluidOutput", fluidOutput);
                }
            }
        }
    }

    private void createReactionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            Object ingredient = request.ingredients[0];
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                JsonObject itemInput = new JsonObject();
                itemInput.addProperty("amount", stack.getCount());
                JsonObject ingredientJson = new JsonObject();
                ingredientJson.addProperty("item", getItemId(stack));
                itemInput.add("ingredient", ingredientJson);
                recipe.add("itemInput", itemInput);
            }
        }
        
        if (request.properties.containsKey("fluidInput") && request.properties.containsKey("fluidInputAmount")) {
            JsonObject fluidInput = new JsonObject();
            fluidInput.addProperty("fluid", (String) request.properties.get("fluidInput"));
            fluidInput.addProperty("amount", getIntValue(request.properties.get("fluidInputAmount"), 100));
            recipe.add("fluidInput", fluidInput);
        }
        
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("gasInput"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("gasInputAmount"), 100));
            recipe.add("gasInput", gasInput);
        }
        
        if (request.outputSlotItems != null) {
            for (Map.Entry<Integer, ItemStack> entry : request.outputSlotItems.entrySet()) {
                ItemStack outputItem = entry.getValue();
                if (outputItem != null && !outputItem.isEmpty()) {
                    JsonObject itemOutput = new JsonObject();
                    itemOutput.addProperty("item", getItemId(outputItem));
                    if (outputItem.getCount() > 1) {
                        itemOutput.addProperty("count", outputItem.getCount());
                    }
                    recipe.add("itemOutput", itemOutput);
                    break;
                }
            }
        }
        
        if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
            JsonObject gasOutput = new JsonObject();
            gasOutput.addProperty("gas", (String) request.properties.get("gasOutput"));
            gasOutput.addProperty("amount", getIntValue(request.properties.get("gasOutputAmount"), 100));
            recipe.add("gasOutput", gasOutput);
        }
        
        if (request.properties.containsKey("duration")) {
            recipe.addProperty("duration", getIntValue(request.properties.get("duration"), 100));
        }
        
        if (request.properties.containsKey("energyRequired")) {
            recipe.addProperty("energyRequired", getIntValue(request.properties.get("energyRequired"), 1000));
        }
    }

    private void createIsotopicCentrifugeRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("inputGas") && request.properties.containsKey("inputAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("inputGas"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("inputAmount"), 100));
            recipe.add("input", gasInput);
        }
        
        if (request.properties.containsKey("outputGas") && request.properties.containsKey("outputAmount")) {
            JsonObject gasOutput = new JsonObject();
            gasOutput.addProperty("gas", (String) request.properties.get("outputGas"));
            gasOutput.addProperty("amount", getIntValue(request.properties.get("outputAmount"), 100));
            recipe.add("output", gasOutput);
        }
    }

    private void createSolarNeutronActivatorRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("inputGas") && request.properties.containsKey("inputAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("inputGas"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("inputAmount"), 100));
            recipe.add("input", gasInput);
        }
        
        if (request.properties.containsKey("outputGas") && request.properties.containsKey("outputAmount")) {
            JsonObject gasOutput = new JsonObject();
            gasOutput.addProperty("gas", (String) request.properties.get("outputGas"));
            gasOutput.addProperty("amount", getIntValue(request.properties.get("outputAmount"), 100));
            recipe.add("output", gasOutput);
        }
    }

    private void createAntiprotonicNucleosynthesizerRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject itemInput = new JsonObject();
            itemInput.add("ingredient", createIngredientJson(request.ingredients[0]));
            recipe.add("itemInput", itemInput);
        }
        
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("gasInput"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("gasInputAmount"), 100));
            recipe.add("gasInput", gasInput);
        }
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
        
        if (request.properties.containsKey("duration")) {
            recipe.addProperty("duration", getIntValue(request.properties.get("duration"), 500));
        }
    }

    private void createEvaporatingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("fluidInput") && request.properties.containsKey("fluidInputAmount")) {
            JsonObject fluidInput = new JsonObject();
            fluidInput.addProperty("fluid", (String) request.properties.get("fluidInput"));
            fluidInput.addProperty("amount", getIntValue(request.properties.get("fluidInputAmount"), 100));
            recipe.add("input", fluidInput);
        }
        
        if (request.properties.containsKey("fluidOutput") && request.properties.containsKey("fluidOutputAmount")) {
            JsonObject fluidOutput = new JsonObject();
            fluidOutput.addProperty("fluid", (String) request.properties.get("fluidOutput"));
            fluidOutput.addProperty("amount", getIntValue(request.properties.get("fluidOutputAmount"), 100));
            recipe.add("output", fluidOutput);
        }
    }

    private void createOxidizerRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        
        if (request.properties.containsKey("outputGas") && request.properties.containsKey("outputAmount")) {
            JsonObject output = new JsonObject();
            output.addProperty("gas", (String) request.properties.get("outputGas"));
            output.addProperty("amount", getIntValue(request.properties.get("outputAmount"), 100));
            recipe.add("output", output);
        }
    }
    
    private void createWashingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("fluid") && request.properties.containsKey("fluidAmount")) {
            JsonObject fluidInput = new JsonObject();
            fluidInput.addProperty("fluid", (String) request.properties.get("fluid"));
            fluidInput.addProperty("amount", getIntValue(request.properties.get("fluidAmount"), 5));
            recipe.add("fluidInput", fluidInput);
        }
        
        if (request.properties.containsKey("inputSlurry") && request.properties.containsKey("inputSlurryAmount")) {
            JsonObject slurryInput = new JsonObject();
            slurryInput.addProperty("slurry", (String) request.properties.get("inputSlurry"));
            slurryInput.addProperty("amount", getIntValue(request.properties.get("inputSlurryAmount"), 1));
            recipe.add("slurryInput", slurryInput);
        }
        
        if (request.properties.containsKey("outputSlurry") && request.properties.containsKey("outputSlurryAmount")) {
            JsonObject output = new JsonObject();
            output.addProperty("slurry", (String) request.properties.get("outputSlurry"));
            output.addProperty("amount", getIntValue(request.properties.get("outputSlurryAmount"), 1));
            recipe.add("output", output);
        }
    }
    
    private void createPaintingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        
        if (request.properties.containsKey("pigment") && request.properties.containsKey("pigmentAmount")) {
            JsonObject chemicalInput = new JsonObject();
            chemicalInput.addProperty("pigment", (String) request.properties.get("pigment"));
            chemicalInput.addProperty("amount", getIntValue(request.properties.get("pigmentAmount"), 256));
            recipe.add("chemicalInput", chemicalInput);
        }
        
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);
    }
    
    private void createPigmentMixingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("leftPigment") && request.properties.containsKey("leftAmount")) {
            JsonObject leftInput = new JsonObject();
            leftInput.addProperty("pigment", (String) request.properties.get("leftPigment"));
            leftInput.addProperty("amount", getIntValue(request.properties.get("leftAmount"), 1));
            recipe.add("leftInput", leftInput);
        }
        
        if (request.properties.containsKey("rightPigment") && request.properties.containsKey("rightAmount")) {
            JsonObject rightInput = new JsonObject();
            rightInput.addProperty("pigment", (String) request.properties.get("rightPigment"));
            rightInput.addProperty("amount", getIntValue(request.properties.get("rightAmount"), 1));
            recipe.add("rightInput", rightInput);
        }
        
        if (request.properties.containsKey("outputPigment") && request.properties.containsKey("outputAmount")) {
            JsonObject output = new JsonObject();
            output.addProperty("pigment", (String) request.properties.get("outputPigment"));
            output.addProperty("amount", getIntValue(request.properties.get("outputAmount"), 2));
            recipe.add("output", output);
        }
    }
    
    private void createPigmentExtractingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        
        if (request.properties.containsKey("outputPigment") && request.properties.containsKey("outputAmount")) {
            JsonObject output = new JsonObject();
            output.addProperty("pigment", (String) request.properties.get("outputPigment"));
            output.addProperty("amount", getIntValue(request.properties.get("outputAmount"), 192));
            recipe.add("output", output);
        }
    }
    
    private void createSeparatingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("fluid") && request.properties.containsKey("fluidAmount")) {
            JsonObject input = new JsonObject();
            input.addProperty("fluid", (String) request.properties.get("fluid"));
            input.addProperty("amount", getIntValue(request.properties.get("fluidAmount"), 2));
            recipe.add("input", input);
        }
        
        if (request.properties.containsKey("leftGas") && request.properties.containsKey("leftGasAmount")) {
            JsonObject leftGasOutput = new JsonObject();
            leftGasOutput.addProperty("gas", (String) request.properties.get("leftGas"));
            leftGasOutput.addProperty("amount", getIntValue(request.properties.get("leftGasAmount"), 2));
            recipe.add("leftGasOutput", leftGasOutput);
        }
        
        if (request.properties.containsKey("rightGas") && request.properties.containsKey("rightGasAmount")) {
            JsonObject rightGasOutput = new JsonObject();
            rightGasOutput.addProperty("gas", (String) request.properties.get("rightGas"));
            rightGasOutput.addProperty("amount", getIntValue(request.properties.get("rightGasAmount"), 1));
            recipe.add("rightGasOutput", rightGasOutput);
        }
        
        if (request.properties.containsKey("energyMultiplier")) {
            recipe.addProperty("energyMultiplier", (double) request.properties.get("energyMultiplier"));
        }
    }
}
