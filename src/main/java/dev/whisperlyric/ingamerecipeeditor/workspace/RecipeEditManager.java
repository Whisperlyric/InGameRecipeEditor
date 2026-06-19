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
            
            // 如果JSON中没有，不使用默认值，让用户知道解析失败
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
        if (value == null) {
            return null;
        }

        IngredientKind kind = IngredientKind.ITEM;

        // 确定原料类型
        switch (slotDef.getType()) {
            case FLUID -> kind = IngredientKind.FLUID;
            case GAS -> kind = IngredientKind.GAS;
            case INFUSE_TYPE -> kind = IngredientKind.INFUSION;
            case PIGMENT -> kind = IngredientKind.PIGMENT;
            case SLURRY -> kind = IngredientKind.SLURRY;
        }

        // 处理对象形式
        if (value.isJsonObject()) {
            JsonObject obj = value.getAsJsonObject();
            
            // 检查是否有嵌套的ingredient字段（如 {"ingredient":[{"tag":"..."}]}）
            if (obj.has("ingredient")) {
                JsonElement ingredientElem = obj.get("ingredient");
                return parseIngredientArrayOrSingle(ingredientElem, kind);
            }
            
            // 检查是否有嵌套的ingredients字段
            if (obj.has("ingredients")) {
                JsonElement ingredientsElem = obj.get("ingredients");
                return parseIngredientArrayOrSingle(ingredientsElem, kind);
            }
            
            // 直接解析单个对象
            String ingredientId = parseSingleIngredientId(obj, kind);
            long amount = 1;
            
            if (obj.has("count")) {
                amount = obj.get("count").getAsLong();
            } else if (obj.has("amount")) {
                amount = obj.get("amount").getAsLong();
            }
            
            if (ingredientId != null) {
                return new IngredientEditValue(kind, ingredientId, amount);
            }
        }
        
        // 处理数组形式（直接是数组）
        if (value.isJsonArray()) {
            return parseIngredientArrayOrSingle(value, kind);
        }

        return null;
    }

    /**
     * 解析ingredient数组或单个元素
     */
    private static IngredientEditValue parseIngredientArrayOrSingle(JsonElement elem, IngredientKind kind) {
        if (elem == null) {
            return null;
        }
        
        // 处理数组形式
        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            StringBuilder ingredientIds = new StringBuilder();
            long amount = 1;

            for (int i = 0; i < arr.size(); i++) {
                JsonElement item = arr.get(i);
                if (item.isJsonObject()) {
                    JsonObject obj = item.getAsJsonObject();
                    String singleId = parseSingleIngredientId(obj, kind);
                    if (singleId != null) {
                        if (ingredientIds.length() > 0) {
                            ingredientIds.append(",");
                        }
                        ingredientIds.append(singleId);
                    }
                    // 从第一个元素读取数量
                    if (i == 0) {
                        if (obj.has("count")) {
                            amount = obj.get("count").getAsLong();
                        } else if (obj.has("amount")) {
                            amount = obj.get("amount").getAsLong();
                        }
                    }
                }
            }

            if (ingredientIds.length() > 0) {
                return new IngredientEditValue(kind, ingredientIds.toString(), amount);
            }
            return null;
        }
        
        // 处理单个对象形式
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            String ingredientId = parseSingleIngredientId(obj, kind);
            long amount = 1;
            
            if (obj.has("count")) {
                amount = obj.get("count").getAsLong();
            } else if (obj.has("amount")) {
                amount = obj.get("amount").getAsLong();
            }
            
            if (ingredientId != null) {
                return new IngredientEditValue(kind, ingredientId, amount);
            }
        }
        
        return null;
    }

    /**
     * 解析单个原料ID（包括tag）
     */
    private static String parseSingleIngredientId(JsonObject obj, IngredientKind kind) {
        // 检查tag格式（如 {"tag": "forge:ores/coal"}）
        if (obj.has("tag")) {
            return "#" + obj.get("tag").getAsString();
        } else if (obj.has("item")) {
            return obj.get("item").getAsString();
        } else if (obj.has("fluid")) {
            kind = IngredientKind.FLUID;
            return obj.get("fluid").getAsString();
        } else if (obj.has("gas")) {
            kind = IngredientKind.GAS;
            return obj.get("gas").getAsString();
        } else if (obj.has("infuse_type")) {
            kind = IngredientKind.INFUSION;
            return obj.get("infuse_type").getAsString();
        } else if (obj.has("pigment")) {
            kind = IngredientKind.PIGMENT;
            return obj.get("pigment").getAsString();
        } else if (obj.has("slurry")) {
            kind = IngredientKind.SLURRY;
            return obj.get("slurry").getAsString();
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
            
            // 清除displayOverrides
            slot.clearDisplayOverrides();
            // 取消注册轮换管理
            try {
                IngredientCycleManager.unregisterSlot(slot);
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * 清除所有槽位显示（用于新建空工作区）
     * 标记所有槽位为清除状态，阻止applyDraftToLayout重新注册轮换
     * 并设置空的displayOverrides使槽位显示为空
     */
    public static void clearAllSlotDisplays(IRecipeLayoutDrawable<?> recipeLayout) {
        List<IRecipeSlotView> slots = recipeLayout.getRecipeSlotsView().getSlotViews();
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT && 
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }
            
            // 标记槽位为已清除，阻止applyDraftToLayout重新注册轮换
            // markSlotCleared会同时从slotCandidates中移除，不需要再调用unregisterSlot
            IngredientCycleManager.markSlotCleared(slot);
            
            // 清除displayOverrides
            slot.clearDisplayOverrides();
            
            // 设置空的displayOverrides，使槽位显示为空
            // 尝试根据槽位原有内容类型设置空值
            try {
                var ingredients = slot.getAllIngredients().toList();
                if (!ingredients.isEmpty()) {
                    var first = ingredients.get(0);
                    Object inner = first.getIngredient();
                    if (inner instanceof ItemStack) {
                        slot.createDisplayOverrides().addIngredient(
                            mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                            net.minecraft.world.item.ItemStack.EMPTY
                        );
                    } else if (inner instanceof FluidStack) {
                        slot.createDisplayOverrides().addIngredient(
                            mezz.jei.api.forge.ForgeTypes.FLUID_STACK,
                            net.minecraftforge.fluids.FluidStack.EMPTY
                        );
                    } else if (isChemicalStack(inner)) {
                        // 化学品类型：尝试创建空的化学品堆
                        try {
                            Object emptyChemical = createEmptyChemicalStack(inner);
                            if (emptyChemical != null) {
                                slot.createDisplayOverrides().addIngredientsUnsafe(java.util.Collections.singletonList(emptyChemical));
                            }
                        } catch (Exception ignored2) {}
                    } else {
                        // 其他类型，不设置任何内容（保持空）
                    }
                }
                // 如果没有原有内容或无法设置空值，保持displayOverrides为空
            } catch (Exception e) {
                // fallback: 保持displayOverrides为空
            }
        }
    }
    
    /**
     * 创建空的化学品堆
     */
    private static Object createEmptyChemicalStack(Object chemicalStack) {
        try {
            // 获取化学品类型
            var getTypeMethod = chemicalStack.getClass().getMethod("getType");
            Object chemicalType = getTypeMethod.invoke(chemicalStack);
            if (chemicalType == null) return null;
            
            // 尝试创建空的化学品堆
            // Mekanism的化学品堆通常有静态方法EMPTY或构造函数接受类型和数量
            var stackClass = chemicalStack.getClass();
            
            // 尝试获取EMPTY字段
            try {
                var emptyField = stackClass.getField("EMPTY");
                return emptyField.get(null);
            } catch (NoSuchFieldException ignored) {}
            
            // 尝试通过构造函数创建空堆
            try {
                var constructor = stackClass.getConstructor(chemicalType.getClass(), long.class);
                return constructor.newInstance(chemicalType, 0L);
            } catch (NoSuchMethodException ignored) {}
            
            // 尝试通过copy方法创建并设置数量为0
            try {
                var copyMethod = stackClass.getMethod("copy");
                Object copy = copyMethod.invoke(chemicalStack);
                var setAmountMethod = copy.getClass().getMethod("setAmount", long.class);
                setAmountMethod.invoke(copy, 0L);
                return copy;
            } catch (Exception ignored) {}
            
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("创建空化学品堆失败", e);
        }
        return null;
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

            // 基于原始JSON（如果有）做深拷贝；否则新建
            JsonObject base;
            if (draftInfo.originalJson() != null) {
                base = draftInfo.originalJson().deepCopy();
            } else {
                base = new JsonObject();
                base.addProperty("type", recipeType);
            }

            if (schema != null) {
                // 有schema：使用schema定义的路径映射
                applyDraftWithSchema(base, draft, schema);
            } else {
                // 无schema：使用通用逻辑基于JSON结构推断路径
                applyDraftGeneric(base, draft, recipeType);
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
     * 使用schema定义的路径映射应用草稿
     */
    private static void applyDraftWithSchema(JsonObject base, Map<Integer, IngredientEditValue> draft, RecipeSchema schema) {
        int inputCount = schema.getInputSlotCount();
        int outputCount = schema.getOutputSlotCount();

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

            JsonObject val = buildIngredientJson(v);
            setJsonAtPath(base, path, val);
        }
    }

    /**
     * 无schema时使用通用逻辑应用草稿修改
     * 基于JSON结构推断槽位路径（与JEIRecipeManager参考项目一致）
     */
    private static void applyDraftGeneric(JsonObject base, Map<Integer, IngredientEditValue> draft, String recipeType) {
        // 分离输入和输出草稿
        // 需要根据槽位角色（INPUT/OUTPUT）区分，但draft只存了全局索引
        // 这里通过原始配方布局推断角色
        // 简化处理：基于JSON结构推断

        boolean isShaped = base.has("pattern") && base.has("key");
        boolean isShapeless = base.has("ingredients") && base.get("ingredients").isJsonArray();
        boolean hasIngredient = base.has("ingredient");
        boolean hasResult = base.has("result");
        boolean hasResults = base.has("results") && base.get("results").isJsonArray();

        // 获取输入槽位数和输出槽位数
        int inputCount = countInputSlots(base, isShaped, isShapeless, hasIngredient);
        int outputCount = countOutputSlots(base, hasResult, hasResults);

        for (Map.Entry<Integer, IngredientEditValue> e : draft.entrySet()) {
            int idx = e.getKey();
            IngredientEditValue v = e.getValue();

            if (idx < inputCount) {
                // 输入槽位
                applyGenericInput(base, v, idx, isShaped, isShapeless, hasIngredient);
            } else {
                // 输出槽位
                int outIdx = idx - inputCount;
                applyGenericOutput(base, v, outIdx, hasResult, hasResults);
            }
        }
    }

    /**
     * 计算输入槽位数量
     */
    private static int countInputSlots(JsonObject base, boolean isShaped, boolean isShapeless, boolean hasIngredient) {
        if (isShaped) {
            JsonArray pattern = base.getAsJsonArray("pattern");
            int height = pattern.size();
            int width = height > 0 ? pattern.get(0).getAsString().length() : 0;
            // 合成台固定3x3=9个输入槽位
            return 9;
        }
        if (isShapeless) {
            return base.getAsJsonArray("ingredients").size();
        }
        if (hasIngredient) {
            return 1;
        }
        // 尝试ingredients数组
        if (base.has("ingredients") && base.get("ingredients").isJsonArray()) {
            return base.getAsJsonArray("ingredients").size();
        }
        return 0;
    }

    /**
     * 计算输出槽位数量
     */
    private static int countOutputSlots(JsonObject base, boolean hasResult, boolean hasResults) {
        if (hasResults) {
            return base.getAsJsonArray("results").size();
        }
        if (hasResult) {
            return 1;
        }
        return 0;
    }

    /**
     * 通用输入槽位修改
     */
    private static void applyGenericInput(JsonObject base, IngredientEditValue v, int slotIndex, boolean isShaped, boolean isShapeless, boolean hasIngredient) {
        JsonObject val = buildIngredientJson(v);

        if (isShaped) {
            // 有序合成：修改pattern+key
            applyShapedInput(base, v, slotIndex);
            return;
        }

        if (isShapeless) {
            // 无序合成：修改ingredients数组
            JsonArray ingredients = base.getAsJsonArray("ingredients");
            if (slotIndex >= 0 && slotIndex < ingredients.size()) {
                ingredients.set(slotIndex, val);
            }
            return;
        }

        if (hasIngredient && slotIndex == 0) {
            // 单输入配方（如熔炼）
            base.add("ingredient", val);
            return;
        }

        // 通用：尝试ingredients数组
        if (base.has("ingredients") && base.get("ingredients").isJsonArray()) {
            JsonArray ingredients = base.getAsJsonArray("ingredients");
            if (slotIndex >= 0 && slotIndex < ingredients.size()) {
                ingredients.set(slotIndex, val);
            }
        }
    }

    /**
     * 有序合成输入修改（修改pattern+key）
     */
    private static void applyShapedInput(JsonObject base, IngredientEditValue v, int slotIndex) {
        if (!base.has("pattern") || !base.has("key")) return;

        JsonArray pattern = base.getAsJsonArray("pattern");
        JsonObject key = base.getAsJsonObject("key");
        int height = pattern.size();
        int width = height > 0 ? pattern.get(0).getAsString().length() : 0;

        // 合成台固定3x3网格，计算行列
        int gridWidth = 3;
        int rowOffset = Math.max(0, (3 - height) / 2);
        int colOffset = Math.max(0, (3 - width) / 2);
        int row = slotIndex / gridWidth - rowOffset;
        int col = slotIndex % gridWidth - colOffset;

        if (row < 0 || row >= height || col < 0 || col >= width) return;

        String rowText = pattern.get(row).getAsString();
        if (col >= rowText.length()) return;

        char keyChar = rowText.charAt(col);
        if (keyChar == ' ') return;

        // 更新key中对应字符的原料
        JsonObject val = buildIngredientJson(v);
        key.add(String.valueOf(keyChar), val);
    }

    /**
     * 通用输出槽位修改
     */
    private static void applyGenericOutput(JsonObject base, IngredientEditValue v, int outIdx, boolean hasResult, boolean hasResults) {
        if (v.kind() != IngredientKind.ITEM) return; // 输出槽位只支持物品

        JsonObject val = new JsonObject();
        val.addProperty("id", v.ingredientId());
        if (v.hasAmount()) val.addProperty("count", v.amount());

        if (hasResult && outIdx == 0) {
            base.add("result", val);
            return;
        }

        if (hasResults) {
            JsonArray results = base.getAsJsonArray("results");
            if (outIdx >= 0 && outIdx < results.size()) {
                results.set(outIdx, val);
            }
        }
    }

    /**
     * 构建原料JSON对象
     */
    private static JsonObject buildIngredientJson(IngredientEditValue v) {
        JsonObject val = new JsonObject();
        switch (v.kind()) {
            case ITEM -> {
                if (v.ingredientId().startsWith("#")) {
                    val.addProperty("tag", v.ingredientId().substring(1));
                } else {
                    val.addProperty("item", v.ingredientId());
                }
                if (v.hasAmount() && v.amount() > 1) val.addProperty("count", v.amount());
            }
            case FLUID -> {
                if (v.ingredientId().startsWith("#")) {
                    val.addProperty("type", "neoforge:tag");
                    val.addProperty("tag", v.ingredientId().substring(1));
                } else {
                    val.addProperty("type", "neoforge:single");
                    val.addProperty("fluid", v.ingredientId());
                }
                if (v.hasAmount()) val.addProperty("amount", v.amount());
            }
            default -> {
                // 化学物质和其他资源
                val.addProperty("id", v.ingredientId());
                if (v.hasAmount()) val.addProperty("amount", v.amount());
            }
        }
        return val;
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
        clearSlot(recipeId, slots, slot, true);
    }
    
    /**
     * 清除槽位内容（右键清除，两种模式都清空槽位）
     * @param isEditMode 是否为编辑模式（现在两种模式行为一致：都清空槽位）
     */
    public static void clearSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot, boolean isEditMode) {
        int index = getSlotIndex(slots, slot);
        if (index < 0) return;

        // 从draft中移除编辑值
        Map<Integer, IngredientEditValue> draft = drafts.get(recipeId);
        if (draft != null) {
            draft.remove(index);
        }
        
        // 标记槽位为已清除，阻止重新注册轮换
        // markSlotCleared会同时从slotCandidates中移除，不需要再调用unregisterSlot
        IngredientCycleManager.markSlotCleared(slot);
        
        // 清除displayOverrides
        slot.clearDisplayOverrides();
        
        // 设置空的displayOverrides，使JEI渲染为空
        try {
            slot.createDisplayOverrides().addIngredient(
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, 
                net.minecraft.world.item.ItemStack.EMPTY
            );
        } catch (Exception e) {
            // 如果ITEM_STACK不支持，尝试FLUID_STACK
            try {
                slot.createDisplayOverrides().addIngredient(
                    mezz.jei.api.forge.ForgeTypes.FLUID_STACK,
                    net.minecraftforge.fluids.FluidStack.EMPTY
                );
            } catch (Exception ignored) {}
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
        // 先检查是否有编辑值
        Optional<IngredientEditValue> value = getSlotEditValue(recipeId, recipe, slots, slot);
        if (value.isPresent()) {
            return value.get().kind();
        }

        // 如果没有编辑值，从槽位本身获取类型
        try {
            var ingredients = slot.getAllIngredients().toList();
            for (var ingredient : ingredients) {
                if (ingredient != null) {
                    Object inner = ingredient.getIngredient();
                    if (inner instanceof ItemStack) {
                        return IngredientKind.ITEM;
                    } else if (inner instanceof FluidStack) {
                        return IngredientKind.FLUID;
                    } else if (isChemicalStack(inner)) {
                        return getChemicalKind(inner);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 默认返回ITEM
        return IngredientKind.ITEM;
    }

    /**
     * 从槽位本身获取原始ingredientId
     */
    public static String getSlotIngredientId(IRecipeSlotDrawable slot) {
        try {
            var ingredients = slot.getAllIngredients().toList();
            for (var ingredient : ingredients) {
                if (ingredient != null) {
                    Object inner = ingredient.getIngredient();
                    if (inner instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                        return itemStack.getItem().toString();
                    } else if (inner instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                        return fluidStack.getFluid().toString();
                    } else if (isChemicalStack(inner)) {
                        return getChemicalId(inner);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 应用草稿到布局（用于渲染）
     * 使用JEI的displayOverrides机制动态更新槽位显示内容
     */
    public static void applyDraftToLayout(IRecipeLayoutDrawable<?> recipeLayout) {
        applyDraftToLayout(recipeLayout, null);
    }

    public static void applyDraftToLayout(IRecipeLayoutDrawable<?> recipeLayout, String explicitRecipeId) {
        String recipeId = explicitRecipeId;
        if (recipeId == null || recipeId.isEmpty()) {
            recipeId = getRecipeIdFromLayout(recipeLayout);
        }
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
                // 但如果槽位被标记为清除，不重新注册轮换
                if (!IngredientCycleManager.isSlotCleared(slot)) {
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
            // 取消清除标记，允许重新注册轮换
            IngredientCycleManager.unmarkSlotCleared(slot);

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
                        // 单个候选：取消轮换，直接显示
                        IngredientCycleManager.unregisterSlot(slot);
                        // slot.clearDisplayOverrides();
                        // var displayOverrides = slot.createDisplayOverrides();
                        try {
                            displayOverrides.addIngredient((IIngredientType<Object>) types.get(0), values.get(0));
                        } catch (Exception ignored) {}
                    } else {
                        // 多个候选：交给轮换管理器（registerSlotCandidates有相同检查，不会重复注册）
                        IngredientCycleManager.registerSlotCandidates(slot, types, values);
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
                        // 单个候选：取消轮换，直接显示
                        IngredientCycleManager.unregisterSlot(slot);
                        // slot.clearDisplayOverrides();
                        // var displayOverrides = slot.createDisplayOverrides();
                        try {
                            displayOverrides.addIngredient((IIngredientType<Object>) types.get(0), values.get(0));
                        } catch (Exception ignored) {}
                    } else {
                        // 多个候选：交给轮换管理器
                        IngredientCycleManager.registerSlotCandidates(slot, types, values);
                    }
                }
                default -> {
                    // 化学物质
                    if (editValue.kind().isChemical()) {
                        IngredientCycleManager.unregisterSlot(slot);
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
            String registryMethodName = switch (editValue.kind()) {
                case GAS -> "gasRegistry";
                case INFUSION -> "infuseTypeRegistry";
                case PIGMENT -> "pigmentRegistry";
                case SLURRY -> "slurryRegistry";
                default -> null;
            };

            if (registryMethodName == null) return;

            Class<?> mekanismApi = Class.forName("mekanism.api.MekanismAPI");
            Object registry = mekanismApi.getMethod(registryMethodName).invoke(null);
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

            // 获取Mekanism JEI的化学物质类型（根据具体化学品类型选择对应的TYPE字段）
            Class<?> mekanismJei = Class.forName("mekanism.client.jei.MekanismJEI");
            String typeName = switch (editValue.kind()) {
                case GAS -> "TYPE_GAS";
                case INFUSION -> "TYPE_INFUSION";
                case PIGMENT -> "TYPE_PIGMENT";
                case SLURRY -> "TYPE_SLURRY";
                default -> "TYPE_CHEMICAL";
            };
            Object typeChemical = mekanismJei.getField(typeName).get(null);
            InGameRecipeEditor.LOGGER.info("applyChemicalToSlot: chemicalId={}, kind={}, typeName={}, typeChemical={}, chemicalStack class={}",
                chemicalId, editValue.kind(), typeName, typeChemical, chemicalStack.getClass().getName());

            if (typeChemical instanceof mezz.jei.api.ingredients.IIngredientType<?> ingredientType) {
                slot.clearDisplayOverrides();
                // 使用反射调用addIngredient方法，避免泛型类型问题
                var displayOverrides = slot.createDisplayOverrides();
                try {
                    var addIngredientMethod = displayOverrides.getClass().getMethod("addIngredient",
                        mezz.jei.api.ingredients.IIngredientType.class, Object.class);
                    addIngredientMethod.invoke(displayOverrides, ingredientType, chemicalStack);
                    InGameRecipeEditor.LOGGER.info("applyChemicalToSlot: addIngredient成功, ingredientType={}", ingredientType);
                } catch (NoSuchMethodException e) {
                    InGameRecipeEditor.LOGGER.warn("找不到addIngredient方法", e);
                } catch (Exception e) {
                    InGameRecipeEditor.LOGGER.warn("addIngredient调用失败", e);
                }
            } else {
                InGameRecipeEditor.LOGGER.warn("applyChemicalToSlot: typeChemical不是IIngredientType实例: {}", typeChemical);
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