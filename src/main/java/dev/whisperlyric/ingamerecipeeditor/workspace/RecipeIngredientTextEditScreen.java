package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.gui.NumberAdjustmentScreen;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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

            Minecraft.getInstance().setScreen(new NumberAdjustmentScreen(
                    parent,
                    1,
                    (int) Math.min(maxAmount, Integer.MAX_VALUE),
                    (int) currentAmount,
                    newAmount -> {
                        RecipeEditManager.setSlotEditValue(
                                recipeId,
                                slots,
                                slot,
                                new RecipeEditManager.IngredientEditValue(initialValue.kind(), initialValue.ingredientId(), newAmount)
                        );
                    },
                    false,
                    0
            ));
        }
    }
}