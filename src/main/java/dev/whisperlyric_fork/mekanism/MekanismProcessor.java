package dev.whisperlyric_fork.mekanism;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.IngredientData;
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
        
        int amount = 1;
        Object ingredientWithoutCount = ingredient;
        
        if (ingredient instanceof IngredientData data) {
            if (data.getType() == IngredientData.Type.ITEM) {
                ItemStack stack = data.getItemStack();
                amount = stack.getCount();
                ItemStack copiedStack = stack.copy();
                copiedStack.setCount(1);
                ingredientWithoutCount = copiedStack;
            } else {
                ingredientWithoutCount = data;
            }
        } else if (ingredient instanceof ItemStack stack) {
            amount = stack.getCount();
            ItemStack copiedStack = stack.copy();
            copiedStack.setCount(1);
            ingredientWithoutCount = copiedStack;
        }
        
        wrapper.addProperty("amount", amount);
        wrapper.add("ingredient", createIngredientJson(ingredientWithoutCount));
        return wrapper;
    }
    
    private int getIntValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
    
    private boolean isEmptyIngredient(Object ingredient) {
        if (ingredient == null) return true;
        if (ingredient instanceof IngredientData data) return data.isEmpty();
        if (ingredient instanceof ItemStack stack) return stack.isEmpty();
        return false;
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
    
    private ItemStack getOutputItem(RecipeRequest request, int slotIndex) {
        if (request.outputSlotItems != null && request.outputSlotItems.containsKey(slotIndex)) {
            return request.outputSlotItems.get(slotIndex);
        }
        if (request.result != null && !request.result.isEmpty()) {
            return request.result;
        }
        return null;
    }
    
    private JsonObject createItemOutputJson(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(item));
        if (item.getCount() > 1) {
            output.addProperty("count", item.getCount());
        }
        return output;
    }
    
    private void addItemOutput(JsonObject recipe, String key, RecipeRequest request, int slotIndex) {
        ItemStack item = getOutputItem(request, slotIndex);
        JsonObject outputJson = createItemOutputJson(item);
        if (outputJson != null) {
            recipe.add(key, outputJson);
        }
    }
    
    private JsonObject createChemicalInputJson(String chemicalKey, String chemicalId, int amount) {
        JsonObject input = new JsonObject();
        input.addProperty(chemicalKey, chemicalId);
        input.addProperty("amount", amount);
        return input;
    }
    
    private JsonObject createGasInputJson(String gasId, int amountMB, int divisor) {
        return createChemicalInputJson("gas", gasId, amountMB / divisor);
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
            "separating",
            "gas_conversion",
            "infusion_conversion"
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
            case "mekanism:gas_conversion" -> createGasConversionRecipe(recipe, request);
            case "mekanism:infusion_conversion" -> createInfusionConversionRecipe(recipe, request);
        }
        
        return recipe;
    }

    private void createCrushingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        addItemOutput(recipe, "output", request, 1);
    }

    private void createEnrichingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        addItemOutput(recipe, "output", request, 1);
    }

    private void createSmeltingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        addItemOutput(recipe, "output", request, 1);
    }

    private void createCombiningRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("mainInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.ingredients != null && request.ingredients.length > 1) {
            recipe.add("extraInput", wrapIngredient(request.ingredients[1]));
        }
        addItemOutput(recipe, "output", request, 2);
    }

    private void createCompressingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("gas") && request.properties.containsKey("gasAmount")) {
            String gasId = (String) request.properties.get("gas");
            int gasAmountMB = getIntValue(request.properties.get("gasAmount"), 100);
            recipe.add("chemicalInput", createGasInputJson(gasId, gasAmountMB, 200));
        }
        addItemOutput(recipe, "output", request, 2);
    }

    private void createPurifyingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            String gasId = (String) request.properties.get("gasInput");
            int gasAmountMB = getIntValue(request.properties.get("gasInputAmount"), 100);
            recipe.add("chemicalInput", createGasInputJson(gasId, gasAmountMB, 200));
        }
        addItemOutput(recipe, "output", request, 2);
    }

    private void createInjectingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0 && !isEmptyIngredient(request.ingredients[0])) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            String gasId = (String) request.properties.get("gasInput");
            int gasAmountMB = getIntValue(request.properties.get("gasInputAmount"), 100);
            recipe.add("chemicalInput", createGasInputJson(gasId, gasAmountMB, 200));
        }
        addItemOutput(recipe, "output", request, 2);
    }

    private void createMetallurgicInfusingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0 && !isEmptyIngredient(request.ingredients[0])) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("infuseType") && request.properties.containsKey("infuseAmount")) {
            String infuseType = (String) request.properties.get("infuseType");
            int infuseAmount = getIntValue(request.properties.get("infuseAmount"), 0);
            if (infuseType != null && !infuseType.isEmpty() && infuseAmount > 0) {
                recipe.add("chemicalInput", createChemicalInputJson("infuse_type", infuseType, infuseAmount));
            }
        }
        addItemOutput(recipe, "output", request, 2);
    }

    private void createSawingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        
        ItemStack mainOutputItem = getOutputItem(request, 1);
        JsonObject mainOutputJson = createItemOutputJson(mainOutputItem);
        if (mainOutputJson != null) {
            recipe.add("mainOutput", mainOutputJson);
        }
        
        ItemStack secondaryOutputItem = request.outputSlotItems != null && request.outputSlotItems.containsKey(2)
            ? request.outputSlotItems.get(2)
            : (request.properties.get("extraOutput") instanceof ItemStack stack ? stack : null);
        
        JsonObject secondaryOutputJson = createItemOutputJson(secondaryOutputItem);
        if (secondaryOutputJson != null) {
            recipe.add("secondaryOutput", secondaryOutputJson);
            double secondaryChance = request.properties.containsKey("secondaryChance") 
                ? (double) request.properties.get("secondaryChance") : 1.0;
            recipe.addProperty("secondaryChance", secondaryChance);
        }
    }

    private void createChemicalInfusingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("leftGas") && request.properties.containsKey("leftAmount")) {
            recipe.add("leftInput", createChemicalInputJson("gas", 
                (String) request.properties.get("leftGas"), 
                getIntValue(request.properties.get("leftAmount"), 100)));
        }
        if (request.properties.containsKey("rightGas") && request.properties.containsKey("rightAmount")) {
            recipe.add("rightInput", createChemicalInputJson("gas",
                (String) request.properties.get("rightGas"),
                getIntValue(request.properties.get("rightAmount"), 100)));
        }
        if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
            recipe.add("output", createChemicalInputJson("gas",
                (String) request.properties.get("gasOutput"),
                getIntValue(request.properties.get("gasOutputAmount"), 100)));
        }
    }

    private void createCrystallizingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("chemicalType")) {
            recipe.addProperty("chemicalType", (String) request.properties.get("chemicalType"));
        }
        String chemicalType = (String) request.properties.getOrDefault("chemicalType", "gas");
        if (request.properties.containsKey("inputGas") && request.properties.containsKey("inputAmount")) {
            recipe.add("input", createChemicalInputJson(chemicalType,
                (String) request.properties.get("inputGas"),
                getIntValue(request.properties.get("inputAmount"), 100)));
        }
        addItemOutput(recipe, "output", request, 1);
    }

    private void createDissolutionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            String gasId = (String) request.properties.get("gasInput");
            int gasAmountMB = getIntValue(request.properties.get("gasInputAmount"), 100);
            recipe.add("gasInput", createGasInputJson(gasId, gasAmountMB, 100));
        }
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("chemicalOutput") && request.properties.containsKey("chemicalOutputAmount")) {
            String chemicalType = (String) request.properties.getOrDefault("chemicalType", "slurry");
            JsonObject output = new JsonObject();
            output.addProperty("chemicalType", chemicalType);
            output.addProperty(chemicalType, (String) request.properties.get("chemicalOutput"));
            output.addProperty("amount", getIntValue(request.properties.get("chemicalOutputAmount"), 1000));
            recipe.add("output", output);
        }
    }

    private void createEnergyConversionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
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
        if (request.ingredients != null && request.ingredients.length > 0 && !isEmptyIngredient(request.ingredients[0])) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
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
        
        int duration = request.properties.containsKey("duration") 
            ? getIntValue(request.properties.get("duration"), 100) 
            : 100;
        recipe.addProperty("duration", duration);
    }

    private void createIsotopicCentrifugeRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("gasInput"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("gasInputAmount"), 100));
            recipe.add("input", gasInput);
        }
        
        if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
            JsonObject gasOutput = new JsonObject();
            gasOutput.addProperty("gas", (String) request.properties.get("gasOutput"));
            gasOutput.addProperty("amount", getIntValue(request.properties.get("gasOutputAmount"), 100));
            recipe.add("output", gasOutput);
        }
    }

    private void createSolarNeutronActivatorRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            JsonObject gasInput = new JsonObject();
            gasInput.addProperty("gas", (String) request.properties.get("gasInput"));
            gasInput.addProperty("amount", getIntValue(request.properties.get("gasInputAmount"), 100));
            recipe.add("input", gasInput);
        }
        
        if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
            JsonObject gasOutput = new JsonObject();
            gasOutput.addProperty("gas", (String) request.properties.get("gasOutput"));
            gasOutput.addProperty("amount", getIntValue(request.properties.get("gasOutputAmount"), 100));
            recipe.add("output", gasOutput);
        }
    }

    private void createAntiprotonicNucleosynthesizerRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("gasInput") && request.properties.containsKey("gasInputAmount")) {
            recipe.add("gasInput", createChemicalInputJson("gas",
                (String) request.properties.get("gasInput"),
                getIntValue(request.properties.get("gasInputAmount"), 100)));
        }
        addItemOutput(recipe, "output", request, 2);
        recipe.addProperty("duration", getIntValue(request.properties.get("duration"), 500));
    }

    private void createEvaporatingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("fluidInput") && request.properties.containsKey("fluidInputAmount")) {
            recipe.add("input", createChemicalInputJson("fluid",
                (String) request.properties.get("fluidInput"),
                getIntValue(request.properties.get("fluidInputAmount"), 100)));
        }
        if (request.properties.containsKey("fluidOutput") && request.properties.containsKey("fluidOutputAmount")) {
            recipe.add("output", createChemicalInputJson("fluid",
                (String) request.properties.get("fluidOutput"),
                getIntValue(request.properties.get("fluidOutputAmount"), 100)));
        }
    }

    private void createOxidizerRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0 && !isEmptyIngredient(request.ingredients[0])) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
            recipe.add("output", createChemicalInputJson("gas",
                (String) request.properties.get("gasOutput"),
                getIntValue(request.properties.get("gasOutputAmount"), 100)));
        }
    }
    
    private void createWashingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("fluidInput") && request.properties.containsKey("fluidInputAmount")) {
            recipe.add("fluidInput", createChemicalInputJson("fluid",
                (String) request.properties.get("fluidInput"),
                getIntValue(request.properties.get("fluidInputAmount"), 5)));
        }
        if (request.properties.containsKey("inputGas") && request.properties.containsKey("inputAmount")) {
            recipe.add("slurryInput", createChemicalInputJson("slurry",
                (String) request.properties.get("inputGas"),
                getIntValue(request.properties.get("inputAmount"), 1)));
        }
        if (request.properties.containsKey("chemicalOutput") && request.properties.containsKey("chemicalOutputAmount")) {
            recipe.add("output", createChemicalInputJson("slurry",
                (String) request.properties.get("chemicalOutput"),
                getIntValue(request.properties.get("chemicalOutputAmount"), 1)));
        }
    }
    
    private void createPaintingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0 && !isEmptyIngredient(request.ingredients[0])) {
            recipe.add("itemInput", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("pigment") && request.properties.containsKey("pigmentAmount")) {
            String pigment = (String) request.properties.get("pigment");
            int pigmentAmount = getIntValue(request.properties.get("pigmentAmount"), 0);
            if (pigment != null && !pigment.isEmpty() && pigmentAmount > 0) {
                recipe.add("chemicalInput", createChemicalInputJson("pigment", pigment, pigmentAmount));
            }
        }
        addItemOutput(recipe, "output", request, 2);
    }
    
    private void createPigmentMixingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("leftPigment") && request.properties.containsKey("leftAmount")) {
            recipe.add("leftInput", createChemicalInputJson("pigment",
                (String) request.properties.get("leftPigment"),
                getIntValue(request.properties.get("leftAmount"), 1)));
        }
        if (request.properties.containsKey("rightPigment") && request.properties.containsKey("rightAmount")) {
            recipe.add("rightInput", createChemicalInputJson("pigment",
                (String) request.properties.get("rightPigment"),
                getIntValue(request.properties.get("rightAmount"), 1)));
        }
        if (request.properties.containsKey("outputPigment") && request.properties.containsKey("outputAmount")) {
            recipe.add("output", createChemicalInputJson("pigment",
                (String) request.properties.get("outputPigment"),
                getIntValue(request.properties.get("outputAmount"), 2)));
        }
    }
    
    private void createPigmentExtractingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("outputPigment") && request.properties.containsKey("outputAmount")) {
            recipe.add("output", createChemicalInputJson("pigment",
                (String) request.properties.get("outputPigment"),
                getIntValue(request.properties.get("outputAmount"), 192)));
        }
    }
    
    private void createSeparatingRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.properties.containsKey("fluid") && request.properties.containsKey("fluidAmount")) {
            recipe.add("input", createChemicalInputJson("fluid",
                (String) request.properties.get("fluid"),
                getIntValue(request.properties.get("fluidAmount"), 2)));
        }
        if (request.properties.containsKey("leftGas") && request.properties.containsKey("leftGasAmount")) {
            recipe.add("leftGasOutput", createChemicalInputJson("gas",
                (String) request.properties.get("leftGas"),
                getIntValue(request.properties.get("leftGasAmount"), 2)));
        }
        if (request.properties.containsKey("rightGas") && request.properties.containsKey("rightGasAmount")) {
            recipe.add("rightGasOutput", createChemicalInputJson("gas",
                (String) request.properties.get("rightGas"),
                getIntValue(request.properties.get("rightGasAmount"), 1)));
        }
        recipe.addProperty("energyMultiplier", 
            request.properties.containsKey("energyMultiplier") ? (double) request.properties.get("energyMultiplier") : 1.0);
    }

    private void createGasConversionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("gasOutput") && request.properties.containsKey("gasOutputAmount")) {
            recipe.add("output", createChemicalInputJson("gas",
                (String) request.properties.get("gasOutput"),
                getIntValue(request.properties.get("gasOutputAmount"), 100)));
        }
    }

    private void createInfusionConversionRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", wrapIngredient(request.ingredients[0]));
        }
        if (request.properties.containsKey("chemicalOutput") && request.properties.containsKey("chemicalOutputAmount")) {
            recipe.add("output", createChemicalInputJson("infuse_type",
                (String) request.properties.get("chemicalOutput"),
                getIntValue(request.properties.get("chemicalOutputAmount"), 100)));
        }
    }
}
