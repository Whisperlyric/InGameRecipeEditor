package com.wzz.registerhelper.integration.jei;

import com.wzz.registerhelper.gui.RecipeCreatorScreen;
import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import com.wzz.registerhelper.util.ResourceUtil;
import dev.whisperlyric_fork.gui.JEISelectionScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class RegisterHelperJEIPlugin implements IModPlugin {
    
    private static final ResourceLocation PLUGIN_ID = ResourceUtil.createInstance("jei_plugin");
    
    @Override
    @NotNull
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }
    
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
            new CustomBrewingRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
        
        registration.addRecipeCategories(
            new CustomAnvilRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }
    
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var brewingRecipes = CustomRecipeLoader.getBrewingRecipes().stream()
            .map(JEIBrewingRecipe::new)
            .toList();
        
        if (!brewingRecipes.isEmpty()) {
            registration.addRecipes(
                CustomBrewingRecipeCategory.RECIPE_TYPE,
                brewingRecipes
            );
        }
        
        var anvilRecipes = CustomRecipeLoader.getAnvilRecipes().stream()
            .map(JEIAnvilRecipe::new)
            .toList();
        
        if (!anvilRecipes.isEmpty()) {
            registration.addRecipes(
                CustomAnvilRecipeCategory.RECIPE_TYPE,
                anvilRecipes
            );
        }
    }
    
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registry) {
        registry.addGhostIngredientHandler(JEISelectionScreen.class, new JEIGhostIngredientHandler<>());
    }
}