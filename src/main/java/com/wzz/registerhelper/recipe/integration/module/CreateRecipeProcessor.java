package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.wzz.registerhelper.util.RecipeUtil.createIngredientJson;

public class CreateRecipeProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("create");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        return switch (type) {
            case "create:emptying" -> createEmptyingRecipe(request);
            case "create:cutting" -> createCuttingRecipe(request);
            case "create:compacting" -> createCompactingRecipe(request);
            case "create:pressing" -> createPressingRecipe(request);
            case "create:filling" -> createFillingRecipe(request);
            case "create:mixing" -> createMixingRecipe(request);
            default -> null;
        };
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"emptying", "cutting", "compacting", "pressing", "mixing", "filling"};
    }

    /**
     * 创建emptying配方（物品 -> 物品 + 流体）
     */
    private JsonObject createEmptyingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:emptying");
        
        JsonArray ingredients = new JsonArray();
        JsonArray results = new JsonArray();
        
        // 输入物品
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject obj = createIngredientJson(ingredient);
                if (obj != null) {
                    ingredients.add(obj);
                }
            }
        }
        
        // 输出物品（从outputSlotItems或result）
        if (request.outputSlotItems != null && !request.outputSlotItems.isEmpty()) {
            for (Map.Entry<Integer, net.minecraft.world.item.ItemStack> entry : request.outputSlotItems.entrySet()) {
                net.minecraft.world.item.ItemStack stack = entry.getValue();
                if (!stack.isEmpty()) {
                    JsonObject resultItem = new JsonObject();
                    resultItem.addProperty("item", RecipeUtil.getItemResourceLocation(stack.getItem()).toString());
                    if (stack.getCount() > 1) {
                        resultItem.addProperty("count", stack.getCount());
                    }
                    results.add(resultItem);
                }
            }
        } else if (request.result != null && !request.result.isEmpty()) {
            JsonObject resultItem = new JsonObject();
            resultItem.addProperty("item", RecipeUtil.getItemResourceLocation(request.result.getItem()).toString());
            if (request.resultCount > 1) {
                resultItem.addProperty("count", request.resultCount);
            }
            results.add(resultItem);
        }
        
        // 输出流体（从properties或outputComponent）
        if (request.properties.containsKey("fluidOutput")) {
            String fluidId = (String) request.properties.get("fluidOutput");
            Object amountObj = request.properties.get("fluidOutputAmount");
            int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 250;
            
            JsonObject fluidResult = new JsonObject();
            fluidResult.addProperty("fluid", fluidId);
            fluidResult.addProperty("amount", amount);
            results.add(fluidResult);
        } else if (request.outputComponent instanceof FluidSlotComponent fluidComp) {
            if (fluidComp.getFluidId() != null && !fluidComp.getFluidId().isEmpty() && fluidComp.getAmount() > 0) {
                JsonObject fluidResult = new JsonObject();
                fluidResult.addProperty("fluid", fluidComp.getFluidId());
                fluidResult.addProperty("amount", (int) fluidComp.getAmount());
                results.add(fluidResult);
            }
        }
        
        recipe.add("ingredients", ingredients);
        recipe.add("results", results);
        return recipe;
    }

    private JsonObject createCuttingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:cutting");
        recipe.add("ingredients", buildIngredients(request.ingredients, false));
        recipe.add("results", buildResults(request));
        recipe.addProperty("processingTime", (int) request.properties.getOrDefault("processingTime", 200));
        return recipe;
    }

    /**
     * 创建compacting配方（物品 + 流体 -> 物品）
     */
    private JsonObject createCompactingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:compacting");
        
        JsonArray ingredients = buildIngredientsWithFluids(request);
        JsonArray results = buildResults(request);
        
        recipe.add("ingredients", ingredients);
        recipe.add("results", results);
        return recipe;
    }

    /**
     * 创建mixing配方（物品 + 流体 -> 物品 + 流体）
     */
    private JsonObject createMixingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:mixing");
        
        JsonArray ingredients = buildIngredientsWithFluids(request);
        JsonArray results = buildResultsWithFluids(request);
        
        // 添加热量需求（如果指定）
        String heatRequirement = (String) request.properties.get("heatRequirement");
        if (heatRequirement != null && !heatRequirement.isEmpty()) {
            recipe.addProperty("heatRequirement", heatRequirement);
        }
        
        recipe.add("ingredients", ingredients);
        recipe.add("results", results);
        return recipe;
    }

    private JsonObject createPressingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:pressing");
        recipe.add("ingredients", buildIngredients(request.ingredients, false));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    /**
     * 创建filling配方（物品 + 流体 -> 物品）
     */
    private JsonObject createFillingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:filling");

        JsonArray ingredients = new JsonArray();
        
        // 输入物品
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject obj = createIngredientJson(ingredient);
                if (obj != null && !obj.has("fluid")) {
                    ingredients.add(obj);
                }
            }
        }
        
        // 输入流体（从properties）
        if (request.properties.containsKey("fluidInput")) {
            String fluidId = (String) request.properties.get("fluidInput");
            Object amountObj = request.properties.get("fluidInputAmount");
            int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 250;
            
            JsonObject fluidIngredient = new JsonObject();
            fluidIngredient.addProperty("fluid", fluidId);
            fluidIngredient.addProperty("amount", amount);
            fluidIngredient.add("nbt", new JsonObject());
            ingredients.add(fluidIngredient);
        }

        recipe.add("ingredients", ingredients);
        recipe.add("results", buildResults(request));

        return recipe;
    }

    /**
     * 构建包含流体输入的ingredients数组
     */
    private JsonArray buildIngredientsWithFluids(RecipeRequest request) {
        JsonArray array = new JsonArray();
        
        // 添加物品输入
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject obj = RecipeUtil.createIngredientJson(ingredient);
                if (obj != null) {
                    array.add(obj);
                }
            }
        }
        
        // 添加流体输入（从properties）
        List<String> fluidInputKeys = new ArrayList<>();
        for (String key : request.properties.keySet()) {
            if (key.startsWith("fluid_input_") && key.endsWith("_id")) {
                fluidInputKeys.add(key);
            }
        }
        
        for (String key : fluidInputKeys) {
            String fluidId = (String) request.properties.get(key);
            if (fluidId != null && !fluidId.isEmpty()) {
                String slotId = key.replace("_id", "");
                Object amountObj = request.properties.get(slotId + "_amount");
                int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 250;
                
                JsonObject fluidIngredient = new JsonObject();
                fluidIngredient.addProperty("fluid", fluidId);
                fluidIngredient.addProperty("amount", amount);
                array.add(fluidIngredient);
            }
        }
        
        // 如果没有通过properties传递，尝试从fluidInput获取
        if (fluidInputKeys.isEmpty() && request.properties.containsKey("fluidInput")) {
            String fluidId = (String) request.properties.get("fluidInput");
            Object amountObj = request.properties.get("fluidInputAmount");
            int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 250;
            
            JsonObject fluidIngredient = new JsonObject();
            fluidIngredient.addProperty("fluid", fluidId);
            fluidIngredient.addProperty("amount", amount);
            array.add(fluidIngredient);
        }
        
        return array;
    }

    /**
     * 构建包含流体输出的results数组
     */
    private JsonArray buildResultsWithFluids(RecipeRequest request) {
        JsonArray array = buildResults(request);
        
        // 添加流体输出（从properties）
        List<String> fluidOutputKeys = new ArrayList<>();
        for (String key : request.properties.keySet()) {
            if (key.startsWith("fluid_output_") && key.endsWith("_id")) {
                fluidOutputKeys.add(key);
            }
        }
        
        for (String key : fluidOutputKeys) {
            String fluidId = (String) request.properties.get(key);
            if (fluidId != null && !fluidId.isEmpty()) {
                String slotId = key.replace("_id", "");
                Object amountObj = request.properties.get(slotId + "_amount");
                int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 250;
                
                JsonObject fluidResult = new JsonObject();
                fluidResult.addProperty("fluid", fluidId);
                fluidResult.addProperty("amount", amount);
                array.add(fluidResult);
            }
        }
        
        // 如果没有通过properties传递，尝试从fluidOutput获取
        if (fluidOutputKeys.isEmpty() && request.properties.containsKey("fluidOutput")) {
            String fluidId = (String) request.properties.get("fluidOutput");
            Object amountObj = request.properties.get("fluidOutputAmount");
            int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 250;
            
            JsonObject fluidResult = new JsonObject();
            fluidResult.addProperty("fluid", fluidId);
            fluidResult.addProperty("amount", amount);
            array.add(fluidResult);
        }
        
        return array;
    }

    @SuppressWarnings("unchecked")
    private JsonArray buildIngredients(Object[] ingredients, boolean allowFluid) {
        JsonArray array = new JsonArray();
        if (ingredients == null) return array;
        for (Object ingredient : ingredients) {
            JsonObject obj;
            // 物品 / tag / NBT
            obj = RecipeUtil.createIngredientJson(ingredient);
            if (allowFluid && ingredient instanceof Map map && map.containsKey("fluid")) {
                obj = new JsonObject();
                String fluidId = (String) map.get("fluid");
                int amount = (int) map.getOrDefault("amount", 100);
                obj.addProperty("fluid", fluidId);
                obj.addProperty("amount", amount);
                if (map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    obj.add("nbt", json);
                } else {
                    obj.add("nbt", new JsonObject());
                }
            }
            if (obj != null) array.add(obj);
        }
        return array;
    }

    private JsonArray buildResults(RecipeRequest request) {
        JsonArray array = new JsonArray();

        if (request.result != null && !request.result.isEmpty()) {
            JsonObject resultItem = new JsonObject();
            resultItem.addProperty("item", RecipeUtil.getItemResourceLocation(request.result.getItem()).toString());
            if (request.resultCount > 1) resultItem.addProperty("count", request.resultCount);
            array.add(resultItem);
        }
        
        // 添加输出槽物品
        if (request.outputSlotItems != null && !request.outputSlotItems.isEmpty()) {
            for (Map.Entry<Integer, net.minecraft.world.item.ItemStack> entry : request.outputSlotItems.entrySet()) {
                net.minecraft.world.item.ItemStack stack = entry.getValue();
                if (!stack.isEmpty()) {
                    JsonObject resultItem = new JsonObject();
                    resultItem.addProperty("item", RecipeUtil.getItemResourceLocation(stack.getItem()).toString());
                    if (stack.getCount() > 1) {
                        resultItem.addProperty("count", stack.getCount());
                    }
                    array.add(resultItem);
                }
            }
        }

        return array;
    }
}