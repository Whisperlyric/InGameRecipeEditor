package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.google.gson.JsonNull;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import dev.whisperlyric.ingamerecipeeditor.tags.CustomTagManager;
import dev.whisperlyric.ingamerecipeeditor.workspace.IngredientCycleManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.whisperlyric.ingamerecipeeditor.schema.RecipeSchema;
import dev.whisperlyric.ingamerecipeeditor.schema.SchemaRegistry;
import dev.whisperlyric.ingamerecipeeditor.schema.SlotDefinition;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配方编辑管理器 - 管理配方编辑状态和草稿
 */
public class RecipeEditManager {

    /**
     * 原料类型枚举
     */
    public enum IngredientKind {
        ITEM,
        FLUID,
        GAS,
        INFUSION,
        PIGMENT,
        SLURRY,
        RESOURCE; // 通用化学物质类型（Mekanism的MergedChemicalTank）

        public boolean isChemical() {
            return this == GAS || this == INFUSION || this == PIGMENT || this == SLURRY || this == RESOURCE;
        }
    }

    /**
     * 编辑值记录
     */
    public record IngredientEditValue(
        IngredientKind kind,
        String ingredientId,
        long amount
    ) {
        public boolean hasAmount() {
            return amount > 0;
        }
    }

    // 草稿存储：配方ID -> 槽位索引 -> 编辑值
    private static final Map<String, Map<Integer, IngredientEditValue>> drafts = new ConcurrentHashMap<>();
    
    // 原始配方存储：配方ID -> 原始槽位值
    private static final Map<String, Map<Integer, IngredientEditValue>> originals = new ConcurrentHashMap<>();

    /**
     * 开始编辑配方
     */
    public static void startEdit(String recipeId, IRecipeLayoutDrawable<?> recipeLayout) {
        if (recipeId == null || recipeId.isEmpty()) {
            return;
        }

        // 清除旧草稿
        drafts.remove(recipeId);
        originals.remove(recipeId);

        // 获取配方JSON
        JsonObject recipeJson = RecipeWorkspaceManager.getInstance().getDraft(recipeId)
            .map(RecipeWorkspaceManager.DraftInfo::originalJson)
            .orElse(null);

        // 存储原始值
        Map<Integer, IngredientEditValue> originalValues = new HashMap<>();
        List<IRecipeSlotView> slots = recipeLayout.getRecipeSlotsView().getSlotViews();
        Object recipe = recipeLayout.getRecipe();

        int index = 0;
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }

