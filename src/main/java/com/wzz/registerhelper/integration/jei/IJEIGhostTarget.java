package com.wzz.registerhelper.integration.jei;

import java.util.function.Consumer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IJEIGhostTarget {

    @Nullable
    IGhostIngredientConsumer getGhostHandler();

    default int borderSize() {
        return 0;
    }

    interface IGhostIngredientConsumer extends Consumer<Object> {

        boolean supportsIngredient(Object ingredient);
    }

    interface IGhostItemConsumer extends IGhostIngredientConsumer {

        @Override
        default boolean supportsIngredient(Object ingredient) {
            return ingredient instanceof ItemStack stack && !stack.isEmpty();
        }
    }

    interface IGhostBlockItemConsumer extends IGhostItemConsumer {

        @Override
        default boolean supportsIngredient(Object ingredient) {
            return IGhostItemConsumer.super.supportsIngredient(ingredient) && ((ItemStack) ingredient).getItem() instanceof BlockItem;
        }
    }
}