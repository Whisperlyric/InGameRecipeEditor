package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

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

            IngredientEditValue originalValue = getSlotOriginalValue(recipe, slot);
            if (originalValue != null) {
                originalValues.put(index, originalValue);
            }
            index++;
        }

        originals.put(recipeId, originalValues);
        InGameRecipeEditor.LOGGER.debug("开始编辑配方: {}", recipeId);
    }

    /**
     * 获取槽位原始值
     */
    private static IngredientEditValue getSlotOriginalValue(Object recipe, IRecipeSlotDrawable slot) {
        try {
            var ingredients = slot.getAllIngredients().toList();
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
        InGameRecipeEditor.LOGGER.debug("清除配方草稿: {}", recipeId);
    }

    /**
     * 提交草稿（生成配方文件）
     */
    public static void submit(String recipeId) {
        Map<Integer, IngredientEditValue> draft = drafts.get(recipeId);
        if (draft == null || draft.isEmpty()) {
            return;
        }

        // TODO: 生成配方JSON文件
        InGameRecipeEditor.LOGGER.info("提交配方草稿: {}", recipeId);
        drafts.remove(recipeId);
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
        InGameRecipeEditor.LOGGER.debug("替换槽位 {} 为物品: {}", index, stack.getItem());
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
        InGameRecipeEditor.LOGGER.debug("替换槽位 {} 为流体: {}", index, stack.getFluid());
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
        InGameRecipeEditor.LOGGER.debug("替换槽位 {} 为化学物质: {} ({})", index, chemicalId, kind);
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
            InGameRecipeEditor.LOGGER.debug("清除槽位 {}", index);
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
        InGameRecipeEditor.LOGGER.debug("设置槽位 {} 编辑值: {} ({})", index, value.ingredientId(), value.kind());
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
     */
    public static void applyDraftToLayout(IRecipeLayoutDrawable<?> recipeLayout) {
        // JEI的布局是只读的，这里只是用于渲染叠加层
        // 实际的渲染在RecipeWorkspaceScreen.renderEditedSlots中处理
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