            // 先尝试从配方JSON读取槽位值（包括tag）
            IngredientEditValue originalValue = getSlotOriginalValueFromJson(recipeJson, recipeLayout, slot, index);
            if (originalValue == null) {
                // 如果JSON中没有，从槽位显示获取
                originalValue = getSlotOriginalValue(recipe, slot);
            }
            if (originalValue != null) {
                originalValues.put(index, originalValue);
            }
            index++;
        }

        originals.put(recipeId, originalValues);
    }

    /**
     * 从配方JSON读取槽位值（包括tag信息）
     */
    private static IngredientEditValue getSlotOriginalValueFromJson(JsonObject recipeJson, IRecipeLayoutDrawable<?> recipeLayout, IRecipeSlotDrawable slot, int slotIndex) {
        if (recipeJson == null) {
            return null;
        }

        try {
            // 获取配方类型
            String recipeType = recipeJson.has("type") ? recipeJson.get("type").getAsString() : null;
            if (recipeType == null) {
                return null;
            }

            // 获取配方schema
            RecipeSchema schema = SchemaRegistry.getSchema(recipeType).orElse(null);
            if (schema == null) {
                return null;
            }

            // 获取槽位定义
            SlotDefinition slotDef = null;
            int roleIndex = 0;

            // 计算roleIndex（在相同角色中的索引）
            List<IRecipeSlotView> slots = recipeLayout.getRecipeSlotsView().getSlotViews();
            int count = 0;
            for (IRecipeSlotView s : slots) {
                if (s instanceof IRecipeSlotDrawable sd) {
                    if (sd.getRole() == slot.getRole()) {
                        if (sd == slot) {
                            roleIndex = count;
                            break;
                        }
                        count++;
                    }
                }
            }

            // 从schema获取槽位定义
            if (slot.getRole() == RecipeIngredientRole.INPUT) {
                if (roleIndex < schema.getInputSlots().size()) {
                    slotDef = schema.getInputSlots().get(roleIndex);
                }
            } else {
                if (roleIndex < schema.getOutputSlots().size()) {
                    slotDef = schema.getOutputSlots().get(roleIndex);
                }
            }

            if (slotDef == null) {
                return null;
            }

            // 从JSON路径读取值
            String[] jsonPaths = slotDef.getJsonPaths();
            if (jsonPaths != null && jsonPaths.length > 0) {
                for (String path : jsonPaths) {
                    JsonElement value = getJsonAtPath(recipeJson, path);
                    if (value != null) {
                        return parseIngredientFromJson(value, slotDef);
                    }
                }
            } else if (slotDef.getJsonField() != null) {
                JsonElement value = getJsonAtPath(recipeJson, slotDef.getJsonField());
                if (value != null) {
                    return parseIngredientFromJson(value, slotDef);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    /**
     * 从JSON元素解析原料值（包括tag）
     */
    private static IngredientEditValue parseIngredientFromJson(JsonElement value, SlotDefinition slotDef) {
        if (value == null || !value.isJsonObject()) {
            return null;
        }

        JsonObject obj = value.getAsJsonObject();
        IngredientKind kind = IngredientKind.ITEM;

        // 确定原料类型
        switch (slotDef.getType()) {
            case FLUID -> kind = IngredientKind.FLUID;
            case GAS -> kind = IngredientKind.GAS;
            case INFUSE_TYPE -> kind = IngredientKind.INFUSION;
            case PIGMENT -> kind = IngredientKind.PIGMENT;
            case SLURRY -> kind = IngredientKind.SLURRY;
        }

        // 读取原料ID或tag
        String ingredientId = null;
        long amount = 1;

        // 检查tag格式（如 {"tag": "forge:ores/coal"}）
        if (obj.has("tag")) {
            ingredientId = "#" + obj.get("tag").getAsString();
        } else if (obj.has("item")) {
            ingredientId = obj.get("item").getAsString();
        } else if (obj.has("fluid")) {
            ingredientId = obj.get("fluid").getAsString();
            kind = IngredientKind.FLUID;
        } else if (obj.has("gas")) {
            ingredientId = obj.get("gas").getAsString();
            kind = IngredientKind.GAS;
        } else if (obj.has("infuse_type")) {
            ingredientId = obj.get("infuse_type").getAsString();
            kind = IngredientKind.INFUSION;
        } else if (obj.has("pigment")) {
            ingredientId = obj.get("pigment").getAsString();
            kind = IngredientKind.PIGMENT;
        } else if (obj.has("slurry")) {
            ingredientId = obj.get("slurry").getAsString();
            kind = IngredientKind.SLURRY;
        }

        // 读取数量
        if (obj.has("count")) {
            amount = obj.get("count").getAsLong();
        } else if (obj.has("amount")) {
            amount = obj.get("amount").getAsLong();
        }

        if (ingredientId != null) {
            return new IngredientEditValue(kind, ingredientId, amount);
        }

        return null;
    }

    /**
     * 从JSON路径获取值
     */
    private static JsonElement getJsonAtPath(JsonObject base, String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || base == null) {
            return null;
        }
        String path = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        String[] parts = path.split("/");
        if (parts.length == 1 && parts[0].contains(".")) {
            parts = parts[0].split("\\.");
        }

        JsonElement current = base;
        for (String part : parts) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            JsonObject obj = current.getAsJsonObject();
            // 处理数组索引（如"inputs[0]"）
            if (part.contains("[") && part.endsWith("]")) {
                String arrayName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.length() - 1));
                if (!obj.has(arrayName) || !obj.get(arrayName).isJsonArray()) {
                    return null;
                }
                JsonArray arr = obj.get(arrayName).getAsJsonArray();
                if (index < 0 || index >= arr.size()) {
                    return null;
                }
                current = arr.get(index);
            } else {
                if (!obj.has(part)) {
                    return null;
                }
                current = obj.get(part);
            }
        }
        return current;
    }

    /**
     * 获取槽位原始值
     */
    private static IngredientEditValue getSlotOriginalValue(Object recipe, IRecipeSlotDrawable slot) {
        try {
            var ingredients = slot.getAllIngredients().toList();
            
            // 先尝试从多个物品推断tag
            Optional<String> itemTag = getInputTextFromItemSlotTag(slot);
            if (itemTag.isPresent()) {
                // 获取数量（取第一个物品的数量）
                for (var ingredient : ingredients) {
                    if (ingredient != null) {
                        Object value = ingredient.getIngredient();
                        if (value instanceof ItemStack stack) {
                            return new IngredientEditValue(
                                IngredientKind.ITEM,
                                itemTag.get(),
                                stack.getCount()
                            );
                        }
                    }
                }
                // 如果没有物品数量，默认为1
                return new IngredientEditValue(IngredientKind.ITEM, itemTag.get(), 1);
            }
            
            // 尝试从多个流体推断tag
            Optional<String> fluidTag = getInputTextFromFluidSlotTag(slot);
            if (fluidTag.isPresent()) {
                for (var ingredient : ingredients) {
                    if (ingredient != null) {
                        Object value = ingredient.getIngredient();
                        if (value instanceof FluidStack stack) {
                            return new IngredientEditValue(
                                IngredientKind.FLUID,
                                fluidTag.get(),
                                stack.getAmount()
                            );
                        }
                    }
                }
                return new IngredientEditValue(IngredientKind.FLUID, fluidTag.get(), 1000);
            }
            
            // 尝试从多个化学品推断tag
            Optional<String> chemicalTag = getInputTextFromChemicalSlotTag(slot);
            if (chemicalTag.isPresent()) {
                for (var ingredient : ingredients) {
                    if (ingredient != null) {
                        Object value = ingredient.getIngredient();
                        if (isChemicalStack(value)) {
                            IngredientKind kind = getChemicalKind(value);
                            long amount = chemicalStackAmount(value);
                            return new IngredientEditValue(kind, chemicalTag.get(), amount);
                        }
                    }
                }
            }
            
            // 没有tag，返回单个物品/流体/化学品
            for (var ingredient : ingredients) {
                if (ingredient != null) {
                    Object value = ingredient.getIngredient();
                    if (value instanceof ItemStack stack) {
                        return new IngredientEditValue(
                            IngredientKind.ITEM,
                            stack.getItem().builtInRegistryHolder().key().location().toString(),
                            stack.getCount()
                        );
                    } else if (value instanceof FluidStack stack) {
                        return new IngredientEditValue(
                            IngredientKind.FLUID,
                            stack.getFluid().builtInRegistryHolder().key().location().toString(),
                            stack.getAmount()
                        );
                    } else if (isChemicalStack(value)) {
                        IngredientKind kind = getChemicalKind(value);
                        String chemicalId = getChemicalId(value);
                        long amount = chemicalStackAmount(value);
                        return new IngredientEditValue(kind, chemicalId, amount);
                    }
                }
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("获取槽位原始值失败", e);
        }
        return null;
    }

    /**
     * 从多个物品推断tag
     */
    private static Optional<String> getInputTextFromItemSlotTag(IRecipeSlotDrawable slot) {
        try {
            List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
            for (var ingredient : slot.getAllIngredients().toList()) {
                if (ingredient != null) {
                    Object value = ingredient.getIngredient();
                    if (value instanceof net.minecraft.world.item.ItemStack stack) {
                        stacks.add(stack);
                    }
                }
            }
            if (stacks.size() <= 1) {
                return Optional.empty();
            }

            List<net.minecraft.world.item.Item> items = stacks.stream()
                .map(net.minecraft.world.item.ItemStack::getItem)
                .toList();

            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getTags()
                .filter(tagEntry -> tagMatchesItems(tagEntry.getSecond(), items))
                .map(entry -> "#" + entry.getFirst().location())
                .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 从多个流体推断tag
     */
    private static Optional<String> getInputTextFromFluidSlotTag(IRecipeSlotDrawable slot) {
        try {
            List<net.minecraftforge.fluids.FluidStack> stacks = new ArrayList<>();
            for (var ingredient : slot.getAllIngredients().toList()) {
                if (ingredient != null) {
                    Object value = ingredient.getIngredient();
                    if (value instanceof net.minecraftforge.fluids.FluidStack stack) {
                        stacks.add(stack);
                    }
                }
            }
            if (stacks.size() <= 1) {
                return Optional.empty();
            }

            List<net.minecraft.world.level.material.Fluid> fluids = stacks.stream()
                .map(net.minecraftforge.fluids.FluidStack::getFluid)
                .toList();

            return net.minecraft.core.registries.BuiltInRegistries.FLUID.getTags()
                .filter(tagEntry -> tagMatchesFluids(tagEntry.getSecond(), fluids))
                .map(entry -> "#" + entry.getFirst().location())
                .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 从多个化学品推断tag
     */
    private static Optional<String> getInputTextFromChemicalSlotTag(IRecipeSlotDrawable slot) {
        try {
            List<Object> stacks = new ArrayList<>();
            for (var ingredient : slot.getAllIngredients().toList()) {
                if (ingredient != null) {
                    Object value = ingredient.getIngredient();
                    if (isChemicalStack(value)) {
                        stacks.add(value);
                    }
                }
            }
            if (stacks.size() <= 1) {
                return Optional.empty();
            }

            return chemicalTagKeyEquivalent(stacks);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 检查tag是否匹配物品列表（所有物品都在tag中）
     */
    private static boolean tagMatchesItems(net.minecraft.core.HolderSet.Named<net.minecraft.world.item.Item> tag, List<net.minecraft.world.item.Item> items) {
        // 检查所有物品是否都在tag中
        for (net.minecraft.world.item.Item item : items) {
            if (!tag.contains(item.builtInRegistryHolder())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查tag是否匹配流体列表（所有流体都在tag中）
     */
    private static boolean tagMatchesFluids(net.minecraft.core.HolderSet.Named<net.minecraft.world.level.material.Fluid> tag, List<net.minecraft.world.level.material.Fluid> fluids) {
        for (net.minecraft.world.level.material.Fluid fluid : fluids) {
            if (!tag.contains(fluid.builtInRegistryHolder())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从化学品列表推断tag
     */
    private static Optional<String> chemicalTagKeyEquivalent(List<Object> stacks) {
        Optional<Object> helper = getMekanismJeiChemicalHelper();
        if (helper.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object tagKey = helper.get().getClass()
                .getMethod("getTagKeyEquivalent", java.util.Collection.class)
                .invoke(helper.get(), stacks);
            if (tagKey instanceof Optional<?> optional && optional.isPresent()) {
                Object location = optional.get().getClass().getMethod("location").invoke(optional.get());
                return location == null ? Optional.empty() : Optional.of(location.toString());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return Optional.empty();
    }

    /**
     * 获取Mekanism JEI化学物质helper
     */
    private static Optional<Object> getMekanismJeiChemicalHelper() {
        try {
            return Optional.ofNullable(Class.forName("mekanism.client.recipe_viewer.jei.MekanismJEI")
                .getMethod("getChemicalHelper")
                .invoke(null));
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    /**
     * 检查是否允许清除槽位（用于界面左键清除行为的权限判断）
     * 如果配方ID为空则不允许；如果存在原始记录、草稿或工作区记录则允许清除。
     */
    public static boolean canClearSlots(String recipeId, Object recipe) {
        if (recipeId == null || recipeId.isEmpty()) return false;

        try {
            // 如果我们已经为该配方保存了原始值或草稿，允许清除
            if (originals.containsKey(recipeId)) return true;
            if (drafts.containsKey(recipeId)) return true;

            // 如果工作区管理器中存在对应的工作区配方记录，也允许清除
            var wsOpt = RecipeWorkspaceManager.getInstance().getWorkspaceRecipe(recipeId);
            if (wsOpt.isPresent()) return true;
        } catch (Exception ignored) {}

        // 保守默认允许：多数情况下界面应允许清除单个槽位（具体限制可在未来根据schema细化）
        return true;
    }

    /**
     * 检查是否有草稿
     */
    public static boolean hasDraft(String recipeId) {
        Map<Integer, IngredientEditValue> draft = drafts.get(recipeId);
        return draft != null && !draft.isEmpty();
    }

    /**
     * 清除草稿
     */
    public static void clear(String recipeId) {
        drafts.remove(recipeId);
        originals.remove(recipeId);
    }

    /**
     * 清除JEI的displayOverrides，恢复原始显示
     */
    public static void clearDisplayOverrides(IRecipeLayoutDrawable<?> recipeLayout) {
        List<IRecipeSlotView> slots = recipeLayout.getRecipeSlotsView().getSlotViews();
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }
            
            // 清除displayOverrides，恢复原始显示
            slot.clearDisplayOverrides();
            // 取消注册轮换管理
            try {
                IngredientCycleManager.unregisterSlot(slot);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 提交草稿（生成配方JSON）
     * 返回生成的JsonObject（如果生成失败返回Optional.empty）
     */
    public static Optional<JsonObject> submit(String recipeId) {
        Map<Integer, IngredientEditValue> draft = drafts.get(recipeId);
        if (draft == null || draft.isEmpty()) {
            return Optional.empty();
        }

        try {
            // 从工作区管理器获取草稿信息以找到配方类型和原始JSON
            var workspaceManager = RecipeWorkspaceManager.getInstance();
            var allDrafts = workspaceManager.getAllDrafts();
            var draftInfo = allDrafts.get(recipeId);
            if (draftInfo == null) {
                InGameRecipeEditor.LOGGER.warn("提交失败：找不到草稿信息，id={}", recipeId);
                return Optional.empty();
            }

            String recipeType = draftInfo.recipeType();
            RecipeSchema schema = SchemaRegistry.getSchema(recipeType).orElse(null);
            if (schema == null) {
                InGameRecipeEditor.LOGGER.warn("提交失败：未知的配方类型 {}", recipeType);
                return Optional.empty();
            }

            // 基于原始JSON（如果有）做深拷贝；否则新建
            JsonObject base = null;
            if (draftInfo.originalJson() != null) {
                base = draftInfo.originalJson().deepCopy();
            } else {
                base = new JsonObject();
                base.addProperty("type", recipeType);
            }

            int inputCount = schema.getInputSlotCount();
            int outputCount = schema.getOutputSlotCount();

            // 将草稿应用到JSON中
            for (Map.Entry<Integer, IngredientEditValue> e : draft.entrySet()) {
                int idx = e.getKey();
                IngredientEditValue v = e.getValue();

                SlotDefinition target = null;
                if (idx < inputCount) {
                    target = schema.getInputSlotByIndex(idx);
                } else {
                    int outIdx = idx - inputCount;
                    target = schema.getOutputSlotByIndex(outIdx);
                }
                if (target == null) continue;

                String path = target.getPrimaryJsonPath();
                if (path == null || path.isEmpty()) continue;

                // 生成值对象（简单格式，兼容item/fluid/chemical）
                JsonObject val = new JsonObject();
                switch (v.kind()) {
                    case ITEM -> {
                        val.addProperty("item", v.ingredientId());
                        if (v.hasAmount()) val.addProperty("count", v.amount());
                    }
                    case FLUID -> {
                        val.addProperty("fluid", v.ingredientId());
                        if (v.hasAmount()) val.addProperty("amount", v.amount());
                    }
                    default -> {
                        // 化学物质和其他资源
                        val.addProperty("resource", v.ingredientId());
                        if (v.hasAmount()) val.addProperty("amount", v.amount());
                    }
                }

                // 支持路径形式（如"a/b/c"或"/a/b/c"或"a.b.c"）
                setJsonAtPath(base, path, val);
            }

            // 移除草稿
            drafts.remove(recipeId);
            return Optional.of(base);
        } catch (Exception ex) {
            InGameRecipeEditor.LOGGER.error("生成配方JSON失败: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 将给定的JsonElement设置到基对象的路径上（支持简单的"/"或"."分隔路径）
     */
    public static void setJsonAtPath(JsonObject base, String rawPath, JsonElement value) {
        if (rawPath == null || rawPath.isEmpty() || base == null || value == null) return;
        String path = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        String[] parts = path.split("/");
        if (parts.length == 1 && parts[0].contains(".")) {
            parts = parts[0].split("\\.");
        }

        JsonElement current = base;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isLast = (i == parts.length - 1);
            boolean nextIsIndex = (i + 1 < parts.length) && parts[i + 1].matches("^\\d+$");

            if (part.matches("^\\d+$")) {
                int idx = Integer.parseInt(part);
                if (!current.isJsonArray()) {
                    // create array in place if possible
                    JsonArray newArr = new JsonArray();
                    current = newArr;
                }

                JsonArray arr = current.getAsJsonArray();
                while (arr.size() <= idx) arr.add(JsonNull.INSTANCE);

                if (isLast) {
                    arr.set(idx, value);
                    return;
                } else {
                    JsonElement nextElem = arr.get(idx);
                    if (nextElem == null || nextElem.isJsonNull()) {
                        JsonObject o = new JsonObject();
                        arr.set(idx, o);
                        current = o;
                    } else {
                        current = nextElem;
                    }
                    continue;
                }
            }

            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                if (isLast) {
                    obj.add(part, value);
                    return;
                }

                if (!obj.has(part) || obj.get(part).isJsonNull()) {
                    if (nextIsIndex) obj.add(part, new JsonArray()); else obj.add(part, new JsonObject());
                } else {
                    JsonElement exists = obj.get(part);
                    if (nextIsIndex && !exists.isJsonArray()) {
                        obj.add(part, new JsonArray());
                    } else if (!nextIsIndex && !exists.isJsonObject()) {
                        obj.add(part, new JsonObject());
                    }
                }

                current = obj.get(part);
                continue;
            } else if (current.isJsonArray()) {
                JsonArray arr = current.getAsJsonArray();
                if (arr.size() == 0 || arr.get(0).isJsonNull()) {
                    JsonObject o = new JsonObject();
                    if (arr.size() == 0) arr.add(o); else arr.set(0, o);
                }
                current = arr.get(0);
                i--;
                continue;
            } else {
                return;
            }
        }
    }

    /**
     * 替换槽位内容（物品）
     */
    public static void replaceSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot, ItemStack stack) {
        int index = getSlotIndex(slots, slot);
        if (index < 0) return;

        Map<Integer, IngredientEditValue> draft = drafts.computeIfAbsent(recipeId, k -> new HashMap<>());
        draft.put(index, new IngredientEditValue(
            IngredientKind.ITEM,
            stack.getItem().builtInRegistryHolder().key().location().toString(),
            stack.getCount()
        ));
    }

    /**
     * 替换槽位内容（流体）
     */
    public static void replaceSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot, FluidStack stack) {
        int index = getSlotIndex(slots, slot);
        if (index < 0) return;

        Map<Integer, IngredientEditValue> draft = drafts.computeIfAbsent(recipeId, k -> new HashMap<>());
        draft.put(index, new IngredientEditValue(
            IngredientKind.FLUID,
            stack.getFluid().builtInRegistryHolder().key().location().toString(),
            stack.getAmount()
        ));
    }

    /**
     * 替换槽位内容（化学物质）
     */
    public static void replaceResourceSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot, Object chemical) {
        int index = getSlotIndex(slots, slot);
        if (index < 0) return;

        IngredientKind kind = getChemicalKind(chemical);
        String chemicalId = getChemicalId(chemical);
        long amount = chemicalStackAmount(chemical);

        Map<Integer, IngredientEditValue> draft = drafts.computeIfAbsent(recipeId, k -> new HashMap<>());
        draft.put(index, new IngredientEditValue(kind, chemicalId, amount));
    }

    /**
     * 清除槽位内容
     */
    public static void clearSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot) {
        int index = getSlotIndex(slots, slot);
        if (index < 0) return;

        Map<Integer, IngredientEditValue> draft = drafts.get(recipeId);
        if (draft != null) {
            draft.remove(index);
        }
    }

    /**
     * 设置槽位编辑值（从文本编辑界面）
     */
    public static void setSlotEditValue(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot, IngredientEditValue value) {
        int index = getSlotIndex(slots, slot);
        if (index < 0) return;

        Map<Integer, IngredientEditValue> draft = drafts.computeIfAbsent(recipeId, k -> new HashMap<>());
        draft.put(index, value);
    }

    /**
     * 获取槽位编辑值
     */
    public static Optional<IngredientEditValue> getSlotEditValue(String recipeId, Object recipe, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot) {
        int index = getSlotIndex(slots, slot);
        if (index < 0) return Optional.empty();

        // 先检查草稿
        Map<Integer, IngredientEditValue> draft = drafts.get(recipeId);
        if (draft != null && draft.containsKey(index)) {
            return Optional.of(draft.get(index));
        }

        // 返回原始值
        Map<Integer, IngredientEditValue> original = originals.get(recipeId);
        if (original != null && original.containsKey(index)) {
            return Optional.of(original.get(index));
        }

        return Optional.empty();
    }

    /**
     * 获取槽位原料类型
     */
    public static IngredientKind getSlotIngredientKind(String recipeId, Object recipe, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot) {
        Optional<IngredientEditValue> value = getSlotEditValue(recipeId, recipe, slots, slot);
        if (value.isPresent()) {
            return value.get().kind();
        }

        // 默认返回ITEM
        return IngredientKind.ITEM;
    }

    /**
     * 应用草稿到布局（用于渲染）
     * 使用JEI的displayOverrides机制动态更新槽位显示内容
     */
    public static void applyDraftToLayout(IRecipeLayoutDrawable<?> recipeLayout) {
        String recipeId = getRecipeIdFromLayout(recipeLayout);
        if (recipeId == null || recipeId.isEmpty()) {
            return;
        }

        Map<Integer, IngredientEditValue> draft = drafts.get(recipeId);
        // We will apply explicit drafts if present; otherwise for native recipes
        // try to detect multi-candidate original slot ingredients and register
        // them for cycling (so JEI-like rotation appears for native recipes).

        List<IRecipeSlotView> slots = recipeLayout.getRecipeSlotsView().getSlotViews();
        int index = 0;
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }

            IngredientEditValue editValue = (draft != null) ? draft.get(index) : null;
            if (editValue != null) {
                applyEditValueToSlot(slot, editValue);
            } else {
                // No draft: inspect original slot's ingredients. If there are
                // multiple distinct ingredients, register them for cycling so
                // native recipes show rotating contents as JEI does.
                try {
                    var ingredients = slot.getAllIngredients().toList();
                    if (ingredients.size() > 1) {
                        java.util.List<mezz.jei.api.ingredients.IIngredientType<?>> types = new java.util.ArrayList<>();
                        java.util.List<Object> values = new java.util.ArrayList<>();
                        for (var ingr : ingredients) {
                            try {
                                Object inner = ingr.getIngredient();
                                if (inner instanceof ItemStack is) {
                                    types.add(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                                    values.add(is.copy());
                                } else if (inner instanceof FluidStack fs) {
                                    types.add(mezz.jei.api.forge.ForgeTypes.FLUID_STACK);
                                    values.add(fs.copy());
                                    } else {
                                        // try to add raw ingredient as fallback
                                        // ITypedIngredient API provides getType() to obtain the ingredient type
                                        types.add(ingr.getType());
                                        values.add(inner);
                                    }
                            } catch (Exception ignored) {}
                        }

                        if (values.size() > 1) {
                            try {
                                IngredientCycleManager.registerSlotCandidates(slot, types, values);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            index++;
        }
    }

    /**
     * 从布局获取配方ID
     */
    private static String getRecipeIdFromLayout(IRecipeLayoutDrawable<?> recipeLayout) {
        try {
            Object recipe = recipeLayout.getRecipe();
            if (recipe instanceof Recipe<?> mcRecipe) {
                // 尝试获取配方ID
                var category = recipeLayout.getRecipeCategory();
                // 使用反射调用getRegistryName方法，避免泛型类型问题
                try {
                    var getRegistryNameMethod = category.getClass().getMethod("getRegistryName", Object.class);
                    Object id = getRegistryNameMethod.invoke(category, recipe);
                    if (id instanceof ResourceLocation rl) {
                        return rl.toString();
                    }
                } catch (NoSuchMethodException e) {
                    InGameRecipeEditor.LOGGER.warn("找不到getRegistryName方法", e);
                }
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("获取配方ID失败", e);
        }
        return null;
    }

    /**
     * 应用编辑值到槽位
     */
    private static void applyEditValueToSlot(IRecipeSlotDrawable slot, IngredientEditValue editValue) {
        try {
            // 清除原有显示
            slot.clearDisplayOverrides();
            
            // 创建新显示覆盖
            var displayOverrides = slot.createDisplayOverrides();

            switch (editValue.kind()) {
                case ITEM -> {
                    // 支持单值 / 多值 / 标签 (#tag) 语法，如 "minecraft:iron_ingot", "{minecraft:iron_ingot,minecraft:gold_ingot}", "#forge:ingots"
                    String raw = editValue.ingredientId();
                    // normalize braces and whitespace
                    raw = raw.replaceAll("[{}\\s]", "");
                    String[] parts = raw.split(",");
                    
                    List<IIngredientType<?>> types = new ArrayList<>();
                    List<Object> values = new ArrayList<>();
                    
                    for (String token : parts) {
                        if (token == null || token.isEmpty()) continue;
                        try {
                            if (token.startsWith("#")) {
                                String tagIdStr = token.substring(1);
                                ResourceLocation tagRl = ResourceLocation.tryParse(tagIdStr);
                                if (tagRl != null) {
                                    // custom tag first
                                    if (CustomTagManager.hasTag(tagRl)) {
                                        var vals = CustomTagManager.readTagItems(tagRl);
                                        for (String idStr : vals) {
                                            ResourceLocation id = ResourceLocation.tryParse(idStr);
                                            if (id != null) {
                                                var it = ForgeRegistries.ITEMS.getValue(id);
                                                if (it != null) {
                                                    ItemStack s = new ItemStack(it, (int) Math.max(1, editValue.amount()));
                                                    types.add(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                                                    values.add(s);
                                                }
                                            }
                                        }
                                    } else {
                                        // scan registered items for this tag
                                        ForgeRegistries.ITEMS.getValues().forEach(item -> {
                                            try {
                                                item.builtInRegistryHolder().tags().forEach(tagKey -> {
                                                    if (tagKey.location().equals(tagRl)) {
                                                        ItemStack s = new ItemStack(item, (int) Math.max(1, editValue.amount()));
                                                        types.add(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                                                        values.add(s);
                                                    }
                                                });
                                            } catch (Exception ignored) {}
                                        });
                                    }
                                }
                            } else {
                                ResourceLocation itemId = ResourceLocation.tryParse(token);
                                if (itemId != null) {
                                    var item = ForgeRegistries.ITEMS.getValue(itemId);
                                    if (item != null) {
                                        ItemStack stack = new ItemStack(item, (int) Math.max(1, editValue.amount()));
                                        types.add(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
                                        values.add(stack);
                                        continue;
                                    }
                                }
                                // fallback: maybe a fluid id
                                ResourceLocation fluidId = ResourceLocation.tryParse(token);
                                if (fluidId != null) {
                                    var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                                    if (fluid != null) {
                                        FluidStack fstack = new FluidStack(fluid, (int) Math.max(1, editValue.amount()));
                                        types.add(mezz.jei.api.forge.ForgeTypes.FLUID_STACK);
                                        values.add(fstack);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    
                    if (values.isEmpty()) return;
                    if (values.size() == 1) {
                        // 单个候选，直接显示
                        try {
                            displayOverrides.addIngredient((IIngredientType<Object>) types.get(0), values.get(0));
                        } catch (Exception ignored) {}
                    } else {
                        // 多个候选，交给轮换管理器负责周期切换
                        try {
                            IngredientCycleManager.registerSlotCandidates(slot, types, values);
                        } catch (Exception e) {
                            // 退化为一次性添加全部（虽然重量较大），以保证至少能显示
                            for (int i = 0; i < values.size(); i++) {
                                try {
                                    displayOverrides.addIngredient((IIngredientType<Object>) types.get(i), values.get(i));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
                case FLUID -> {
                    String raw = editValue.ingredientId();
                    raw = raw.replaceAll("[{}\\s]", "");
                    String[] parts = raw.split(",");

                    List<IIngredientType<?>> types = new ArrayList<>();
                    List<Object> values = new ArrayList<>();

                    for (String token : parts) {
                        if (token == null || token.isEmpty()) continue;
                        try {
                            if (token.startsWith("#")) {
                                String tagIdStr = token.substring(1);
                                ResourceLocation tagRl = ResourceLocation.tryParse(tagIdStr);
                                if (tagRl != null) {
                                    if (CustomTagManager.hasTag(tagRl)) {
                                        var vals = CustomTagManager.readTagFluids(tagRl);
                                        for (String idStr : vals) {
                                            ResourceLocation id = ResourceLocation.tryParse(idStr);
                                            if (id != null) {
                                                var f = ForgeRegistries.FLUIDS.getValue(id);
                                                if (f != null) {
                                                    FluidStack s = new FluidStack(f, (int) Math.max(1, editValue.amount()));
                                                    types.add(mezz.jei.api.forge.ForgeTypes.FLUID_STACK);
                                                    values.add(s);
                                                }
                                            }
                                        }
                                    } else {
                                        ForgeRegistries.FLUIDS.getValues().forEach(f -> {
                                            try {
                                                f.builtInRegistryHolder().tags().forEach(tagKey -> {
                                                    if (tagKey.location().equals(tagRl)) {
                                                        FluidStack s = new FluidStack(f, (int) Math.max(1, editValue.amount()));
                                                        types.add(mezz.jei.api.forge.ForgeTypes.FLUID_STACK);
                                                        values.add(s);
                                                    }
                                                });
                                            } catch (Exception ignored) {}
                                        });
                                    }
                                }
                            } else {
                                ResourceLocation fluidId = ResourceLocation.tryParse(token);
                                if (fluidId != null) {
                                    var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                                    if (fluid != null) {
                                        FluidStack stack = new FluidStack(fluid, (int) Math.max(1, editValue.amount()));
                                        types.add(mezz.jei.api.forge.ForgeTypes.FLUID_STACK);
                                        values.add(stack);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    if (values.isEmpty()) return;
                    if (values.size() == 1) {
                        try {
                            displayOverrides.addIngredient((IIngredientType<Object>) types.get(0), values.get(0));
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            IngredientCycleManager.registerSlotCandidates(slot, types, values);
                        } catch (Exception e) {
                            for (int i = 0; i < values.size(); i++) {
                                try {
                                    displayOverrides.addIngredient((IIngredientType<Object>) types.get(i), values.get(i));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
                default -> {
                    // 化学物质
                    if (editValue.kind().isChemical()) {
                        applyChemicalToSlot(slot, editValue);
                    }
                }
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("应用编辑值到槽位失败", e);
        }
    }

    /**
     * 应用化学物质到槽位
     */
    private static void applyChemicalToSlot(IRecipeSlotDrawable slot, IngredientEditValue editValue) {
        try {
            ResourceLocation chemicalId = ResourceLocation.tryParse(editValue.ingredientId());
            if (chemicalId == null) {
                return;
            }

            // 根据化学品类型选择正确的registry
            String registryName = switch (editValue.kind()) {
                case GAS -> "gasRegistry";
                case INFUSION -> "infuseTypeRegistry";
                case PIGMENT -> "pigmentRegistry";
                case SLURRY -> "slurryRegistry";
                default -> "gasRegistry"; // RESOURCE类型默认尝试gasRegistry
            };

            Class<?> mekanismApi = Class.forName("mekanism.api.MekanismAPI");
            Object registry = mekanismApi.getField(registryName).get(null);
            Object chemical = registry.getClass().getMethod("getValue", ResourceLocation.class).invoke(registry, chemicalId);
            
            if (chemical == null) {
                return;
            }

            // 检查是否为空类型
            Method isEmptyTypeMethod = chemical.getClass().getMethod("isEmptyType");
            Boolean isEmpty = (Boolean) isEmptyTypeMethod.invoke(chemical);
            if (isEmpty) {
                return;
            }

            Class<?> chemicalClass = chemical.getClass();
            Class<?> chemicalStackClass = Class.forName("mekanism.api.chemical.ChemicalStack");
            Object chemicalStack = chemicalStackClass
                .getConstructor(chemicalClass.getSuperclass(), long.class)
                .newInstance(chemical, Math.max(1, editValue.amount()));

            // 获取Mekanism JEI的化学物质类型
            Class<?> mekanismJei = Class.forName("mekanism.client.jei.MekanismJEI");
            Object typeChemical = mekanismJei.getField("TYPE_CHEMICAL").get(null);
            
            if (typeChemical instanceof mezz.jei.api.ingredients.IIngredientType<?> ingredientType) {
                slot.clearDisplayOverrides();
                // 使用反射调用addIngredient方法，避免泛型类型问题
                var displayOverrides = slot.createDisplayOverrides();
                try {
                    var addIngredientMethod = displayOverrides.getClass().getMethod("addIngredient", 
                        mezz.jei.api.ingredients.IIngredientType.class, Object.class);
                    addIngredientMethod.invoke(displayOverrides, ingredientType, chemicalStack);
                } catch (NoSuchMethodException e) {
                    InGameRecipeEditor.LOGGER.warn("找不到addIngredient方法", e);
                }
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("应用化学物质到槽位失败", e);
        }
    }

    /**
     * 获取槽位索引
     */
    private static int getSlotIndex(List<IRecipeSlotView> slots, IRecipeSlotDrawable slot) {
        int index = 0;
        for (IRecipeSlotView slotView : slots) {
            if (slotView == slot) {
                return index;
            }
            if (slotView instanceof IRecipeSlotDrawable) {
                IRecipeSlotDrawable drawable = (IRecipeSlotDrawable) slotView;
                if (drawable.getRole() == RecipeIngredientRole.INPUT || 
                    drawable.getRole() == RecipeIngredientRole.OUTPUT) {
                    index++;
                }
            }
        }
        return -1;
    }

    // ========== 化学物质处理 ==========

    /**
     * 检查是否为化学物质堆
     */
    public static boolean isChemicalStack(Object value) {
        if (value == null) return false;
        String className = value.getClass().getName();
        return className.contains("mekanism.api.chemical") || 
               className.contains("GasStack") ||
               className.contains("InfusionStack") ||
               className.contains("PigmentStack") ||
               className.contains("SlurryStack") ||
               className.contains("ChemicalStack");
    }

    /**
     * 获取化学物质类型
     */
    public static IngredientKind getChemicalKind(Object chemical) {
        String className = chemical.getClass().getSimpleName();
        if (className.contains("Gas")) return IngredientKind.GAS;
        if (className.contains("Infusion")) return IngredientKind.INFUSION;
        if (className.contains("Pigment")) return IngredientKind.PIGMENT;
        if (className.contains("Slurry")) return IngredientKind.SLURRY;
        return IngredientKind.RESOURCE;
    }

    /**
     * 获取化学物质ID
     */
    public static String getChemicalId(Object chemical) {
        try {
            // 尝试通过反射获取化学物质的getType方法
            var typeMethod = chemical.getClass().getMethod("getType");
            Object type = typeMethod.invoke(chemical);
            if (type != null) {
                // 尙试获取registryName
                var registryNameMethod = type.getClass().getMethod("getRegistryName");
                Object registryName = registryNameMethod.invoke(type);
                if (registryName instanceof ResourceLocation rl) {
                    return rl.toString();
                }
                // 尝试toString
                return type.toString();
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("获取化学物质ID失败", e);
        }
        return "";
    }

    /**
     * 获取化学物质数量
     */
    public static long chemicalStackAmount(Object chemical) {
        try {
            var amountMethod = chemical.getClass().getMethod("getAmount");
            Object amount = amountMethod.invoke(chemical);
            if (amount instanceof Long l) {
                return l;
            } else if (amount instanceof Integer i) {
                return i.longValue();
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("获取化学物质数量失败", e);
        }
        return 0;
    }
}