package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wzz.registerhelper.init.ModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class KubeJsUtils {
    
    // 支持的配方类型映射
    private static final Map<String, RecipeConverter> RECIPE_CONVERTERS = new HashMap<>();
    
    static {
        // Avaritia 配方
        RECIPE_CONVERTERS.put("avaritia:shaped_table", KubeJsUtils::convertAvaritiaShapedToJS);
        RECIPE_CONVERTERS.put("avaritia:shapeless_table", KubeJsUtils::convertAvaritiaShapelessToJS);
        
        // 原版配方
        RECIPE_CONVERTERS.put("minecraft:crafting_shaped", KubeJsUtils::convertVanillaShapedToJS);
        RECIPE_CONVERTERS.put("minecraft:crafting_shapeless", KubeJsUtils::convertVanillaShapelessToJS);
        
        RECIPE_CONVERTERS.put("extendedcrafting:shaped_table", KubeJsUtils::convertExtendedShapedToJS);
        RECIPE_CONVERTERS.put("extendedcrafting:shapeless_table", KubeJsUtils::convertExtendedShapelessToJS);
        RECIPE_CONVERTERS.put("extendedcrafting:combination", KubeJsUtils::convertExtendedCombinationToJS);
        
        RECIPE_CONVERTERS.put("thermal:smelter", KubeJsUtils::convertThermalSmelterToJS);
        RECIPE_CONVERTERS.put("thermal:pulverizer", KubeJsUtils::convertThermalPulverizerToJS);
        RECIPE_CONVERTERS.put("thermal:centrifuge", KubeJsUtils::convertThermalCentrifugeToJS);
        
        RECIPE_CONVERTERS.put("mekanism:crushing", KubeJsUtils::convertMekanismCrushingToJS);
        RECIPE_CONVERTERS.put("mekanism:enriching", KubeJsUtils::convertMekanismEnrichingToJS);
        RECIPE_CONVERTERS.put("mekanism:smelting", KubeJsUtils::convertMekanismSmeltingToJS);
        RECIPE_CONVERTERS.put("mekanism:combining", KubeJsUtils::convertMekanismCombiningToJS);
        RECIPE_CONVERTERS.put("mekanism:compressing", KubeJsUtils::convertMekanismCompressingToJS);
        RECIPE_CONVERTERS.put("mekanism:purifying", KubeJsUtils::convertMekanismPurifyingToJS);
        RECIPE_CONVERTERS.put("mekanism:injecting", KubeJsUtils::convertMekanismInjectingToJS);
        RECIPE_CONVERTERS.put("mekanism:metallurgic_infusing", KubeJsUtils::convertMekanismMetallurgicInfusingToJS);
        RECIPE_CONVERTERS.put("mekanism:sawing", KubeJsUtils::convertMekanismSawingToJS);
        RECIPE_CONVERTERS.put("mekanism:chemical_infusing", KubeJsUtils::convertMekanismChemicalInfusingToJS);
        RECIPE_CONVERTERS.put("mekanism:crystallizing", KubeJsUtils::convertMekanismCrystallizingToJS);
        RECIPE_CONVERTERS.put("mekanism:dissolution", KubeJsUtils::convertMekanismDissolutionToJS);
        RECIPE_CONVERTERS.put("mekanism:energy_conversion", KubeJsUtils::convertMekanismEnergyConversionToJS);
        RECIPE_CONVERTERS.put("mekanism:gas_conversion", KubeJsUtils::convertMekanismGasConversionToJS);
        RECIPE_CONVERTERS.put("mekanism:infusion_conversion", KubeJsUtils::convertMekanismInfusionConversionToJS);
        RECIPE_CONVERTERS.put("mekanism:rotary", KubeJsUtils::convertMekanismRotaryToJS);
        RECIPE_CONVERTERS.put("mekanism:separating", KubeJsUtils::convertMekanismSeparatingToJS);
        RECIPE_CONVERTERS.put("mekanism:reaction", KubeJsUtils::convertMekanismReactionToJS);
        RECIPE_CONVERTERS.put("mekanism:centrifuging", KubeJsUtils::convertMekanismCentrifugingToJS);
        RECIPE_CONVERTERS.put("mekanism:activating", KubeJsUtils::convertMekanismActivatingToJS);
        RECIPE_CONVERTERS.put("mekanism:nucleosynthesizing", KubeJsUtils::convertMekanismNucleosynthesizingToJS);
        RECIPE_CONVERTERS.put("mekanism:evaporating", KubeJsUtils::convertMekanismEvaporatingToJS);
        RECIPE_CONVERTERS.put("mekanism:oxidizing", KubeJsUtils::convertMekanismOxidizingToJS);
        RECIPE_CONVERTERS.put("mekanism:washing", KubeJsUtils::convertMekanismWashingToJS);
        RECIPE_CONVERTERS.put("mekanism:painting", KubeJsUtils::convertMekanismPaintingToJS);
        RECIPE_CONVERTERS.put("mekanism:pigment_mixing", KubeJsUtils::convertMekanismPigmentMixingToJS);
        RECIPE_CONVERTERS.put("mekanism:pigment_extracting", KubeJsUtils::convertMekanismPigmentExtractingToJS);
        
        RECIPE_CONVERTERS.put("create:mixing", KubeJsUtils::convertCreateMixingToJS);
        RECIPE_CONVERTERS.put("create:cutting", KubeJsUtils::convertCreateCuttingToJS);
        RECIPE_CONVERTERS.put("create:pressing", KubeJsUtils::convertCreatePressingToJS);
        RECIPE_CONVERTERS.put("create:crushing", KubeJsUtils::convertCreateCrushingToJS);
    }
    
    @FunctionalInterface
    private interface RecipeConverter {
        String convert(JsonObject recipeJson);
    }

    // ==================== Avaritia 配方转换 ====================
    
    private static String convertAvaritiaShapedToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;
        
        script.append("    avaritia.shaped_table(\n");
        script.append("        ").append(tier).append(",\n");
        script.append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        for (int i = 0; i < pattern.size(); i++) {
            script.append("            '").append(pattern.get(i).getAsString()).append("'");
            if (i < pattern.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ],\n        {\n");
        
        JsonObject key = recipeJson.getAsJsonObject("key");
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            if (!first) script.append(",\n");
            script.append("            ").append(entry.getKey()).append(": ");
            script.append(formatIngredient(entry.getValue().getAsJsonObject()));
            first = false;
        }
        script.append("\n        }\n    )");
        return script.toString();
    }
    
    private static String convertAvaritiaShapelessToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;
        
        script.append("    avaritia.shapeless_table(\n");
        script.append("        ").append(tier).append(",\n");
        script.append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        return script.toString();
    }

    // ==================== 原版配方转换 ====================
    
    private static String convertVanillaShapedToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.shaped(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        for (int i = 0; i < pattern.size(); i++) {
            script.append("            '").append(pattern.get(i).getAsString()).append("'");
            if (i < pattern.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ],\n        {\n");
        
        JsonObject key = recipeJson.getAsJsonObject("key");
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            if (!first) script.append(",\n");
            script.append("            ").append(entry.getKey()).append(": ");
            script.append(formatIngredient(entry.getValue().getAsJsonObject()));
            first = false;
        }
        script.append("\n        }\n    )");
        return script.toString();
    }
    
    private static String convertVanillaShapelessToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.shapeless(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        return script.toString();
    }

    // ==================== Extended Crafting 配方转换 ====================
    
    private static String convertExtendedShapedToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 0;
        
        script.append("    event.recipes.extendedcrafting.shaped_table(\n");
        if (tier > 0) {
            script.append("        ").append(tier).append(",\n");
        }
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        for (int i = 0; i < pattern.size(); i++) {
            script.append("            '").append(pattern.get(i).getAsString()).append("'");
            if (i < pattern.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ],\n        {\n");
        
        JsonObject key = recipeJson.getAsJsonObject("key");
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            if (!first) script.append(",\n");
            script.append("            ").append(entry.getKey()).append(": ");
            script.append(formatIngredient(entry.getValue().getAsJsonObject()));
            first = false;
        }
        script.append("\n        }\n    )");
        return script.toString();
    }
    
    private static String convertExtendedShapelessToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 0;
        
        script.append("    event.recipes.extendedcrafting.shapeless_table(\n");
        if (tier > 0) {
            script.append("        ").append(tier).append(",\n");
        }
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        return script.toString();
    }
    
    private static String convertExtendedCombinationToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int powerCost = recipeJson.has("powerCost") ? recipeJson.get("powerCost").getAsInt() : 0;
        
        script.append("    event.recipes.extendedcrafting.combination(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        ").append(formatIngredient(recipeJson.getAsJsonObject("input")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        
        if (powerCost > 0) {
            script.append(".powerCost(").append(powerCost).append(")");
        }
        return script.toString();
    }

    // ==================== Thermal 配方转换 ====================
    
    private static String convertThermalSmelterToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.thermal.smelter(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("result");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        if (ingredients.size() == 1) {
            script.append(formatIngredient(ingredients.get(0).getAsJsonObject()));
        } else {
            script.append("[");
            for (int i = 0; i < ingredients.size(); i++) {
                script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
                if (i < ingredients.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        script.append("\n    )");
        
        if (recipeJson.has("energy")) {
            script.append(".energy(").append(recipeJson.get("energy").getAsInt()).append(")");
        }
        return script.toString();
    }
    
    private static String convertThermalPulverizerToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.thermal.pulverizer(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("result");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        
        if (recipeJson.has("energy")) {
            script.append(".energy(").append(recipeJson.get("energy").getAsInt()).append(")");
        }
        return script.toString();
    }
    
    private static String convertThermalCentrifugeToJS(JsonObject recipeJson) {
        return convertThermalPulverizerToJS(recipeJson).replace("pulverizer", "centrifuge");
    }

    // ==================== Mekanism 配方转换 ====================
    
    private static String convertMekanismCrushingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.crushing(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("input")));
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismEnrichingToJS(JsonObject recipeJson) {
        return convertMekanismCrushingToJS(recipeJson).replace("crushing", "enriching");
    }
    
    private static String convertMekanismSmeltingToJS(JsonObject recipeJson) {
        return convertMekanismCrushingToJS(recipeJson).replace("crushing", "smelting");
    }

    // ==================== Create 配方转换 ====================
    
    private static String convertCreateMixingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.mixing(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        [");
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(", ");
        }
        script.append("]\n    )");
        
        if (recipeJson.has("heatRequirement")) {
            script.append(".heated()");
        }
        return script.toString();
    }
    
    private static String convertCreateCuttingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.cutting(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        
        if (recipeJson.has("processingTime")) {
            script.append(".processingTime(").append(recipeJson.get("processingTime").getAsInt()).append(")");
        }
        return script.toString();
    }
    
    private static String convertCreatePressingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.pressing(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertCreateCrushingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.crushing(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        
        if (recipeJson.has("processingTime")) {
            script.append(".processingTime(").append(recipeJson.get("processingTime").getAsInt()).append(")");
        }
        return script.toString();
    }

    // ==================== 辅助方法 ====================
    
    private static String formatOutput(JsonObject result) {
        String item = result.get("item").getAsString();
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        
        StringBuilder output = new StringBuilder("'");
        if (count > 1) {
            output.append(count).append("x ");
        }
        output.append(item).append("'");
        return output.toString();
    }
    
    private static String formatIngredient(JsonObject ingredient) {
        if (ingredient.has("ingredient")) {
            JsonObject inner = ingredient.getAsJsonObject("ingredient");
            int amount = ingredient.has("amount") ? ingredient.get("amount").getAsInt() : 1;
            String innerFormat = formatIngredientObject(inner);
            if (amount > 1) {
                return "{ amount: " + amount + ", ingredient: " + innerFormat + " }";
            }
            return "{ ingredient: " + innerFormat + " }";
        } else if (ingredient.has("tag")) {
            return "{ ingredient: { tag: '" + ingredient.get("tag").getAsString() + "' } }";
        } else if (ingredient.has("item")) {
            String item = ingredient.get("item").getAsString();
            return "{ ingredient: { item: '" + item + "' } }";
        }
        return "{ ingredient: { item: 'minecraft:air' } }";
    }
    
    private static String formatIngredientObject(JsonObject ingredient) {
        if (ingredient.has("tag")) {
            return "{ tag: '" + ingredient.get("tag").getAsString() + "' }";
        } else if (ingredient.has("item")) {
            String item = ingredient.get("item").getAsString();
            return "{ item: '" + item + "' }";
        }
        return "{ item: 'minecraft:air' }";
    }
    
    private static String formatChemicalObject(JsonObject obj) {
        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        
        if (obj.has("amount")) {
            sb.append("amount: ").append(obj.get("amount").getAsInt());
            first = false;
        }
        
        String[] chemicalTypes = {"gas", "fluid", "slurry", "pigment", "infuse_type"};
        for (String type : chemicalTypes) {
            if (obj.has(type)) {
                if (!first) sb.append(", ");
                sb.append(type).append(": '").append(obj.get(type).getAsString()).append("'");
                first = false;
            }
        }
        
        sb.append(" }");
        return sb.toString();
    }
    
    private static String formatItemOutput(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        if (obj.has("item")) {
            sb.append("{ item: '").append(obj.get("item").getAsString()).append("'");
            if (obj.has("count")) {
                sb.append(", count: ").append(obj.get("count").getAsInt());
            }
            sb.append(" }");
        }
        return sb.toString();
    }

    // ==================== 主要导出方法 ====================
    
    public static List<Path> getAllJsonRecipeFiles() {
        List<Path> jsonFiles = new ArrayList<>();
        try {
            Path recipesDir = FMLPaths.CONFIGDIR.get().resolve("registerhelper/recipes");
            
            if (Files.exists(recipesDir)) {
                try (Stream<Path> paths = Files.walk(recipesDir)) {
                    paths.filter(Files::isRegularFile)
                         .filter(path -> path.toString().endsWith(".json"))
                         .forEach(jsonFiles::add);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonFiles;
    }
    
    public static void exportAllJsonRecipesToJS(boolean singleFile) {
        List<Path> jsonFiles = getAllJsonRecipeFiles();
        Path recipesBaseDir = FMLPaths.CONFIGDIR.get().resolve("registerhelper/recipes");
        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/rh_generated");

        if (singleFile) {
            Map<String, List<String>> recipesByMod = new LinkedHashMap<>();
            Map<String, Set<String>> recipeTypesByMod = new LinkedHashMap<>();
            Set<RotaryType> rotaryTypes = new HashSet<>();
            
            for (Path jsonFile : jsonFiles) {
                try {
                    String jsonContent = Files.readString(jsonFile);
                    JsonObject recipeJson = JsonParser.parseString(jsonContent).getAsJsonObject();
                    String recipeType = recipeJson.get("type").getAsString();
                    
                    RecipeConverter converter = RECIPE_CONVERTERS.get(recipeType);
                    if (converter == null) {
                        System.out.println("不支持的配方类型: " + recipeType + " (文件: " + jsonFile.getFileName() + ")");
                        continue;
                    }
                    
                    String jsScript = converter.convert(recipeJson);
                    String modName = recipeType.split(":")[0];
                    
                    recipesByMod.computeIfAbsent(modName, k -> new ArrayList<>()).add(jsScript);
                    recipeTypesByMod.computeIfAbsent(modName, k -> new HashSet<>()).add(recipeType);
                    
                    if (recipeType.equals("mekanism:rotary")) {
                        RotaryType rotaryType = getRotaryType(recipeJson);
                        if (rotaryType != null) {
                            rotaryTypes.add(rotaryType);
                        }
                    }
                    
                } catch (Exception e) {
                    System.out.println("解析配方失败: " + jsonFile);
                    e.printStackTrace();
                }
            }

            StringBuilder allRecipes = new StringBuilder();
            allRecipes.append("// Auto-generated from config/registerhelper/recipes\n");
            allRecipes.append("// Total recipes: ").append(jsonFiles.size()).append("\n\n");
            
            allRecipes.append("ServerEvents.recipes(event => {\n");
            
            if (recipesByMod.containsKey("avaritia")) {
                allRecipes.append("    const { avaritia } = event.recipes;\n");
            }
            
            allRecipes.append("\n");
            
            Set<String> allRecipeTypes = new HashSet<>();
            for (Set<String> types : recipeTypesByMod.values()) {
                allRecipeTypes.addAll(types);
            }
            
            for (String recipeType : allRecipeTypes) {
                if (FUNCTION_DEFINITIONS.containsKey(recipeType)) {
                    allRecipes.append(FUNCTION_DEFINITIONS.get(recipeType).generateFunctionDefinition()).append("\n");
                }
            }
            
            if (!rotaryTypes.isEmpty()) {
                if (rotaryTypes.contains(RotaryType.REVERSIBLE)) {
                    allRecipes.append("    // 回旋式气液转换器 - 可逆模式\n");
                    allRecipes.append("    function rotary(fluid, gas, fluidAmount, gasAmount) {\n");
                    allRecipes.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                    allRecipes.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                    allRecipes.append("        event.custom({\n");
                    allRecipes.append("            type: 'mekanism:rotary',\n");
                    allRecipes.append("            fluidInput: { amount: fluidAmount, fluid: fluid },\n");
                    allRecipes.append("            fluidOutput: { amount: fluidAmount, fluid: fluid },\n");
                    allRecipes.append("            gasInput: { amount: gasAmount, gas: gas },\n");
                    allRecipes.append("            gasOutput: { amount: gasAmount, gas: gas }\n");
                    allRecipes.append("        });\n");
                    allRecipes.append("    }\n\n");
                }
                
                if (rotaryTypes.contains(RotaryType.DECONDENSERATE)) {
                    allRecipes.append("    // 回旋式气液转换器 - 液体蒸发模式\n");
                    allRecipes.append("    function decondensentrate(fluidInput, gasOutput, fluidAmount, gasAmount) {\n");
                    allRecipes.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                    allRecipes.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                    allRecipes.append("        event.custom({\n");
                    allRecipes.append("            type: 'mekanism:rotary',\n");
                    allRecipes.append("            fluidInput: { amount: fluidAmount, fluid: fluidInput },\n");
                    allRecipes.append("            gasOutput: { amount: gasAmount, gas: gasOutput }\n");
                    allRecipes.append("        });\n");
                    allRecipes.append("    }\n\n");
                }
                
                if (rotaryTypes.contains(RotaryType.CONDENSERATE)) {
                    allRecipes.append("    // 回旋式气液转换器 - 气体冷凝模式\n");
                    allRecipes.append("    function condensentrate(gasInput, fluidOutput, gasAmount, fluidAmount) {\n");
                    allRecipes.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                    allRecipes.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                    allRecipes.append("        event.custom({\n");
                    allRecipes.append("            type: 'mekanism:rotary',\n");
                    allRecipes.append("            gasInput: { amount: gasAmount, gas: gasInput },\n");
                    allRecipes.append("            fluidOutput: { amount: fluidAmount, fluid: fluidOutput }\n");
                    allRecipes.append("        });\n");
                    allRecipes.append("    }\n\n");
                }
            }
            
            for (Map.Entry<String, List<String>> entry : recipesByMod.entrySet()) {
                allRecipes.append("    // ========== ").append(entry.getKey().toUpperCase())
                          .append(" Recipes (").append(entry.getValue().size()).append(") ==========\n\n");
                
                for (String recipe : entry.getValue()) {
                    allRecipes.append(recipe).append(";\n\n");
                }
            }
            
            allRecipes.append("});");

            try {
                Files.createDirectories(scriptsDir);
                Files.writeString(scriptsDir.resolve("registerhelper_recipes.js"), allRecipes.toString());
                System.out.println("成功导出 " + jsonFiles.size() + " 个配方到 kubejs/server_scripts/rh_generated/registerhelper_recipes.js");
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            for (Path jsonFile : jsonFiles) {
                try {
                    Path relativePath = recipesBaseDir.relativize(jsonFile);
                    String fileName = relativePath.toString()
                            .replace(".json", "")
                            .replace("\\", "_")
                            .replace("/", "_");
                    
                    String jsonContent = Files.readString(jsonFile);
                    JsonObject recipeJson = JsonParser.parseString(jsonContent).getAsJsonObject();
                    String recipeType = recipeJson.get("type").getAsString();
                    
                    RecipeConverter converter = RECIPE_CONVERTERS.get(recipeType);
                    if (converter == null) {
                        System.out.println("不支持的配方类型: " + recipeType);
                        continue;
                    }
                    
                    String jsScript = converter.convert(recipeJson);
                    
                    Files.createDirectories(scriptsDir);
                    Path outputPath = scriptsDir.resolve(fileName + ".js");

                    StringBuilder content = new StringBuilder();
                    content.append("ServerEvents.recipes(event => {\n");
                    
                    String modName = recipeType.split(":")[0];
                    if (modName.equals("avaritia")) {
                        content.append("    const { avaritia } = event.recipes;\n\n");
                    }
                    
                    if (FUNCTION_DEFINITIONS.containsKey(recipeType)) {
                        content.append(FUNCTION_DEFINITIONS.get(recipeType).generateFunctionDefinition()).append("\n");
                    }
                    
                    if (recipeType.equals("mekanism:rotary")) {
                        RotaryType rotaryType = getRotaryType(recipeJson);
                        
                        if (rotaryType == RotaryType.REVERSIBLE) {
                            content.append("    // 回旋式气液转换器 - 可逆模式\n");
                            content.append("    function rotary(fluid, gas, fluidAmount, gasAmount) {\n");
                            content.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                            content.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                            content.append("        event.custom({\n");
                            content.append("            type: 'mekanism:rotary',\n");
                            content.append("            fluidInput: { amount: fluidAmount, fluid: fluid },\n");
                            content.append("            fluidOutput: { amount: fluidAmount, fluid: fluid },\n");
                            content.append("            gasInput: { amount: gasAmount, gas: gas },\n");
                            content.append("            gasOutput: { amount: gasAmount, gas: gas }\n");
                            content.append("        });\n");
                            content.append("    }\n\n");
                        } else if (rotaryType == RotaryType.DECONDENSERATE) {
                            content.append("    // 回旋式气液转换器 - 液体蒸发模式\n");
                            content.append("    function decondensentrate(fluidInput, gasOutput, fluidAmount, gasAmount) {\n");
                            content.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                            content.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                            content.append("        event.custom({\n");
                            content.append("            type: 'mekanism:rotary',\n");
                            content.append("            fluidInput: { amount: fluidAmount, fluid: fluidInput },\n");
                            content.append("            gasOutput: { amount: gasAmount, gas: gasOutput }\n");
                            content.append("        });\n");
                            content.append("    }\n\n");
                        } else if (rotaryType == RotaryType.CONDENSERATE) {
                            content.append("    // 回旋式气液转换器 - 气体冷凝模式\n");
                            content.append("    function condensentrate(gasInput, fluidOutput, gasAmount, fluidAmount) {\n");
                            content.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                            content.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                            content.append("        event.custom({\n");
                            content.append("            type: 'mekanism:rotary',\n");
                            content.append("            gasInput: { amount: gasAmount, gas: gasInput },\n");
                            content.append("            fluidOutput: { amount: fluidAmount, fluid: fluidOutput }\n");
                            content.append("        });\n");
                            content.append("    }\n\n");
                        }
                    }
                    
                    content.append(jsScript).append(";\n");
                    content.append("});");
                    
                    Files.writeString(outputPath, content.toString());
                    
                } catch (Exception e) {
                    System.out.println("导出配方失败: " + jsonFile);
                    e.printStackTrace();
                }
            }
            System.out.println("成功导出 " + jsonFiles.size() + " 个配方到 kubejs/server_scripts/rh_generated/");
        }
    }
    
    public static void exportAllJsonRecipesByMod() {
        List<Path> jsonFiles = getAllJsonRecipeFiles();
        Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/rh_generated");
        
        Map<String, List<String>> recipesByMod = new LinkedHashMap<>();
        Map<String, Set<String>> recipeTypesByMod = new LinkedHashMap<>();
        Map<String, Set<RotaryType>> rotaryTypesByMod = new LinkedHashMap<>();
        
        for (Path jsonFile : jsonFiles) {
            try {
                String jsonContent = Files.readString(jsonFile);
                JsonObject recipeJson = JsonParser.parseString(jsonContent).getAsJsonObject();
                String recipeType = recipeJson.get("type").getAsString();
                
                RecipeConverter converter = RECIPE_CONVERTERS.get(recipeType);
                if (converter == null) {
                    System.out.println("不支持的配方类型: " + recipeType + " (文件: " + jsonFile.getFileName() + ")");
                    continue;
                }
                
                String jsScript = converter.convert(recipeJson);
                String modName = recipeType.split(":")[0];
                
                recipesByMod.computeIfAbsent(modName, k -> new ArrayList<>()).add(jsScript);
                recipeTypesByMod.computeIfAbsent(modName, k -> new HashSet<>()).add(recipeType);
                
                if (recipeType.equals("mekanism:rotary")) {
                    RotaryType rotaryType = getRotaryType(recipeJson);
                    if (rotaryType != null) {
                        rotaryTypesByMod.computeIfAbsent(modName, k -> new HashSet<>()).add(rotaryType);
                    }
                }
                
            } catch (Exception e) {
                System.out.println("解析配方失败: " + jsonFile);
                e.printStackTrace();
            }
        }
        
        try {
            Files.createDirectories(scriptsDir);
            
            for (Map.Entry<String, List<String>> entry : recipesByMod.entrySet()) {
                String modName = entry.getKey();
                List<String> recipes = entry.getValue();
                Set<String> recipeTypes = recipeTypesByMod.get(modName);
                Set<RotaryType> rotaryTypes = rotaryTypesByMod.getOrDefault(modName, new HashSet<>());
                
                StringBuilder content = new StringBuilder();
                content.append("// Auto-generated ").append(modName).append(" recipes\n");
                content.append("// Total recipes: ").append(recipes.size()).append("\n\n");
                
                content.append("ServerEvents.recipes(event => {\n");
                
                if (modName.equals("avaritia")) {
                    content.append("    const { avaritia } = event.recipes;\n\n");
                }
                
                for (String recipeType : recipeTypes) {
                    if (FUNCTION_DEFINITIONS.containsKey(recipeType)) {
                        content.append(FUNCTION_DEFINITIONS.get(recipeType).generateFunctionDefinition()).append("\n");
                    }
                }
                
                if (modName.equals("mekanism") && !rotaryTypes.isEmpty()) {
                    if (rotaryTypes.contains(RotaryType.REVERSIBLE)) {
                        content.append("    // 回旋式气液转换器 - 可逆模式\n");
                        content.append("    function rotary(fluid, gas, fluidAmount, gasAmount) {\n");
                        content.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                        content.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                        content.append("        event.custom({\n");
                        content.append("            type: 'mekanism:rotary',\n");
                        content.append("            fluidInput: { amount: fluidAmount, fluid: fluid },\n");
                        content.append("            fluidOutput: { amount: fluidAmount, fluid: fluid },\n");
                        content.append("            gasInput: { amount: gasAmount, gas: gas },\n");
                        content.append("            gasOutput: { amount: gasAmount, gas: gas }\n");
                        content.append("        });\n");
                        content.append("    }\n\n");
                    }
                    
                    if (rotaryTypes.contains(RotaryType.DECONDENSERATE)) {
                        content.append("    // 回旋式气液转换器 - 液体蒸发模式\n");
                        content.append("    function decondensentrate(fluidInput, gasOutput, fluidAmount, gasAmount) {\n");
                        content.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                        content.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                        content.append("        event.custom({\n");
                        content.append("            type: 'mekanism:rotary',\n");
                        content.append("            fluidInput: { amount: fluidAmount, fluid: fluidInput },\n");
                        content.append("            gasOutput: { amount: gasAmount, gas: gasOutput }\n");
                        content.append("        });\n");
                        content.append("    }\n\n");
                    }
                    
                    if (rotaryTypes.contains(RotaryType.CONDENSERATE)) {
                        content.append("    // 回旋式气液转换器 - 气体冷凝模式\n");
                        content.append("    function condensentrate(gasInput, fluidOutput, gasAmount, fluidAmount) {\n");
                        content.append("        gasAmount = gasAmount !== undefined ? gasAmount : 1;\n");
                        content.append("        fluidAmount = fluidAmount !== undefined ? fluidAmount : 1;\n");
                        content.append("        event.custom({\n");
                        content.append("            type: 'mekanism:rotary',\n");
                        content.append("            gasInput: { amount: gasAmount, gas: gasInput },\n");
                        content.append("            fluidOutput: { amount: fluidAmount, fluid: fluidOutput }\n");
                        content.append("        });\n");
                        content.append("    }\n\n");
                    }
                }
                
                for (String recipe : recipes) {
                    content.append(recipe).append(";\n\n");
                }
                
                content.append("});");
                
                Path outputPath = scriptsDir.resolve(modName + ".js");
                Files.writeString(outputPath, content.toString());
            }
            
            System.out.println("成功导出 " + recipesByMod.size() + " 个mod的配方到 kubejs/server_scripts/rh_generated/");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static List<String> getSupportedRecipeTypes() {
        return new ArrayList<>(RECIPE_CONVERTERS.keySet());
    }
    
    private static String convertMekanismCompressingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.compressing(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        if (recipeJson.has("chemicalInput")) {
            JsonObject chemicalInput = recipeJson.getAsJsonObject("chemicalInput");
            script.append(", { gas: '").append(chemicalInput.get("gas").getAsString()).append("'");
            script.append(", amount: ").append(chemicalInput.get("amount").getAsInt()).append(" }");
        }
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismPurifyingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.purifying(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        if (recipeJson.has("chemicalInput")) {
            JsonObject chemicalInput = recipeJson.getAsJsonObject("chemicalInput");
            script.append(", { gas: '").append(chemicalInput.get("gas").getAsString()).append("'");
            script.append(", amount: ").append(chemicalInput.get("amount").getAsInt()).append(" }");
        }
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismInjectingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.injecting(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        if (recipeJson.has("chemicalInput")) {
            JsonObject chemicalInput = recipeJson.getAsJsonObject("chemicalInput");
            script.append(", { gas: '").append(chemicalInput.get("gas").getAsString()).append("'");
            script.append(", amount: ").append(chemicalInput.get("amount").getAsInt()).append(" }");
        }
        script.append("\n    )");
        return script.toString();
    }
    
    private enum RotaryType {
        REVERSIBLE,      // 可逆模式
        DECONDENSERATE,  // 液体蒸发模式
        CONDENSERATE     // 气体冷凝模式
    }
    
    private static RotaryType getRotaryType(JsonObject recipeJson) {
        boolean hasFluidInput = recipeJson.has("fluidInput");
        boolean hasGasInput = recipeJson.has("gasInput");
        boolean hasFluidOutput = recipeJson.has("fluidOutput");
        boolean hasGasOutput = recipeJson.has("gasOutput");
        
        if (hasFluidInput && hasGasInput && hasFluidOutput && hasGasOutput) {
            return RotaryType.REVERSIBLE;
        } else if (hasFluidInput && hasGasOutput && !hasGasInput && !hasFluidOutput) {
            return RotaryType.DECONDENSERATE;
        } else if (hasGasInput && hasFluidOutput && !hasFluidInput && !hasGasOutput) {
            return RotaryType.CONDENSERATE;
        }
        return null;
    }
    
    @FunctionalInterface
    private interface FunctionDefinitionGenerator {
        String generateFunctionDefinition();
    }
    
    private static final Map<String, FunctionDefinitionGenerator> FUNCTION_DEFINITIONS = new HashMap<>();
    
    static {
        FUNCTION_DEFINITIONS.put("mekanism:separating", () -> 
            "    // 电解分离器\n" +
            "    function separating(input, leftGasOutput, rightGasOutput, energyMultiplier) {\n" +
            "        energyMultiplier = energyMultiplier !== undefined ? energyMultiplier : 1;\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:separating',\n" +
            "            input: input,\n" +
            "            leftGasOutput: leftGasOutput,\n" +
            "            rightGasOutput: rightGasOutput,\n" +
            "            energyMultiplier: energyMultiplier\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:reaction", () -> 
            "    // 加压反应室\n" +
            "    function reaction(itemInput, fluidInput, gasInput, itemOutput, gasOutput, duration, energyRequired) {\n" +
            "        duration = duration !== undefined ? duration : 100;\n" +
            "        energyRequired = energyRequired !== undefined ? energyRequired : 100;\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:reaction',\n" +
            "            itemInput: itemInput,\n" +
            "            fluidInput: fluidInput,\n" +
            "            gasInput: gasInput,\n" +
            "            itemOutput: itemOutput,\n" +
            "            gasOutput: gasOutput,\n" +
            "            duration: duration,\n" +
            "            energyRequired: energyRequired\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:centrifuging", () -> 
            "    // 同位素离心机\n" +
            "    function centrifuging(input, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:centrifuging',\n" +
            "            input: input,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:activating", () -> 
            "    // 太阳能中子活化器\n" +
            "    function activating(input, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:activating',\n" +
            "            input: input,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:nucleosynthesizing", () -> 
            "    // 反质子核合成器\n" +
            "    function nucleosynthesizing(itemInput, gasInput, output, duration) {\n" +
            "        duration = duration !== undefined ? duration : 100;\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:nucleosynthesizing',\n" +
            "            itemInput: itemInput,\n" +
            "            gasInput: gasInput,\n" +
            "            output: output,\n" +
            "            duration: duration\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:evaporating", () -> 
            "    // 热力蒸馏塔\n" +
            "    function evaporating(input, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:evaporating',\n" +
            "            input: input,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:oxidizing", () -> 
            "    // 化学氧化机\n" +
            "    function oxidizing(input, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:oxidizing',\n" +
            "            input: input,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:sawing", () -> 
            "    // 精密锯木机\n" +
            "    function sawing(input, mainOutput, secondaryOutput, secondaryChance) {\n" +
            "        secondaryChance = secondaryChance !== undefined ? secondaryChance : 0.05;\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:sawing',\n" +
            "            input: input,\n" +
            "            mainOutput: mainOutput,\n" +
            "            secondaryOutput: secondaryOutput,\n" +
            "            secondaryChance: secondaryChance\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:dissolution", () -> 
            "    // 化学溶解室\n" +
            "    function dissolution(gasInput, itemInput, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:dissolution',\n" +
            "            gasInput: gasInput,\n" +
            "            itemInput: itemInput,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:gas_conversion", () -> 
            "    // 物品到气体\n" +
            "    function gasConversion(input, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:gas_conversion',\n" +
            "            input: input,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:infusion_conversion", () -> 
            "    // 物品到灌注类型\n" +
            "    function infusionConversion(input, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:infusion_conversion',\n" +
            "            input: input,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:washing", () -> 
            "    // 化学清洗机\n" +
            "    function washing(fluidInput, slurryInput, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:washing',\n" +
            "            fluidInput: fluidInput,\n" +
            "            slurryInput: slurryInput,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:painting", () -> 
            "    // 上色机\n" +
            "    function painting(chemicalInput, itemInput, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:painting',\n" +
            "            chemicalInput: chemicalInput,\n" +
            "            itemInput: itemInput,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:pigment_mixing", () -> 
            "    // 颜料混合器\n" +
            "    function pigmentMixing(leftInput, rightInput, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:pigment_mixing',\n" +
            "            leftInput: leftInput,\n" +
            "            rightInput: rightInput,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
        
        FUNCTION_DEFINITIONS.put("mekanism:pigment_extracting", () -> 
            "    // 颜料提取器\n" +
            "    function pigmentExtracting(input, output) {\n" +
            "        event.custom({\n" +
            "            type: 'mekanism:pigment_extracting',\n" +
            "            input: input,\n" +
            "            output: output\n" +
            "        });\n" +
            "    }\n"
        );
    }
    
    private static String convertMekanismRotaryToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        
        boolean hasFluidInput = recipeJson.has("fluidInput");
        boolean hasGasInput = recipeJson.has("gasInput");
        boolean hasFluidOutput = recipeJson.has("fluidOutput");
        boolean hasGasOutput = recipeJson.has("gasOutput");
        
        if (hasFluidInput && hasGasInput && hasFluidOutput && hasGasOutput) {
            JsonObject fluidInput = recipeJson.getAsJsonObject("fluidInput");
            JsonObject gasInput = recipeJson.getAsJsonObject("gasInput");
            
            String fluid = fluidInput.get("fluid").getAsString();
            String gas = gasInput.get("gas").getAsString();
            int fluidAmount = fluidInput.get("amount").getAsInt();
            int gasAmount = gasInput.get("amount").getAsInt();
            
            script.append("    rotary('").append(fluid).append("', '").append(gas).append("'");
            if (fluidAmount != 1 || gasAmount != 1) {
                script.append(", ").append(fluidAmount).append(", ").append(gasAmount);
            }
            script.append(")");
        }
        else if (hasFluidInput && hasGasOutput && !hasGasInput && !hasFluidOutput) {
            JsonObject fluidInput = recipeJson.getAsJsonObject("fluidInput");
            JsonObject gasOutput = recipeJson.getAsJsonObject("gasOutput");
            
            String fluid = fluidInput.get("fluid").getAsString();
            String gas = gasOutput.get("gas").getAsString();
            int fluidAmount = fluidInput.get("amount").getAsInt();
            int gasAmount = gasOutput.get("amount").getAsInt();
            
            script.append("    decondensentrate('").append(fluid).append("', '").append(gas).append("'");
            if (fluidAmount != 1 || gasAmount != 1) {
                script.append(", ").append(fluidAmount).append(", ").append(gasAmount);
            }
            script.append(")");
        }
        else if (hasGasInput && hasFluidOutput && !hasFluidInput && !hasGasOutput) {
            JsonObject gasInput = recipeJson.getAsJsonObject("gasInput");
            JsonObject fluidOutput = recipeJson.getAsJsonObject("fluidOutput");
            
            String gas = gasInput.get("gas").getAsString();
            String fluid = fluidOutput.get("fluid").getAsString();
            int gasAmount = gasInput.get("amount").getAsInt();
            int fluidAmount = fluidOutput.get("amount").getAsInt();
            
            script.append("    condensentrate('").append(gas).append("', '").append(fluid).append("'");
            if (gasAmount != 1 || fluidAmount != 1) {
                script.append(", ").append(gasAmount).append(", ").append(fluidAmount);
            }
            script.append(")");
        }
        else {
            script.append("    event.custom({\n");
            script.append("        type: 'mekanism:rotary',\n");
            
            if (hasFluidInput) {
                JsonObject fluidInput = recipeJson.getAsJsonObject("fluidInput");
                script.append("        fluidInput: { amount: ").append(fluidInput.get("amount").getAsInt());
                script.append(", fluid: '").append(fluidInput.get("fluid").getAsString()).append("' },\n");
            }
            if (hasGasInput) {
                JsonObject gasInput = recipeJson.getAsJsonObject("gasInput");
                script.append("        gasInput: { amount: ").append(gasInput.get("amount").getAsInt());
                script.append(", gas: '").append(gasInput.get("gas").getAsString()).append("' },\n");
            }
            if (hasFluidOutput) {
                JsonObject fluidOutput = recipeJson.getAsJsonObject("fluidOutput");
                script.append("        fluidOutput: { amount: ").append(fluidOutput.get("amount").getAsInt());
                script.append(", fluid: '").append(fluidOutput.get("fluid").getAsString()).append("' },\n");
            }
            if (hasGasOutput) {
                JsonObject gasOutput = recipeJson.getAsJsonObject("gasOutput");
                script.append("        gasOutput: { amount: ").append(gasOutput.get("amount").getAsInt());
                script.append(", gas: '").append(gasOutput.get("gas").getAsString()).append("' }\n");
            }
            
            script.append("    })");
        }
        
        return script.toString();
    }
    
    private static String convertMekanismSeparatingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    separating(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("leftGasOutput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("leftGasOutput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("rightGasOutput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("rightGasOutput")));
        } else {
            script.append("        {}");
        }
        
        if (recipeJson.has("energyMultiplier")) {
            script.append(",\n        ").append(recipeJson.get("energyMultiplier").getAsDouble());
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismReactionToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    reaction(\n");
        
        if (recipeJson.has("itemInput")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("fluidInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("fluidInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("gasInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("gasInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("itemOutput")) {
            script.append("        ").append(formatItemOutput(recipeJson.getAsJsonObject("itemOutput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("gasOutput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("gasOutput")));
        } else {
            script.append("        {}");
        }
        
        if (recipeJson.has("duration") && recipeJson.has("energyRequired")) {
            script.append(",\n        ").append(recipeJson.get("duration").getAsInt());
            script.append(", ").append(recipeJson.get("energyRequired").getAsInt());
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismCentrifugingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    centrifuging(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismActivatingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    activating(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismNucleosynthesizingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    nucleosynthesizing(\n");
        
        if (recipeJson.has("itemInput")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("gasInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("gasInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatItemOutput(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        if (recipeJson.has("duration")) {
            script.append(",\n        ").append(recipeJson.get("duration").getAsInt());
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismEvaporatingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    evaporating(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismOxidizingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    oxidizing(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismCombiningToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.combining(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("mainInput")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("extraInput")));
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismMetallurgicInfusingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.metallurgic_infusing(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        if (recipeJson.has("chemicalInput")) {
            JsonObject chemicalInput = recipeJson.getAsJsonObject("chemicalInput");
            script.append(", { infuse_type: '").append(chemicalInput.get("infuse_type").getAsString()).append("'");
            script.append(", amount: ").append(chemicalInput.get("amount").getAsInt()).append(" }");
        }
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismSawingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    sawing(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("mainOutput")) {
            script.append("        ").append(formatItemOutput(recipeJson.getAsJsonObject("mainOutput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("secondaryOutput")) {
            script.append("        ").append(formatItemOutput(recipeJson.getAsJsonObject("secondaryOutput")));
        } else {
            script.append("        {}");
        }
        
        if (recipeJson.has("secondaryChance")) {
            script.append(",\n        ").append(recipeJson.get("secondaryChance").getAsDouble());
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismChemicalInfusingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.chemical_infusing(\n");
        if (recipeJson.has("output")) {
            JsonObject output = recipeJson.getAsJsonObject("output");
            script.append("        { gas: '").append(output.get("gas").getAsString()).append("'");
            script.append(", amount: ").append(output.get("amount").getAsInt()).append(" }");
        }
        script.append(",\n        ");
        if (recipeJson.has("leftInput")) {
            JsonObject leftInput = recipeJson.getAsJsonObject("leftInput");
            script.append("{ gas: '").append(leftInput.get("gas").getAsString()).append("'");
            script.append(", amount: ").append(leftInput.get("amount").getAsInt()).append(" }");
        }
        script.append(",\n        ");
        if (recipeJson.has("rightInput")) {
            JsonObject rightInput = recipeJson.getAsJsonObject("rightInput");
            script.append("{ gas: '").append(rightInput.get("gas").getAsString()).append("'");
            script.append(", amount: ").append(rightInput.get("amount").getAsInt()).append(" }");
        }
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismCrystallizingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        
        String chemicalType = recipeJson.has("chemicalType") ? 
            recipeJson.get("chemicalType").getAsString() : "gas";
        
        script.append("    event.recipes.mekanism.crystallizing(\n");
        script.append("        '").append(chemicalType).append("',\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        }
        script.append(",\n        ");
        
        if (recipeJson.has("input")) {
            JsonObject input = recipeJson.getAsJsonObject("input");
            script.append("{ ").append(chemicalType).append(": '");
            if (input.has(chemicalType)) {
                script.append(input.get(chemicalType).getAsString());
            } else if (input.has("gas")) {
                script.append(input.get("gas").getAsString());
            }
            script.append("', amount: ").append(input.get("amount").getAsInt()).append(" }");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismDissolutionToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    dissolution(\n");
        
        if (recipeJson.has("gasInput")) {
            JsonObject gasInput = recipeJson.getAsJsonObject("gasInput");
            script.append("        { gas: '").append(gasInput.get("gas").getAsString()).append("'");
            script.append(", amount: ").append(gasInput.get("amount").getAsInt()).append(" }");
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("itemInput")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            JsonObject output = recipeJson.getAsJsonObject("output");
            script.append("        { ");
            if (output.has("chemicalType")) {
                script.append("chemicalType: '").append(output.get("chemicalType").getAsString()).append("', ");
            }
            String chemicalType = output.has("chemicalType") ? output.get("chemicalType").getAsString() : "slurry";
            if (output.has(chemicalType)) {
                script.append(chemicalType).append(": '").append(output.get(chemicalType).getAsString()).append("', ");
            }
            script.append("amount: ").append(output.get("amount").getAsInt()).append(" }");
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismEnergyConversionToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.energy_conversion(\n");
        script.append("        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("input")));
        script.append(",\n        ");
        if (recipeJson.has("output")) {
            script.append(recipeJson.get("output").getAsLong());
        } else {
            script.append("1000");
        }
        script.append("\n    )");
        
        if (ModConfig.isAutoGenerateRecipeIdEnabled()) {
            String recipeId = generateRecipeId(recipeJson, "mekanism", "energy_conversion", "input");
            script.append(".id('").append(recipeId).append("')");
        }
        
        return script.toString();
    }
    
    private static String generateRecipeId(JsonObject recipeJson, String modId, String recipeType, String mainOutputKey) {
        String mainOutputId = "unknown";
        
        if (recipeJson.has(mainOutputKey)) {
            JsonObject mainOutput = recipeJson.getAsJsonObject(mainOutputKey);
            if (mainOutput.has("ingredient")) {
                mainOutput = mainOutput.getAsJsonObject("ingredient");
            }
            if (mainOutput.has("item")) {
                String fullItem = mainOutput.get("item").getAsString();
                String[] parts = fullItem.split(":");
                if (parts.length >= 2) {
                    mainOutputId = parts[1];
                } else {
                    mainOutputId = fullItem;
                }
            } else if (mainOutput.has("tag")) {
                String fullTag = mainOutput.get("tag").getAsString();
                String[] parts = fullTag.split(":");
                if (parts.length >= 2) {
                    mainOutputId = parts[1];
                } else {
                    mainOutputId = fullTag;
                }
            } else if (mainOutput.has("gas")) {
                String fullGas = mainOutput.get("gas").getAsString();
                String[] parts = fullGas.split(":");
                if (parts.length >= 2) {
                    mainOutputId = parts[1];
                } else {
                    mainOutputId = fullGas;
                }
            } else if (mainOutput.has("fluid")) {
                String fullFluid = mainOutput.get("fluid").getAsString();
                String[] parts = fullFluid.split(":");
                if (parts.length >= 2) {
                    mainOutputId = parts[1];
                } else {
                    mainOutputId = fullFluid;
                }
            }
        }
        
        return "recipehelper/" + modId + "/" + recipeType + "/" + mainOutputId;
    }
    
    private static String convertMekanismGasConversionToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    gasConversion(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismInfusionConversionToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    infusionConversion(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismWashingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    washing(\n");
        
        if (recipeJson.has("fluidInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("fluidInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("slurryInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("slurryInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismPaintingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    painting(\n");
        
        if (recipeJson.has("chemicalInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("chemicalInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("itemInput")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("itemInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatItemOutput(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismPigmentMixingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    pigmentMixing(\n");
        
        if (recipeJson.has("leftInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("leftInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("rightInput")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("rightInput")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismPigmentExtractingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    pigmentExtracting(\n");
        
        if (recipeJson.has("input")) {
            script.append("        ").append(formatIngredient(recipeJson.getAsJsonObject("input")));
        } else {
            script.append("        {}");
        }
        script.append(",\n");
        
        if (recipeJson.has("output")) {
            script.append("        ").append(formatChemicalObject(recipeJson.getAsJsonObject("output")));
        } else {
            script.append("        {}");
        }
        
        script.append("\n    )");
        return script.toString();
    }
}