package dev.whisperlyric.ingamerecipeeditor.button;

import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.util.JeiRecipeHelper;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeWorkspaceManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/**
 * 配方编辑按钮 - 点击编辑配方（包含内容物的工作区）
 * 对所有配方显示，但tooltip根据是否为生成配方显示不同提示
 */
public class RecipeEditButton extends AbstractRecipeButton {
    private static final ResourceLocation EDIT_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_edit.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final boolean generatedRecipe;
    private final IRecipeLayoutDrawable<?> recipeLayout;

    public RecipeEditButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 8, 8);
        this.recipeLayout = recipeLayout;

        // 使用JeiRecipeHelper获取正确的配方ID
        String id = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.recipeId = id != null ? id : "";
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;
        this.generatedRecipe = !this.recipeId.isEmpty() && GeneratedRecipesManager.isGeneratedRecipeId(this.recipeId);
    }

    @Override
    protected ResourceLocation getIconTexture() {
        return EDIT_TEXTURE;
    }

    @Override
    protected List<Component> getTooltipLines() {
        if (generatedRecipe) {
            // 生成配方：编辑此配方
            return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe_edit.edit_generated"));
        } else {
            // 普通配方：编辑配方内容
            return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe_edit.edit_recipe"));
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.recipeId != null && !this.recipeId.isEmpty()) {
            // 播放点击声音
            playDownSound(Minecraft.getInstance().getSoundManager());

            // 打开工作区界面，编辑配方内容
            Screen currentScreen = Minecraft.getInstance().screen;
            RecipeWorkspaceManager.getInstance().openWorkspace(currentScreen, recipeLayout);

            InGameRecipeEditor.LOGGER.info("编辑配方: {}", recipeId);
        }
    }

    public String getRecipeId() {
        return recipeId;
    }

    public boolean isGeneratedRecipe() {
        return generatedRecipe;
    }

    public boolean isSupportedRecipe() {
        return supportedRecipe;
    }
}