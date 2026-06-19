package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.gui.NumberAdjustmentScreen;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 配方槽位数量编辑工具类 - 直接打开NumberAdjustmentScreen
 */
public class RecipeIngredientTextEditScreen {

    public static void open(Screen parent, String recipeId, List<IRecipeSlotView> slots,
                           IRecipeSlotDrawable slot, RecipeEditManager.IngredientEditValue initialValue) {
        if (Minecraft.getInstance() != null) {
            long currentAmount = Math.max(1, initialValue.amount());
            long maxAmount = 1000;

            // 如果ingredientId为空，从槽位本身获取原始ingredientId
            String ingredientId = initialValue.ingredientId();
            if (ingredientId == null || ingredientId.isEmpty()) {
                ingredientId = RecipeEditManager.getSlotIngredientId(slot);
            }

            // 如果仍然为空，则不允许编辑
            if (ingredientId == null || ingredientId.isEmpty()) {
                return;
            }

            // 根据槽位类型设置上限
            RecipeEditManager.IngredientKind kind = initialValue.kind();
            if (kind == RecipeEditManager.IngredientKind.ITEM) {
                // 物品槽：根据物品堆叠上限设置
                try {
                    var ingredients = slot.getAllIngredients().toList();
                    for (var ingredient : ingredients) {
                        if (ingredient != null) {
                            Object inner = ingredient.getIngredient();
                            if (inner instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                                maxAmount = itemStack.getMaxStackSize();
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            String finalIngredientId = ingredientId;
            Minecraft.getInstance().setScreen(new NumberAdjustmentScreen(
                    parent,
                    1,
                    (int) maxAmount,
                    (int) currentAmount,
                    newAmount -> RecipeEditManager.setSlotEditValue(
                            recipeId,
                            slots,
                            slot,
                            new RecipeEditManager.IngredientEditValue(kind, finalIngredientId, newAmount)
                    ),
                    false,
                    0
            ));
        }
    }
}