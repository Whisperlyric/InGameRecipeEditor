package dev.whisperlyric.ingamerecipeeditor.workspace;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 工作区 Ghost Ingredient Handler - 处理拖拽物品到工作区槽位
 */
public class RecipeWorkspaceGhostIngredientHandler implements IGhostIngredientHandler<RecipeWorkspaceScreen> {
    
    @Override
    public <I> @NotNull List<Target<I>> getTargetsTyped(
            @NotNull RecipeWorkspaceScreen screen,
            mezz.jei.api.ingredients.ITypedIngredient<I> ingredient,
            boolean doStart
    ) {
        Optional<ItemStack> itemStack = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
        Optional<FluidStack> fluidStack = ingredient.getIngredient(ForgeTypes.FLUID_STACK);
        Object rawIngredient = ingredient.getIngredient();
        RecipeEditManager.IngredientKind draggedKind;

        if (itemStack.isPresent() && !itemStack.get().isEmpty()) {
            draggedKind = RecipeEditManager.IngredientKind.ITEM;
        } else if (fluidStack.isPresent() && !fluidStack.get().isEmpty()) {
            draggedKind = RecipeEditManager.IngredientKind.FLUID;
        } else if (RecipeEditManager.isChemicalStack(rawIngredient)) {
            draggedKind = RecipeEditManager.getChemicalKind(rawIngredient);
        } else {
            // 尝试通过 Mekanism JEI 的特定原料类型检测
            Optional<RecipeEditManager.IngredientKind> mekanismKind = getMekanismDraggedKind(ingredient);
            if (mekanismKind.isPresent()) {
                draggedKind = mekanismKind.get();
            } else {
                return List.of();
            }
        }

        String recipeId = screen.getRecipeId();
        List<IRecipeSlotView> slots = screen.getSlots();

        List<Target<I>> targets = new ArrayList<>();

        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            if (slot.getRole() != RecipeIngredientRole.INPUT &&
                slot.getRole() != RecipeIngredientRole.OUTPUT) {
                continue;
            }

            RecipeEditManager.IngredientKind slotKind = RecipeEditManager.getSlotIngredientKind(
                recipeId, screen.getRecipeLayout().getRecipe(), slots, slot
            );
            
            // 类型匹配逻辑
            if (!isKindCompatible(slotKind, draggedKind)) {
                continue;
            }

            Rect2i area = screen.getSlotAbsoluteRect(slot);
            targets.add(new WorkspaceTarget<>(screen, recipeId, slots, slot, area));
        }

        return targets;
    }

    /**
     * 检查槽位类型和拖拽类型是否兼容
     */
    private static boolean isKindCompatible(RecipeEditManager.IngredientKind slotKind, RecipeEditManager.IngredientKind draggedKind) {
        if (slotKind == draggedKind) {
            return true;
        }
        // RESOURCE 作为通用化学物质类型，可以匹配任何具体化学物质类型
        if (slotKind == RecipeEditManager.IngredientKind.RESOURCE && draggedKind.isChemical()) {
            return true;
        }
        // 如果拖拽的是 RESOURCE（未知化学物质），可以放入任何化学物质槽位
        return draggedKind == RecipeEditManager.IngredientKind.RESOURCE && slotKind.isChemical();
    }

    /**
     * 通过 Mekanism JEI 的特定原料类型检测拖拽原料的类型
     */
    private static <I> Optional<RecipeEditManager.IngredientKind> getMekanismDraggedKind(mezz.jei.api.ingredients.ITypedIngredient<I> ingredient) {
        try {
            Class<?> mekanismJeiClass = Class.forName("mekanism.client.jei.MekanismJEI");
            
            // 检查 Gas
            Object gasType = mekanismJeiClass.getField("TYPE_GAS").get(null);
            if (gasType instanceof mezz.jei.api.ingredients.IIngredientType<?> type && ingredient.getIngredient(type).isPresent()) {
                return Optional.of(RecipeEditManager.IngredientKind.GAS);
            }
            
            // 检查 Infusion
            Object infusionType = mekanismJeiClass.getField("TYPE_INFUSION").get(null);
            if (infusionType instanceof mezz.jei.api.ingredients.IIngredientType<?> type && ingredient.getIngredient(type).isPresent()) {
                return Optional.of(RecipeEditManager.IngredientKind.INFUSION);
            }
            
            // 检查 Pigment
            Object pigmentType = mekanismJeiClass.getField("TYPE_PIGMENT").get(null);
            if (pigmentType instanceof mezz.jei.api.ingredients.IIngredientType<?> type && ingredient.getIngredient(type).isPresent()) {
                return Optional.of(RecipeEditManager.IngredientKind.PIGMENT);
            }
            
            // 检查 Slurry
            Object slurryType = mekanismJeiClass.getField("TYPE_SLURRY").get(null);
            if (slurryType instanceof mezz.jei.api.ingredients.IIngredientType<?> type && ingredient.getIngredient(type).isPresent()) {
                return Optional.of(RecipeEditManager.IngredientKind.SLURRY);
            }
        } catch (ReflectiveOperationException e) {
            // Mekanism JEI 未加载，静默处理
        }
        return Optional.empty();
    }

    @Override
    public void onComplete() {
        // 完成拖拽后的处理
    }

    private static class WorkspaceTarget<I> implements Target<I> {
        private final RecipeWorkspaceScreen screen;
        private final String recipeId;
        private final List<IRecipeSlotView> slots;
        private final IRecipeSlotDrawable slot;
        private final Rect2i area;

        private WorkspaceTarget(RecipeWorkspaceScreen screen, String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot, Rect2i area) {
            this.screen = screen;
            this.recipeId = recipeId;
            this.slots = slots;
            this.slot = slot;
            this.area = area;
        }

        @Override
        public @NotNull Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(@NotNull I ingredient) {
            if (ingredient instanceof ItemStack stack) {
                RecipeEditManager.replaceSlot(recipeId, slots, slot, stack);
            } else if (ingredient instanceof FluidStack stack) {
                RecipeEditManager.replaceSlot(recipeId, slots, slot, stack);
            } else if (RecipeEditManager.isChemicalStack(ingredient)) {
                RecipeEditManager.replaceResourceSlot(recipeId, slots, slot, ingredient);
            }
        }

        public RecipeWorkspaceScreen getScreen() {
            return screen;
        }
    }
}